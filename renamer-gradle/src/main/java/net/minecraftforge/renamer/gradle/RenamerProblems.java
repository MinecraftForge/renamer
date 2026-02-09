/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import javax.inject.Inject;

import org.gradle.api.Task;
import org.gradle.api.problems.Severity;
import java.io.Serial;

abstract class RenamerProblems extends EnhancedProblems {
    private static final @Serial long serialVersionUID = -5334414678185075096L;

    @Inject
    public RenamerProblems() {
        super(RenamerPlugin.NAME, RenamerPlugin.DISPLAY_NAME);
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
