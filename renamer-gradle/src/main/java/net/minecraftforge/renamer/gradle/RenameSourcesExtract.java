/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.inject.Inject;
import java.util.Objects;

public abstract class RenameSourcesExtract extends RenameSourceBase {
    public abstract @InputFiles @Optional ConfigurableFileCollection getDependencies();
    public abstract @InputFiles ConfigurableFileCollection getSources();
    public abstract @OutputFile RegularFileProperty getOutput();

    public abstract @Input @Optional Property<Boolean> getBatch();
    public abstract @Input @Optional Property<Boolean> getMixins();
    public abstract @Input @Optional Property<Boolean> getMixinsFatal();

    private final Property<JavaVersion> sourceCompatiblityProp = this.getObjects().property(JavaVersion.class);

    public @Input @Optional Property<JavaVersion> getSourceCompatibility() {
        return this.sourceCompatiblityProp;
    }

    public void setSourceCompatibility(JavaLanguageVersion javaVersion) {
        this.getSourceCompatibility().set(JavaVersion.toVersion(javaVersion));
    }

    public void setSourceCompatibility(Provider<? extends JavaLanguageVersion> javaVersion) {
        this.getSourceCompatibility().set(javaVersion.map(JavaVersion::toVersion));
    }

    @Inject
    public RenameSourcesExtract() {
        this.getOutput().convention(this.getDefaultOutputFile("txt"));
        this.getBatch().convention(true);
        this.getMixins().convention(false);
        this.getMixinsFatal().convention(false);

        var java = Objects.requireNonNull(this.getProject().getExtensions().findByType(JavaPluginExtension.class));
        this.getSourceCompatibility().convention(java.getToolchain().getLanguageVersion().map(JavaVersion::toVersion));
    }

    @Override
    protected void addArguments() {
        this.args("--extract");

        this.args("--lib", this.getDependencies());
        this.args("--in", this.getSources());
        this.args("--out", this.getOutput());

        this.args("--batch", this.getBatch());
        this.args("--mixins", this.getMixins());
        this.args("--fatalmixins", this.getMixinsFatal());

        this.args("--source-compatibility", this.getSourceCompatibility().map(this::parseSourceCompatibility));

        super.addArguments();
    }
}
