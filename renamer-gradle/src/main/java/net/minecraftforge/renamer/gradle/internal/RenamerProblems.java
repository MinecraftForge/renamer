/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import javax.inject.Inject;

import net.minecraftforge.renamer.gradle.RenamerExtension;
import org.gradle.api.Task;
import org.gradle.api.problems.Severity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.Serial;

abstract class RenamerProblems extends EnhancedProblems {
    private static final @Serial long serialVersionUID = -5334414678185075096L;

    @Inject
    public RenamerProblems() {
        super(RenamerExtension.NAME, RenamerPlugin.DISPLAY_NAME);
    }

    void reportUnknownSourceSetFromTask(TaskProvider<? extends AbstractArchiveTask> task) {
        report("unknown-task-source-set", "Could not find source set containing task '%s'".formatted(task.getName()), spec -> spec
            .details("""
                Could not find the source set that contains task '%s'
                The 'main' source set will be used as a fallback. This may cause issues renaming the task's output file.""")
            .severity(Severity.ERROR)
            .solution("Use the `renamer.rename` methods that accept a source set used to build the task.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException containerNotYetRegistered(Exception e) {
        getLogger().error("ERROR: Renamer container not yet registered.");
        return throwing(e, "renamer-container-not-registered", "Renamer container not yet registered", spec -> spec
            .details("""
                Cannot access Renamer details before it has been registered!
                A container must first be registered using `renamer.rename`.""")
            .severity(Severity.ERROR)
            .solution("Use the `renamer.rename` method at least once to register a container that can be referenced by the `renamer` extension.")
            .solution(HELP_MESSAGE));
    }

    void reportIllegalTaskName(Task existing, String name) {
        this.getLogger().error("ERROR: Cannot register renamer task {}, name already exists", name);
        this.report("rename-duplicate-task-name", "Cannot register renamer task", spec -> spec
            .details("""
                Cannot register renamer task, as a task with that name already exists.
                Name: %s"""
                .formatted(name))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Use the `renamer.rename` methods that take in a explicit task name.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException reportMultipleMapFiles(Throwable e, Task task) {
        getLogger().error("ERROR: Failed to find Mapping File");
        return throwing(e, "rename-multiple-map-files", "Renamer Map File returned to many files", spec -> spec
            .details("""
                Only expected one file for the renaming map task '%s'.
                If using a configuration to resolve the file, be sure to disable transitive dependencies."""
                .formatted(task.getName()))
            .severity(Severity.ERROR)
            .solution("Specify one map file for task '" + task.getName() + "'.")
            .solution(HELP_MESSAGE));
    }
}
