/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.renamer.gradle.RenamerConfiguration;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

interface RenamerExtensionInternal extends RenamerExtension, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(RenamerExtension.class);
    }

    /* CONTAINER PROXY */

    RenamerContainerInternal getContainer();

    @Override
    default void mappings(String artifact) {
        this.getContainer().mappings(artifact);
    }

    @Override
    default void mappings(Dependency dependency) {
        this.getContainer().mappings(dependency);
    }

    @Override
    default void mappings(Provider<? extends Dependency> dependency) {
        this.getContainer().mappings(dependency);
    }

    @Override
    default void setMappings(FileCollection files) {
        this.getContainer().setMappings(files);
    }

    @Override
    default void classes(Action<? super RenamerConfiguration> action) {
        this.getContainer().classes(action);
    }

    @Override
    default String getName() {
        return this.getContainer().getName();
    }
}
