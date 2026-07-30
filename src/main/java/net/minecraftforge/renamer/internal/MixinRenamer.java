/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.renamer.api.Transformer;

final class MixinRenamer implements Transformer {
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    private static final Attributes.Name MIXIN_CONFIGS = new Attributes.Name("MixinConfigs");
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private final Set<String> refmaps = new HashSet<>();
    private final Consumer<String> logger;
    private final EnhancedRemapper remapper;

    MixinRenamer(Consumer<String> logger, EnhancedRemapper remapper) {
        this.logger = logger;
        this.remapper = remapper;
    }

    boolean isTarget(String entry) {
        return this.refmaps.contains(entry);
    }

    @Override
    public void preprocess(Map<String, Entry> entries) {
        Entry entry = entries.get(MANIFEST_NAME);
        if (entry == null) // No Manifest, so no configs.
            return;

        String[] cfgs = findConfigs(entries.get(MANIFEST_NAME));
        if (cfgs == null)
            return;

        for (String cfg : cfgs) {
            entry = entries.get(cfg);
            if (entry == null) {
                logger.accept("Missing mixin config: " + cfg);
                continue;
            }

            String refmap = findRefMap(entry);
            if (refmap != null)
                refmaps.add(refmap);
        }
    }

    @Override
    public ResourceEntry process(ResourceEntry resource) {
        if (!refmaps.contains(resource.getName()))
            return resource;

        Refmap refmap = null;
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(resource.getData()))) {
            refmap = GSON.fromJson(reader, Refmap.class);
        } catch (IOException e) {
            logger.accept("Failed to parse Mixin Refmap " + resource.getName() + ": " + e);
            return resource;
        }

        if (refmap.mappings == null || refmap.mappings.isEmpty())
            return resource;

        Map<String, Map<String, String>> output = new LinkedHashMap<>(refmap.mappings.size());
        for (Map.Entry<String, Map<String, String>> mixin : refmap.mappings.entrySet()) {
            Map<String, String> _new = new LinkedHashMap<>(mixin.getValue().size());
            output.put(mixin.getKey(), _new);

            for (Map.Entry<String, String> entry : mixin.getValue().entrySet()) {
                MemberInfo old = MemberInfo.parse(entry.getValue());
                MemberInfo mapped = old.map(this.remapper);
                _new.put(entry.getKey(), mapped.toString());
            }
        }

        refmap.mappings = output;
        String json = GSON.toJson(refmap);
        return ResourceEntry.create(resource.getName(), resource.getTime(), json.getBytes(StandardCharsets.UTF_8));
    }

    private String @Nullable [] findConfigs(Entry entry) {
        try {
            Manifest mf = new Manifest(new ByteArrayInputStream(entry.getData()));
            String value = (String)mf.getMainAttributes().get(MIXIN_CONFIGS);
            return value == null ? null : value.split(",");
        } catch (IOException e) {
            logger.accept("Failed to parse manifest: " + e);
            return null;
        }
    }

    private @Nullable String findRefMap(Entry entry) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(entry.getData()))) {
            MixinConfig cfg = GSON.fromJson(reader, MixinConfig.class);
            return cfg.refmap;
        } catch (IOException e) {
            logger.accept("Failed to parse Mixin Config " + entry.getName() + ": " + e);
            return null;
        }
    }

    private static class MixinConfig {
        /*
         * I could read these values, and then parse the mixins themselves to fully remap things.
         * But that would require special casing all mixin annotations and keeping up to date on them
         * Which is a much bigger task then I want to do right now.
         * If someone DID want to tackle that, I have most of the logic done in Srg2Source
         * https://github.com/MinecraftForge/Srg2Source/tree/master/src/main/java/net/minecraftforge/srg2source/mixin
        @SerializedName("package")
        public String mixinPackage;
        public List<String> mixins;
        public List<String> client;
        public List<String> server;
        */
        public String refmap;
    }

    private static class Refmap {
       public Map<String, Map<String, String>> mappings;
       // This might be useful information, but for the current use case, we just nuke this info
       //public @Nullable Map<String, Map<String, Map<String, String>>> data;
    }

    private static final class MemberInfo {
        private final String owner;
        private final String name;
        private final String quantifier;
        private final String desc;
        private final String tail;

        private final String asString;

        private MemberInfo(String owner, String name, String quantifier, String desc, String tail) {
            this.owner = owner;
            this.name = name;
            this.quantifier = quantifier;
            this.desc = desc;
            this.tail = tail;

            StringBuilder buf = new StringBuilder();
            if (this.owner != null)
                buf.append('L').append(this.owner).append(';');
            if (this.name != null)
                buf.append(this.name);
            if (this.quantifier != null)
                buf.append(this.quantifier);
            if (this.desc != null) {
                if (this.desc.charAt(0) != '(')
                    buf.append(':');
                buf.append(this.desc);
            }
            if (this.tail != null)
                buf.append(this.tail);
            this.asString = buf.toString();
        }

        @Override
        public String toString() {
            return this.asString;
        }

        private MemberInfo map(EnhancedRemapper remapper) {
            String owner = this.owner == null ? null : remapper.map(this.owner);

            String name = null;
            if (this.name != null) {
                // No owner, means we need to do the 'naive' srg lookup
                if (this.owner == null) {
                    name = remapper.naive(this.name);
                } else {
                    if (this.desc != null && this.desc.charAt(0) == '(')
                        name = remapper.mapMethodName(this.owner, this.name, this.desc);
                    else
                        name = remapper.mapFieldName(this.owner, this.name, this.desc);
                }
            }

            String desc = null;
            if (this.desc != null)
                desc = this.desc.charAt(0) == '(' ? remapper.mapMethodDesc(this.desc) : remapper.mapDesc(this.desc);

            return new MemberInfo(owner, name, this.quantifier, desc, this.tail);
        }

        private static MemberInfo parse(final String input) {
            String owner = null;
            String name = input.replaceAll("\\s", "");
            String quantifier = null;
            String desc = null;
            String tail = null;

            // Find tail, I think this is just legacy, but support it anyways
            int pos = name.indexOf("->");
            if (pos > -1) {
                tail = name.substring(pos);
                name = name.substring(0, pos);
            }

            // Find the desc, can be either a field with : or a normal method desc
            pos = name.lastIndexOf(':');
            if (pos != -1) { // Field name:desc
                desc = name.substring(pos + 1);
                name = name.substring(0, pos);
            } else {
                pos = name.lastIndexOf('(');
                if (pos != -1) {
                    desc = name.substring(pos);
                    name = name.substring(0, pos);
                }
            }

            pos = name.lastIndexOf('.');
            if (pos != -1) { // Legacy format: owner.name
                owner = name.substring(0, pos).replace('.', '/');
                name = name.substring(pos + 1);
            } else if (name.charAt(0) == 'L') { // Modern format: Lowner;name
                pos = name.indexOf(';');
                if (pos != -1) {
                    owner = name.substring(1, pos).replace('.', '/');
                    name = name.substring(pos + 1);
                }
            }

            if (owner == null) { // Possibly a full class name
                name = name.replace('.', '/');
                if (name.indexOf('/') != -1) {
                    owner = name;
                    name = "";
                }
            }

            // Pull out the qualifier if there is one
            if (!name.isEmpty()) {
                char last = name.charAt(name.length() - 1);
                if (last == '*' || last == '+') {
                    quantifier = name.substring(name.length() - 1);
                    name = name.substring(0, name.length() - 1);
                } else {
                    pos = name.indexOf('{');
                    if (pos != -1) {
                        quantifier = name.substring(pos);
                        name = name.substring(0, pos);
                    }
                }
            }

            if (name.isEmpty())
                name = null;

            return new MemberInfo(owner, name, quantifier, desc, tail);
        }
    }
}

