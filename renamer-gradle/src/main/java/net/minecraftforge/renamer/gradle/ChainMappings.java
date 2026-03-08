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

public abstract class ChainMappings extends DefaultTask implements RenamerTask {
	public abstract @InputFiles @Classpath ConfigurableFileCollection getLeft();
	public abstract @InputFiles @Classpath ConfigurableFileCollection getRight();
	public abstract @OutputFile RegularFileProperty getOutput();
	public abstract @Input Property<String> getFormat();
	public abstract @Input Property<Boolean> getReverse();

	@Inject
	public ChainMappings() {
		var output = getProject().getLayout().getBuildDirectory().dir("mappings");
		this.getOutput().convention(output.map(d -> d.file(getName() + '.' + this.getFormat().get())));
		this.getFormat().convention("tsrg");
		this.getReverse().convention(false);
	}

	@TaskAction
	protected void exec() throws IOException {
		var left = IMappingFile.load(this.getLeft().getSingleFile());
		var right = IMappingFile.load(this.getRight().getSingleFile());

		var output = getOutput().getAsFile().get();

		var format = Format.get(getFormat().get().toLowerCase(Locale.ENGLISH));
		if (format == null)
			throw new IllegalArgumentException("Unknown format: " + getFormat().get());

		Files.createDirectories(output.getParentFile().toPath());
		var map = left.chain(right);
		map.write(output.toPath(), format, getReverse().get());
	}

	public void left(Provider<?> provider) {
		this.getLeft().setFrom(Util.toConfiguration(getProject(), provider));
	}

	public void left(TaskProvider<?> task) {
		this.getLeft().setFrom(Util.toFile(task));
	}

	public void left(ConfigurableFileCollection value) {
		this.getLeft().setFrom(value);
	}

	public void right(Provider<?> provider) {
		this.getRight().setFrom(Util.toConfiguration(getProject(), provider));
	}

	public void right(TaskProvider<?> task) {
		this.getRight().setFrom(Util.toFile(task));
	}

	public void right(ConfigurableFileCollection value) {
		this.getRight().setFrom(value);
	}
}
