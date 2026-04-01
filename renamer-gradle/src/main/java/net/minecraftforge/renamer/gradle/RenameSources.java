/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import java.io.File;
import java.util.Date;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jspecify.annotations.Nullable;

public abstract class RenameSources implements PublishArtifact {
	private final Project project;
	private final TaskProvider<RenameSourcesApply> apply;
	private final TaskProvider<RenameSourcesExtract> extract;
    private boolean naiveSrg = false;

	@Inject
	public RenameSources(Project project, TaskProvider<RenameSourcesApply> apply, TaskProvider<RenameSourcesExtract> extract) {
		this.project = project;
		this.apply = apply;
		this.extract = extract;
		setNaiveSrg(false);
	}

	public TaskProvider<RenameSourcesApply> getApply() {
		return this.apply;
	}
	public TaskProvider<RenameSourcesApply> apply(Action<? super RenameSourcesApply> action) {
		var ret = getApply();
		ret.configure(action);
		return ret;
	}

	public TaskProvider<RenameSourcesExtract> getExtract() {
		return this.extract;
	}
	public TaskProvider<RenameSourcesExtract> extract(Action<? super RenameSourcesExtract> action) {
		var ret = getExtract();
		ret.configure(action);
		return ret;
	}


    public boolean getNaiveSrg() {
    	return this.naiveSrg;
    }
    public void setNaiveSrg(boolean value) {
    	this.naiveSrg = value;
    	if (value)
    		this.apply(task -> task.getRangeMap().unset());
    	else
    		this.apply(task -> task.getRangeMap().set(getExtract().flatMap(RenameSourcesExtract::getOutput)));
    }

    public void from(SourceSet input) {
    	extract(task -> {
    		task.getSources().from(input.getJava().getSourceDirectories());
    		task.getDependencies().from(input.getCompileClasspath());
    	});
    	apply(task -> {
    		task.getSources().from(input.getJava().getSourceDirectories());
    	});
    }

    public void from(AbstractArchiveTask task) {
        this.from(project.getTasks().named(task.getName(), AbstractArchiveTask.class));
    }

    public void from(TaskProvider<? extends AbstractArchiveTask> jar) {
    	extract(task -> {
    		task.getSources().setFrom(jar.flatMap(AbstractArchiveTask::getArchiveFile));
    	});
    	apply(task -> {
	        task.getSources().setFrom(jar.flatMap(AbstractArchiveTask::getArchiveFile));
	        task.getArchiveClassifier().set(jar.flatMap(AbstractArchiveTask::getArchiveClassifier).filter(Util.STRING_IS_PRESENT).map(s -> s + "-renamed").orElse("renamed"));
	        task.getArchiveExtension().set(jar.flatMap(AbstractArchiveTask::getArchiveExtension).orElse("jar"));
    	});
    }


    // Implement PublishArtifact so people can use `renameSources` not `renameSources.apply` in maven publish
	@Override
	public @Internal TaskDependency getBuildDependencies() {
		return getApply().get().getBuildDependencies();
	}

	@Override
	public @Internal String getName() {
		return getApply().get().getName();
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Internal String getExtension() {
		return getApply().get().getExtension();
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Internal String getType() {
		return getApply().get().getType();
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nullable @Internal String getClassifier() {
		return getApply().get().getClassifier();
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Internal File getFile() {
		return getApply().get().getFile();
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nullable @Internal Date getDate() {
		return getApply().get().getDate();
	}
}
