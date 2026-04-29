/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.internal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import com.github.jezza.Toml;
import com.github.jezza.TomlArray;
import com.github.jezza.TomlTable;

import net.minecraftforge.renamer.api.ClassProvider;
import net.minecraftforge.renamer.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;

public class RenamingTransformer implements Transformer {
    private static final String ABSTRACT_FILE = "fernflower_abstract_parameter_names.txt";
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    private static final String MODS_TOML = "META-INF/mods.toml";
    private static final String ACCESS_TRANSFORMER = "META-INF/accesstransformer.cfg";
    private static final Attributes.Name FMLAT = new Attributes.Name("FMLAT");

    private final EnhancedRemapper remapper;
    private final Set<String> abstractParams = ConcurrentHashMap.newKeySet();
    private final boolean collectAbstractParams;
    private final boolean renameAts;
    private final Consumer<String> logger;
    private final Set<String> atPaths = new HashSet<>();

    public RenamingTransformer(ClassProvider classProvider, IMappingFile map, Consumer<String> log) {
        this(classProvider, map, log, true);
    }

    public RenamingTransformer(ClassProvider classProvider, IMappingFile map, Consumer<String> log, boolean collectAbstractParams) {
        this(classProvider, map, log, collectAbstractParams, false);
    }

    public RenamingTransformer(ClassProvider classProvider, IMappingFile map, Consumer<String> log, boolean collectAbstractParams, boolean naiveSrg) {
        this(classProvider, map, log, collectAbstractParams, naiveSrg, false);
    }

    public RenamingTransformer(ClassProvider classProvider, IMappingFile map, Consumer<String> log, boolean collectAbstractParams, boolean naiveSrg, boolean renameAts) {
        this.collectAbstractParams = collectAbstractParams;
        this.renameAts = renameAts;
        this.logger = log;
        this.remapper = new EnhancedRemapper(classProvider, map, log, naiveSrg);
    }

    @Override
    public void preprocess(Map<String, Entry> entries) {
        if (renameAts) {
            if (entries.containsKey(ACCESS_TRANSFORMER))
                atPaths.add(ACCESS_TRANSFORMER);
            if (entries.containsKey(MANIFEST_NAME)) {
                Entry entry = entries.get(MANIFEST_NAME);
                try {
                    Manifest mf = new Manifest(new ByteArrayInputStream(entry.getData()));
                    String cfgs = (String)mf.getMainAttributes().get(FMLAT);
                    if (cfgs != null) {
                        for (String pt : cfgs.split(" "))
                            atPaths.add("META-INF/" + pt);
                    }
                } catch (IOException e) {
                    logger.accept("Failed to parse manifest: " + e);
                }
            }
            if (entries.containsKey(MODS_TOML)) {
                Entry entry = entries.get(MODS_TOML);
                try {
                    TomlTable toml = Toml.from(new ByteArrayInputStream(entry.getData()));
                    Object mods = toml.get("mods");
                    if (mods instanceof TomlArray) {
                        TomlArray arr = (TomlArray)mods;
                        for (Object mod : arr) {
                            if (mod instanceof TomlTable) {
                                TomlTable table = (TomlTable)mod;
                                Object ats = table.get("accessTransformers");
                                if (ats instanceof String) {
                                    for (String pt : ((String)ats).split(","))
                                        atPaths.add(pt);
                                } else if (ats instanceof TomlArray) {
                                    for (Object pt : ((TomlArray)ats))
                                        atPaths.add((String)pt);
                                }
                            }
                        }
                    } else if (mods != null) {
                        logger.accept("Failed to parse mods.toml, Unknown mods entry: " + mods.getClass());
                    }
                } catch (IOException | ClassCastException e) {
                    logger.accept("Failed to parse mods.toml: " + e);
                }
            }
        }
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(0);
        ClassRemapper remapper = new EnhancedClassRemapper(writer, this.remapper, this);

        reader.accept(remapper, 0);

        byte[] data = writer.toByteArray();
        String newName = this.remapper.map(entry.getClassName());

        if (entry.isMultiRelease())
            return ClassEntry.create(newName, entry.getTime(), data, entry.getVersion());
        return ClassEntry.create(newName + ".class", entry.getTime(), data);
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (ABSTRACT_FILE.equals(entry.getName()))
            return null;

        if (this.atPaths.contains(entry.getName()))
            return renameAccessTransformer(entry);

        return entry;
    }

    @Override
    public Collection<? extends Entry> getExtras() {
        if (abstractParams.isEmpty() || !collectAbstractParams)
            return Collections.emptyList();
        byte[] data = abstractParams.stream().sorted().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
        return Collections.singletonList(ResourceEntry.create(ABSTRACT_FILE, Entry.STABLE_TIMESTAMP, data));
    }

    void storeNames(String className, String methodName, String methodDescriptor, Collection<String> paramNames) {
        abstractParams.add(className + ' ' + methodName + ' ' + methodDescriptor + ' ' + String.join(" ", paramNames));
    }

    private ResourceEntry renameAccessTransformer(ResourceEntry entry) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (OutputStreamWriter out = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(entry.getData()), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                int comment = line.indexOf('#');
                List<String> pts = Util.tokenize(comment == -1 ? line : line.substring(0, comment));
                if (pts.size() == 2) { // Classes
                    String cls = pts.get(1).replace('.', '/');
                    out.write(pts.get(0));
                    out.write(' ');
                    out.write(this.remapper.map(cls).replace('/', '.'));
                    if (comment != -1) {
                        out.write(' ');
                        out.write(line.substring(comment));
                    }
                } else if (pts.size() == 3) {
                    String cls = pts.get(1).replace('.', '/');
                    String name = pts.get(2);
                    out.write(pts.get(0));
                    out.write(' ');
                    out.write(this.remapper.map(cls).replace('/', '.'));
                    out.write(' ');
                    int paren = name.indexOf('(');
                    if (paren == -1) { // Field
                        out.write(this.remapper.mapFieldName(cls, name, null));
                    } else {
                        String desc = name.substring(paren);
                        name = name.substring(0, paren);
                        out.write(this.remapper.mapMethodName(cls, name, desc));
                        out.write(this.remapper.mapMethodDesc(desc));
                    }
                    if (comment != -1) {
                        out.write(' ');
                        out.write(line.substring(comment));
                    }
                } else { // Unknown format, write the line as brought in
                    out.write(line);
                }
                out.write('\n');
            }
            out.flush();
            return ResourceEntry.create(entry.getName(), entry.getTime(), bos.toByteArray());
        } catch (IOException e) {
            // In theory this should never be possible. As we're just working in memory
            logger.accept("Error renaming access transformer " + entry.getName() + ": " + e);
            throw new RuntimeException(e);
        }
    }
}
