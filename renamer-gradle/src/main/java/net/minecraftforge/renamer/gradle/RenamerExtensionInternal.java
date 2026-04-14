/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.Action;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

interface RenamerExtensionInternal extends RenamerExtension, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(RenamerExtension.class);
    }

    @Override
    default TaskProvider<RenameJar> classes(String name, AbstractArchiveTask input, Action<? super RenameJar> action) {
        return this.classes(name, it -> {
            it.from(input);
            action.execute(it);
        });
    }

    @Override
    default TaskProvider<RenameJar> classes(String name, TaskProvider<? extends AbstractArchiveTask> input, Action<? super RenameJar> action) {
        return this.classes(name, it -> {
            it.from(input);
            action.execute(it);
        });
    }
}
