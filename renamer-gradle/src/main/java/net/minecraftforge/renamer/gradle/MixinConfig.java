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

	/**
	 * Adds The following to the specified ForgeGradle run config:
	 * <ul>
	 *   <li>
	 *     <u>Args:</u>
	 *     <p>
	 *     {@code "--mixin-config [config]"} - Tells Mixin to load specific configs, this is not needed if you specify hem in your Manifest file
	 *   </li>
	 *   <li>
	 *     <u>System Properties:</u>
	 *     <ul>
	 *       <li>{@code "mixin.env.remapRefMap: true"} - Tells Mixin to remap refmaps when loading</li>
	 *       <li>{@code "mixin.env.refMapremappingFile: [map file]"} - The mapping file used to generate the refmap</li>
	 *     </ul>
	 *   </li>
	 * </ul>
	 *
	 * @param runConfig
	 *
	 */
	void run(Object runConfig);

	/**
	 * Configures a Jar task with Mixin related values.
	 * Specifically it adds a Manifest entry called {@code MixinConfigs} with a comma separated list of config files.
	 * It also forces the inclusion of any refMap files for registered sourcesets
	 *
	 * @param provider The jar task provider
	 *
	 */
	void jar(TaskProvider<Jar> provider);

	/**
	 * Gets the task that merges all generated extra mappings merged together.
	 */
	TaskProvider<MergeMappings> getGeneratedMappings();

	/**
	 * Gets the task that converts a mapping file into the format needed by the Annotation Processor
	 * This can be used to customize what mappings are applied.
	 * Defaults to converting {@link RenamerExtension#getMappings()} to {@code `tsrg`}
	 */
	TaskProvider<ConvertMappings> getMappings();
}
