/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import net.minecraftforge.gradleutils.shared.ToolExecBase;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.process.ExecResult;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Set;

@CacheableTask
public abstract class RenameJar extends ToolExecBase<RenamerProblems> implements RenamerTask, PublishArtifact {
    public abstract @InputFile @PathSensitive(PathSensitivity.RELATIVE) RegularFileProperty getInput();
    public abstract @InputFiles @Classpath ConfigurableFileCollection getMap();
    public abstract @InputFiles @Optional @CompileClasspath ConfigurableFileCollection getLibraries();
    public abstract @Input @Optional Property<String> getArchiveClassifier();
    public abstract @Input Property<String> getArchiveExtension();
    public abstract @Input Property<Boolean> getReverse();
    public abstract @Input Property<Boolean> getNaiveSrg();
    public abstract @Input Property<Boolean> getAccessTransformers();
    public abstract @Input Property<Boolean> getLegacyAccessTransformers();

    /// Tells Renamer to not compress files when building the output jar
    /// This is done because different compression libraries may produce different compressed bytes.
    /// Using Store takes more space, but helps make reproduceable jars
    public abstract @Input Property<Boolean> getStore();

    /// Adds the --mixins flag when invoking renamer, this will attempt to remap Mixin related things.
    /// Currently, this only support refmaps, typically used when remapping dependencies without enabling mixins in your project itself.
    public abstract @Input Property<Boolean> getMixins();

    public abstract @OutputFile RegularFileProperty getOutput();

    protected abstract @Internal Property<Date> getArchiveDate();
    protected abstract @Inject DependencyFactory getDependencyFactory();

    @Inject
    public RenameJar(RenamerExtensionImpl renamer) {
        super(Tools.RENAMER);
        // We don't want the default runs to log to the main task output
        this.getStandardOutputLogLevel().convention(LogLevel.INFO);

        // As a reminder, this is overridden by manually calling one of the #mappings methods
        this.getMap().convention(getProviders().provider(() -> renamer.mappings));

        // This is the path used for arbitrary file input
        // Not when used on AbstractArchiveTasks
        // Basically what we do is just take the input file name, and unless told otherwise append a '-renamed' suffix to it
        // We also make sure the output file ends up in our project directory, as renaming dependencies can cause conflicts if we are in a multi-project build
        this.getOutput().convention(
            getObjects().fileProperty().fileProvider(getProviders().zip(this.getInput().getLocationOnly(), this.getArchiveClassifier().orElse("renamed"), (input, classifier) -> {
                var file = input.getAsFile();
                var outputDir = file.getParentFile();

                var projectDir = this.getProject().getLayout().getProjectDirectory().getAsFile().getAbsolutePath();
                // the file isn't in our project, which could cause inter-project issues. So pick a spot in our project
                if (outputDir == null || !outputDir.getAbsolutePath().startsWith(projectDir))
                    outputDir = this.localCaches().dir("renamed").get().getAsFile();

                String name;
                int idx = file.getName().lastIndexOf('.');
                var inputName = file.getName().substring(0, idx);

                var ext = file.getName().substring(idx + 1);
                if (this.getArchiveExtension().isPresent())
                    ext = this.getArchiveExtension().get();

                if (classifier.isEmpty())
                    name = inputName + '.' + ext;
                else
                    name = inputName + '-' + classifier + '.' + ext;

                var ret = new File(outputDir, name);
                // If all else fails, just append -renamed
                if (ret.getAbsolutePath().equals(file.getAbsolutePath()))
                    ret = new File(outputDir, inputName + "-renamed." + ext);
                return ret;
            })).orElse(this.getDefaultOutputFile())
        );
        this.getLibraries().convention(getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getCompileClasspath));

