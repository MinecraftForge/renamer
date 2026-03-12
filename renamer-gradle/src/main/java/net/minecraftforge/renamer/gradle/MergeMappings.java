/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.Format;

public abstract class MergeMappings extends DefaultTask implements RenamerTask {
	public abstract @InputFiles @Classpath ListProperty<ConfigurableFileCollection> getMaps();
	public abstract @OutputFile RegularFileProperty getOutput();
	public abstract @Input Property<String> getFormat();
	public abstract @Input Property<Boolean> getReverse();

	@Inject
	public MergeMappings() {
		var output = getProject().getLayout().getBuildDirectory().dir("mappings");
		this.getOutput().convention(output.map(d -> d.file(getName() + '.' + this.getFormat().get())));
		this.getFormat().convention("tsrg");
		this.getReverse().convention(false);
	}

	@TaskAction
	protected void exec() throws IOException {
		var output = getOutput().getAsFile().get();

		var format = Format.get(getFormat().get().toLowerCase(Locale.ENGLISH));
		if (format == null)
			throw new IllegalArgumentException("Unknown format: " + getFormat().get());

		IMappingFile map = null;
		for (var cfg : getMaps().get()) {
			// I am specifically forcing this to be a single file to go along with other tasks
			// And because ConfigurableFileCollections use a set which is unordered and order matters
			var file = cfg.getSingleFile();
			// Sometimes we get files that don't exist, from Mixin bullshit
			if (!file.exists())
				continue;

			var current = IMappingFile.load(file);
			if (map == null)
				map = current;
			else
				map = map.merge(current);
		}

		Files.createDirectories(output.getParentFile().toPath());
		map.write(output.toPath(), format, getReverse().get());
	}

	public ConfigurableFileCollection map(Provider<?> provider) {
		var ret = this.getProject().files(Util.toConfiguration(getProject(), provider));
		this.getMaps().add(ret);
		return ret;
	}

	public ConfigurableFileCollection map(TaskProvider<?> task) {
		var ret = this.getProject().files(Util.toFile(task));
		this.getMaps().add(ret);
		return ret;
	}

	public ConfigurableFileCollection map(ConfigurableFileCollection value) {
		this.getMaps().add(value);
		return value;
	}
}
