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

    /**
     * {@inheritDoc}
     * Add a Mixin config
     * @param name path to config file, searches from {@code resources/}
     */
	default void config(String name) {
		getConfigs().add(name);
	}

    /**
     * {@inheritDoc}
     * Add a Mixin config
     * @param name path to config file, searches from {@code resources/}
     */
	default void config(Provider<String> name) {
		getConfigs().add(name);
	}

    /**
     * {@inheritDoc}
     * Add a source set to Mixin, automatically infers refmap name
     * @param source source set to add
     */
	default MixinSourceSetConfig source(SourceSet source) {
		return source(source, source.getName().toLowerCase(Locale.ENGLISH));
	}

    /**
     * {@inheritDoc}
     * Add and source set to Mixin and configure it, automatically infers refmap name
     * @param source source set to add
     * @param action configuration for the source set
     */
	default MixinSourceSetConfig source(SourceSet source, Action<? super MixinSourceSetConfig> action) {
		return source(source, source.getName().toLowerCase(Locale.ENGLISH), action);
	}

    /**
     * {@inheritDoc}
     * Add a source set to Mixin
     * @param source source set to add
     * @param name prefix to the output refmap.json file
     */
	default MixinSourceSetConfig source(SourceSet source, String name) {
		return source(source, name, cfg -> {});
	}

    /**
     * {@inheritDoc}
     * Add and source set to Mixin and configure it
     * @param source source set to add
     * @param name prefix to the output refmap.json file
     * @param action configuration for the source set
     */
	MixinSourceSetConfig source(SourceSet source, String name, Action<? super MixinSourceSetConfig> action);

	void run(Object runConfig);
	void jar(TaskProvider<Jar> provider);
}
