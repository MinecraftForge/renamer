/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import net.minecraftforge.gradleutils.shared.SharedUtil;

import java.io.File;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jspecify.annotations.Nullable;

final class Util extends SharedUtil {
    static final Spec<? super String> STRING_IS_PRESENT = s -> !s.isBlank();

    static @Nullable SourceSet findSourceSetFromJar(Project project, String jarTaskName) {
        var candidates = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().matching(sourceSet -> sourceSet.getJarTaskName().equals(jarTaskName)).iterator();
        return candidates.hasNext() ? candidates.next() : null;
    }

    static Configuration toConfiguration(Project project, Provider<?> input) {
    	if (input == null)
    		return null;
    	var ret = project.getConfigurations().detachedConfiguration();
    	ret.setTransitive(false);
    	var deps = project.getDependencies();
    	ret.getDependencies().addLater(input.map(value -> {
    		if (value instanceof Dependency dep)
    			return dep;
    		if (value instanceof File file)
    			return deps.create(project.files(file));
    		return deps.create(value);
    	}));
    	return ret;
    }

    // Note: I would rather have this take in ? extends SingleFileOutput but this isn't FG and thats not a standard helper interface
    static Provider<File> toFile(TaskProvider<?> provider) {
    	return provider.flatMap(task -> ((RegularFileProperty)InvokerHelper.getProperty(task, "output")).getAsFile());
    }
}
