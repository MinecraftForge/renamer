/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.JavaVersion;
import org.gradle.api.logging.LogLevel;
import org.gradle.process.ExecResult;

import net.minecraftforge.gradleutils.shared.ToolExecBase;

import javax.inject.Inject;
import java.io.IOException;

abstract class RenameSourceBase extends ToolExecBase<RenamerProblems> implements RenamerTask {
    // NOTE: check net.minecraftforge.srg2source.api.SourceVersion for compatible ranges
    private static final int SRC_COMPAT_MIN = 6;
    private static final String SRC_COMPAT_MIN_STR = "1.6";
    private static final int SRC_COMPAT_MAX = 23;
    private static final String SRC_COMPAT_MAX_STR = "23";

    @Inject
    RenameSourceBase() {
        super(Tools.SRG2SRC);
        this.getStandardOutputLogLevel().set(LogLevel.INFO);
    }

    @Override
    protected ExecResult exec() throws IOException {
        return super.exec().rethrowFailure().assertNormalExitValue();
    }

    protected final String parseSourceCompatibility(JavaVersion javaVersion) {
        int version = javaVersion.ordinal() + 1;

        if (version < SRC_COMPAT_MIN) {
            this.getLogger().warn("WARNING: {} source compatibility {} is lower than minimum of {}", this.getIdentityPath(), version, SRC_COMPAT_MIN_STR);
            return SRC_COMPAT_MIN_STR;
        } else if (version > SRC_COMPAT_MAX) {
            this.getLogger().warn("WARNING: {} source compatibility {} is higher than the maximum of {}", this.getIdentityPath(), version, SRC_COMPAT_MAX_STR);
            return SRC_COMPAT_MAX_STR;
        } else {
            return Integer.toString(version);
        }
    }
}
