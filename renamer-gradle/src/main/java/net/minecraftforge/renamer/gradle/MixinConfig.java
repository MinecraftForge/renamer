/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import java.util.Locale;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

public interface MixinConfig extends MixinSourceSetConfig {
	ListProperty<String> getConfigs();
	default void config(String name) {
		getConfigs().add(name);
	}
	default void config(Provider<String> name) {
		getConfigs().add(name);
	}

	default MixinSourceSetConfig source(SourceSet source) {
		return source(source, source.getName().toLowerCase(Locale.ENGLISH));
	}
	default MixinSourceSetConfig source(SourceSet source, Action<? super MixinSourceSetConfig> action) {
		return source(source, source.getName().toLowerCase(Locale.ENGLISH), action);
	}
	default MixinSourceSetConfig source(SourceSet source, String name) {
		return source(source, name, cfg -> {});
	}
	MixinSourceSetConfig source(SourceSet source, String name, Action<? super MixinSourceSetConfig> action);

	void run(Object runConfig);
	void jar(TaskProvider<Jar> provider);
}
