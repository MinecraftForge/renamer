/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import net.minecraftforge.gradleutils.shared.Tool;

final class Tools {
    private Tools() { }

    static final Tool RENAMER = Tool.ofForge("classes", "net.minecraftforge:renamer:2.0.6:all", 8);
    static final Tool SRG2SRC = Tool.ofForge("srg2source", "net.minecraftforge:Srg2Source:8.2.1:fatjar", 17);
}
