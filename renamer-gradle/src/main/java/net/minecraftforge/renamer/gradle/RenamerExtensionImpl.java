/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

abstract class RenamerExtensionImpl implements RenamerExtensionInternal {
    // Renamer inputs
    final ConfigurableFileCollection mappings = getObjects().fileCollection();
	private int dependencyCount = 0;
	boolean defaultMixinBehavior = true;
	private MixinConfigImpl mixin = null;

    protected abstract @Inject Project getProject();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject DependencyFactory getDependencies();

    @Inject
    public RenamerExtensionImpl() { }

    @Override
    public void mappings(String artifact) {
        this.mappings(getDependencies().create(artifact));
    }

    @Override
    public void mappings(Dependency dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
    }

    @Override
    public void mappings(Provider<?> dependency) {
    	this.mappings.setFrom(Util.toConfiguration(getProject(), dependency));
    }

    @Override
    public void mappings(TaskProvider<?> task) {
    	this.mappings.setFrom(Util.toFile(task));
    }

    @Override
    public void setMappings(FileCollection files) {
        this.mappings.setFrom(files);
    }

    @Override
    public ConfigurableFileCollection getMappings() {
    	return this.mappings;
    }

    private static final String ASSEMBLE = "assemble";
    @Override
    public TaskProvider<RenameJar> classes(String name, Action<? super RenameJar> action) {
    	var tasks = getProject().getTasks();
        var ret = tasks.register(name, RenameJar.class, this);
        ret.configure(action);

        // Make the assemble task build our file, like the normal java plugin does
        if (tasks.getNames().contains(ASSEMBLE))
        	tasks.named(ASSEMBLE).configure(task -> task.dependsOn(ret));

        return ret;
    }

    @Override
    public TaskProvider<ConvertMappings> convert(String name, @Nullable Provider<?> input, String format, Action<? super ConvertMappings> action) {
    	var output = getProject().getLayout().getBuildDirectory().file("mappings/" + name + '.' + format);
    	return getProject().getTasks().register(name, ConvertMappings.class, task -> {
    		if (input != null)
    			task.map(input);
    		task.getFormat().set(format);
    		task.getOutput().set(output);
    		action.execute(task);
    	});
    }

    @Override
    public TaskProvider<ConvertMappings> convert(String name, @Nullable TaskProvider<?> input, String format, Action<? super ConvertMappings> action) {
    	var output = getProject().getLayout().getBuildDirectory().file("mappings/" + name + '.' + format);
    	return getProject().getTasks().register(name, ConvertMappings.class, task -> {
    		if (input != null)
    			task.map(input);
    		task.getFormat().set(format);
    		task.getOutput().set(output);
    		action.execute(task);
    	});
    }

    @Override
    public TaskProvider<ChainMappings> chain(String name, Action<? super ChainMappings> action) {
    	return getProject().getTasks().register(name, ChainMappings.class, action);
    }

    @Override
    public TaskProvider<MergeMappings> merge(String name, Action<? super MergeMappings> action) {
    	return getProject().getTasks().register(name, MergeMappings.class, action);
    }

    @Override
    public Provider<Dependency> dependency(String notation, Action<? super RenameJar> action) {
    	var dep = this.getProject().getDependencies().create(notation);
    	var self = this.getProject().getConfigurations().detachedConfiguration(dep);
    	self.setTransitive(false);
    	var deps = this.getProject().getConfigurations().detachedConfiguration(dep);
    	var libraries = deps.minus(self);
    	var rename = this.getProject().getTasks().register("_rename_dep_" + this.dependencyCount++, RenameJar.class, this);
    	rename.configure(task -> {
    		task.getMap().setFrom(this.mappings);
    		task.getInput().set(self.getSingleFile());
    		task.getLibraries().setFrom(libraries);
    		action.execute(task);
    	});
    	return rename.map(task -> this.getProject().getDependencies().create(this.getProject().files(task)));
	}

    @Override
    public MixinConfig getMixin() {
    	if (this.mixin == null) {
        	this.mixin = this.getObjects().newInstance(MixinConfigImpl.class, this);
        	this.getProject().afterEvaluate(this::mixinDefaultActions);
    	}
    	return this.mixin;
    }

    @Override
    public MixinConfig enableMixinRefmaps(Action<MixinConfig> action) {
    	var ret = getMixin();
    	action.execute(ret);
    	return ret;
    }

    private void mixinDefaultActions(Project project) {
    	if (!this.defaultMixinBehavior)
    		return;

    	var java = project.getExtensions().findByType(JavaPluginExtension.class);
    	// can't do shit if this isn't java
    	if (java == null)
    		return;

    	for (var sourceSet : java.getSourceSets())
    		this.mixin.source(sourceSet);

    	this.mixin.jar(project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class));

    	var minecraft = project.getExtensions().findByName("minecraft");
    	var runs = minecraft == null ? null : (NamedDomainObjectContainer<?>)InvokerHelper.getProperty(minecraft, "runs");
    	if (runs != null) {
    		for (var run : runs)
    			this.mixin.run(run);
    	}
    }
}
