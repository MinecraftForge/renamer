/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public interface MixinSourceSetConfig {
	Property<String> getRefMap();
	Property<Boolean> getDisableTargetValidator();
	Property<Boolean> getDisableTargetExport();
	Property<Boolean> getDisableOverwriteChecker();
	Property<String> getOverwriteErrorLevel();
	Property<String> getDefaultObfuscationEnv();
    ListProperty<String> getMappingTypes();
	MapProperty<String, String> getTokens();
	ConfigurableFileCollection getExtraMappings();
	Property<Boolean> getQuiet();
	Property<Boolean> getShowMessageTypes();
	MapProperty<String, String> getMessages();
}