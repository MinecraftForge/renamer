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
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Date;
import java.util.Set;

@CacheableTask
public abstract class RenameJar extends ToolExecBase<RenamerProblems> implements RenamerTask, PublishArtifact {
    public abstract @InputFile @PathSensitive(PathSensitivity.RELATIVE) RegularFileProperty getInput();

    public abstract @InputFiles @Classpath ConfigurableFileCollection getMap();

    public abstract @OutputFile RegularFileProperty getOutput();

    public abstract @InputFiles @Optional @CompileClasspath ConfigurableFileCollection getLibraries();

    public abstract @Input @Optional Property<String> getArchiveClassifier();

    public abstract @Input Property<String> getArchiveExtension();

    protected abstract @Internal Property<Date> getArchiveDate();

    protected abstract @Inject DependencyFactory getDependencyFactory();

    @Inject
    public RenameJar() {
        super(Tools.RENAMER);
        // We don't want the default runs to log to the main task output
        this.getStandardOutputLogLevel().convention(LogLevel.INFO);

        // As a reminder, this is overridden by manually calling one of the #mappings methods
        this.getMap().convention(getProviders().provider(() -> {
            // TODO [Renamer] This assumes there's only ever one registered renamer, but i don't care for now
            var renamer = (RenamerExtensionImpl) getProject().getExtensions().findByType(RenamerExtension.class);
            if (renamer == null) return null;

            return renamer.mappings;
        }));

        this.getOutput().convention(
            getObjects().fileProperty().fileProvider(getProviders().zip(this.getInput().getLocationOnly(), this.getArchiveClassifier().orElse(""), (input, classifier) -> {
                var file = input.getAsFile();
                String name;
                {
                    int idx = file.getName().lastIndexOf('.');
                    var inputName = file.getName().substring(0, idx);
                    var ext = file.getName().substring(idx);
                    name = inputName + '-' + classifier + ext;

                    if (name.equals(file.getName()))
                        name = inputName + classifier + "-renamed" + ext;
                }
                return new File(file.getParentFile(), name);
            })).orElse(this.getDefaultOutputFile())
        );
        this.getLibraries().convention(getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getCompileClasspath));

        this.getArchiveExtension().convention("jar");
        this.getArchiveDate().convention(this.getInput().map(input -> new Date(input.getAsFile().lastModified())));
    }

    public void from(AbstractArchiveTask task) {
        this.from(getProject().getTasks().named(task.getName(), AbstractArchiveTask.class));
    }

    public void from(TaskProvider<? extends AbstractArchiveTask> task) {
        this.getInput().set(task.flatMap(AbstractArchiveTask::getArchiveFile));
        this.getArchiveClassifier().set(task.flatMap(AbstractArchiveTask::getArchiveClassifier).filter(Util.STRING_IS_PRESENT).map(s -> s + "-renamed").orElse("renamed"));
        this.getArchiveExtension().set(task.flatMap(AbstractArchiveTask::getArchiveExtension).orElse("jar"));
    }

    public void mappings(String artifact) {
        this.mappings(getDependencyFactory().create(artifact));
    }

    public void mappings(Dependency dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
    }

    public void mappings(Provider<? extends Dependency> dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration();
        configuration.getDependencies().addLater(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
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

