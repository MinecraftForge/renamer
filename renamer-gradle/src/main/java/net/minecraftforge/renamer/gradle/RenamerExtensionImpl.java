/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import javax.inject.Inject;

abstract class RenamerExtensionImpl implements RenamerExtensionInternal {
    // Renamer inputs
    final ConfigurableFileCollection mappings = getObjects().fileCollection();

    protected abstract @Inject Project getProject();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject DependencyFactory getDependencies();

    @Inject
    public RenamerExtensionImpl() { }

    @Override
    public void mappings(String artifact) {
        this.mappings(getDependencies().create(artifact));
    }

    @Override
    public void mappings(Dependency dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
    }

    @Override
    public void mappings(Provider<? extends Dependency> dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration();
        configuration.getDependencies().addLater(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
    }

    @Override
    public void setMappings(FileCollection files) {
        this.mappings.setFrom(files);
    }

    @Override
    public TaskProvider<RenameJar> classes(String name, Action<? super RenameJar> action) {
        return getProject().getTasks().register(name, RenameJar.class, action);
    }
}
