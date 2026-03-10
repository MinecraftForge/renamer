/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

/// The extension interface for the Renamer Gradle plugin.
public interface RenamerExtension {
    /// The name for this extension when added to [projects][org.gradle.api.Project].
    String NAME = "renamer";

    default void mappings(String channel, String version) {
        mappings("net.minecraft:mappings_" + channel + ':' + version + "@tsrg.gz");
    }

    void mappings(String artifact);

    void mappings(Dependency dependency);

    void mappings(Provider<?> dependency);

    void mappings(TaskProvider<?> task);

    default void mappings(ProviderConvertible<?> dependency) {
        this.mappings(dependency.asProvider());
    }

    void setMappings(FileCollection files);

    default TaskProvider<RenameJar> classes(AbstractArchiveTask input) {
        return this.classes(input, it -> it.from(input));
    }

    default TaskProvider<RenameJar> classes(AbstractArchiveTask input, Action<? super RenameJar> action) {
        return this.classes("rename" + StringGroovyMethods.capitalize(input.getName()), input, action);
    }

    default TaskProvider<RenameJar> classes(TaskProvider<? extends AbstractArchiveTask> input) {
        return this.classes(input, it -> it.from(input));
    }

    default TaskProvider<RenameJar> classes(TaskProvider<? extends AbstractArchiveTask> input, Action<? super RenameJar> action) {
        return this.classes("rename" + StringGroovyMethods.capitalize(input.getName()), input, action);
    }

    default TaskProvider<RenameJar> classes(String name, AbstractArchiveTask input) {
        return this.classes(input, it -> it.from(input));
    }

    TaskProvider<RenameJar> classes(String name, AbstractArchiveTask input, Action<? super RenameJar> action);

    default TaskProvider<RenameJar> classes(String name, TaskProvider<? extends AbstractArchiveTask> input) {
        return this.classes(input, it -> it.from(input));
    }

    TaskProvider<RenameJar> classes(String name, TaskProvider<? extends AbstractArchiveTask> input, Action<? super RenameJar> action);


    // Convert mapping files from one format to another, Provider version accepts anything that can be resolved to a dependency
    default TaskProvider<ConvertMappings> convert(String name) {
    	return convert(name, (Provider<?>)null, "tsrg", task -> {});
    }
    default TaskProvider<ConvertMappings> convert(String name, Action<? super ConvertMappings> action) {
    	return convert(name, (Provider<?>)null, "tsrg", action);
    }
    default TaskProvider<ConvertMappings> convert(String name, Provider<?> input) {
    	return convert(name, input, "tsrg", task -> {});
    }
    default TaskProvider<ConvertMappings> convert(String name, Provider<?> input, Action<? super ConvertMappings> action) {
    	return convert(name, input, "tsrg", action);
    }
    default TaskProvider<ConvertMappings> convert(String name, Provider<?> input, String format) {
    	return convert(name, input, format, task -> {});
    }
    TaskProvider<ConvertMappings> convert(String name, Provider<?> input, String format, Action<? super ConvertMappings> action);


    // Convert mappings files from one format to another, TaskProvider accepts anything that has a 'RegularFileProperty getOutput()'
    default TaskProvider<ConvertMappings> convert(String name, TaskProvider<?> input) {
    	return convert(name, input, "tsrg", task -> {});
    }
    default TaskProvider<ConvertMappings> convert(String name, TaskProvider<?> input, Action<? super ConvertMappings> action) {
    	return convert(name, input, "tsrg", action);
    }
    default TaskProvider<ConvertMappings> convert(String name, TaskProvider<?> input, String format) {
    	return convert(name, input, format, task -> {});
    }
    TaskProvider<ConvertMappings> convert(String name, TaskProvider<?> input, String format, Action<? super ConvertMappings> action);

    // Chains two mapping files together, Provider accepts anything that can be resolved to a dependency, TaskProvider accepts anything that has a 'RegularFileProperty getOutput()'
    default TaskProvider<ChainMappings> chain(String name) {
    	return chain(name, task -> {});
    }
    TaskProvider<ChainMappings> chain(String name, Action<? super ChainMappings> action);

    // Merges multiple mapping files together, Provider accepts anything that can be resolved to a dependency, TaskProvider accepts anything that has a 'RegularFileProperty getOutput()'
    default TaskProvider<MergeMappings> merge(String name) {
    	return merge(name, task -> {});
    }
    TaskProvider<MergeMappings> merge(String name, Action<? super MergeMappings> action);

    /// Used for deobfuscating dependencies.
    /// This does not support source file deobfuscation
    default Provider<Dependency> dependency(String coordinates) {
    	return dependency(coordinates, task -> {});
    }
    Provider<Dependency> dependency(String coordinates, Action<? super RenameJar> action);
}
