/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

/// The extension interface for the Renamer Gradle plugin.
public interface RenamerExtension extends RenamerContainer {
    /// The name for this extension when added to [projects][org.gradle.api.Project].
    String NAME = "renamer";

    default RenamerContainer register() {
        return this.register(emptyAction());
    }

    default RenamerContainer register(String name) {
        return this.register(name, emptyAction());
    }

    default RenamerContainer register(SourceSet sourceSet) {
        return this.register(sourceSet, emptyAction());
    }

    default RenamerContainer register(SourceSet sourceSet,  String name) {
        return this.register(sourceSet, name, emptyAction());
    }

    default RenamerContainer register(Provider<? extends SourceSet> sourceSet) {
        return this.register(sourceSet, emptyAction());
    }

    default RenamerContainer register(Provider<? extends SourceSet> sourceSet,  String name) {
        return this.register(sourceSet, name, emptyAction());
    }

    default RenamerContainer register(Action<? super RenamerContainer> action) {
        return this.register("", action);
    }

    RenamerContainer register(String name, Action<? super RenamerContainer> action);

    default RenamerContainer register(SourceSet sourceSet, Action<? super RenamerContainer> action) {
        return this.register(sourceSet, "", action);
    }

    RenamerContainer register(SourceSet sourceSet,  String name, Action<? super RenamerContainer> action);

    default RenamerContainer register(Provider<? extends SourceSet> sourceSet, Action<? super RenamerContainer> action) {
        return register(sourceSet.get(), action);
    }

    default RenamerContainer register(Provider<? extends SourceSet> sourceSet, String name, Action<? super RenamerContainer> action) {
        return register(sourceSet.get(), name, action);
    }

    private static Action<? super RenamerContainer> emptyAction() {
        return it -> { };
    }
}