        this.getArchiveExtension().convention("jar");
        this.getArchiveDate().convention(this.getInput().map(input -> new Date(input.getAsFile().lastModified())));
        this.getReverse().convention(false);
        this.getNaiveSrg().convention(false);
        this.getAccessTransformers().convention(false);
        this.getLegacyAccessTransformers().convention(false);
        this.getStore().convention(false);
        this.getMixins().convention(false);
    }

    @Override
    @TaskAction
    protected ExecResult exec() throws IOException {
        return super.exec().assertNormalExitValue().rethrowFailure();
    }

    public void from(AbstractArchiveTask task) {
        this.from(getProject().getTasks().named(task.getName(), AbstractArchiveTask.class));
    }

    public void from(TaskProvider<? extends AbstractArchiveTask> task) {
        var libs = this.getProject().getLayout().getBuildDirectory().dir("libs");
        this.getInput().set(task.flatMap(AbstractArchiveTask::getArchiveFile));
        this.getArchiveClassifier().set(task.flatMap(AbstractArchiveTask::getArchiveClassifier).filter(Util.STRING_IS_PRESENT).map(s -> s + "-renamed").orElse("renamed"));
        this.getArchiveExtension().set(task.flatMap(AbstractArchiveTask::getArchiveExtension).orElse("jar"));
        this.getOutput().set(getObjects().fileProperty().fileProvider(
            task.flatMap(AbstractArchiveTask::getArchiveBaseName)
            .zip(task.flatMap(AbstractArchiveTask::getArchiveAppendix).orElse(""), RenameJar::append)
            .zip(task.flatMap(AbstractArchiveTask::getArchiveVersion).orElse(""), RenameJar::append)
            .zip(this.getArchiveClassifier().orElse(""), RenameJar::append)
            .zip(this.getArchiveExtension().orElse(""), (name, ext) -> ext.isEmpty() ? name : name + '.' + ext)
            .zip(libs, (name, dir) -> dir.file(name).getAsFile())
        ));
    }

    private static String append(String prefix, @Nullable String value) {
        if (value != null && !value.isEmpty())
            return prefix + '-' + value;
        return prefix;
    }

    public void mappings(String artifact) {
        this.mappings(getDependencyFactory().create(artifact));
    }

    public void mappings(Dependency dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
    }

    public void mappings(Provider<?> provider) {
        this.getMap().setFrom(Util.toConfiguration(getProject(), provider));
    }

    public void mappings(TaskProvider<?> task) {
        this.getMap().setFrom(Util.toFile(task));
    }

    public void setMappings(FileCollection files) {
        this.getMap().setFrom(files);
    }

    @Override
    protected void addArguments() {
        this.args("--input", this.getInput());
        this.args("--map", this.getMapFile());
        this.args("--output", this.getOutput());
        this.args("--lib", this.getLibraries());

        if (this.getReverse().getOrElse(false))
            this.args("--reverse");

        if (this.getNaiveSrg().getOrElse(false))
            this.args("--naive-srg");

        if (this.getLegacyAccessTransformers().getOrElse(false))
            this.args("--legacy-access-transformers");
        else if (this.getAccessTransformers().getOrElse(false))
            this.args("--access-transformers");

        if (this.getStore().getOrElse(false))
            this.args("--store");

        if (this.getMixins().getOrElse(false))
            this.args("--mixins");

        super.addArguments();
    }

    private File getMapFile() {
        try {
            return this.getMap().getSingleFile();
        } catch (IllegalStateException exception) {
            throw getProblems().reportMultipleMapFiles(exception, this);
        }
    }

    @Override
    @Deprecated
    public @Internal @Nullable String getClassifier() {
        return this.getArchiveClassifier().getOrNull();
    }

    @Override
    @Deprecated
    public @Internal String getExtension() {
        return this.getArchiveExtension().get();
    }

    @Override
    @Deprecated
    public @Internal String getType() {
        return this.getExtension();
    }

    @Override
    @Deprecated
    public @Internal File getFile() {
        return this.getOutput().getAsFile().get();
    }

    @Override
    @Deprecated
    public @Internal Date getDate() {
        return this.getArchiveDate().get();
    }

    @Override
    public @Internal TaskDependency getBuildDependencies() {
        return task -> Set.of(this);
    }
}

