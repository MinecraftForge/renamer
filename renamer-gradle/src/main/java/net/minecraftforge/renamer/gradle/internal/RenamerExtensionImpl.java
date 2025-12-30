/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.renamer.gradle.RenamerContainer;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.File;

import javax.inject.Inject;

abstract class RenamerExtensionImpl implements RenamerExtensionInternal {
    private static final Logger LOGGER = Logging.getLogger(RenamerExtension.class);

    private final RenamerProblems problems = getObjects().newInstance(RenamerProblems.class);

    protected abstract @Inject Project getProject();

    protected abstract @Inject ObjectFactory getObjects();

    private @Nullable RenamerContainerInternal container;

    @Inject
    public RenamerExtensionImpl() { }

    @Override
    public RenamerContainerInternal getContainer() {
        if (this.container == null)
            throw problems.containerNotYetRegistered(new IllegalStateException("Renamer container not yet registered"));

        return this.container;
    }

    @Override
    public RenamerContainer register(String name, Action<? super RenamerContainer> action) {
        return this.register(
            getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME),
            name,
            action
        );
    }

    @Override
    @NullUnmarked
    public RenamerContainer register(SourceSet sourceSet, String name, Action<? super RenamerContainer> action) {
        this.container = getObjects().newInstance(RenamerContainerImpl.class, sourceSet, name);
        action.execute(this.container);
        return this.container;
    }
}
