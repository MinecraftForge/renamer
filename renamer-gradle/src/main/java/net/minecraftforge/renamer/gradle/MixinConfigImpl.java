/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

abstract class MixinConfigImpl implements MixinConfig {
	private static final Logger LOGGER = LogManager.getLogger(MixinConfig.class);
	private final RenamerExtensionImpl ext;
	private final Map<String, MixinSourceSetConfigImpl> sourceSets = new HashMap<>();
	private final TaskProvider<ConvertMappings> formatMappings;
	private final TaskProvider<MergeMappings> generatedMappings;

	@Inject
	public MixinConfigImpl(RenamerExtensionImpl ext) {
		this.ext = ext;
		this.getMappingTypes().convention(List.of("tsrg"));
    	this.formatMappings = ext.convert("formatMixinMappings", null, "tsrg", task -> task.map(ext.mappings));
    	this.generatedMappings = ext.merge("mergeMixinMappings", task -> task.map(this.formatMappings));

		/*
		 * Mixin Magic:
		 *   Run Configs:
		 *     Args:
		 *       --mixin.config <filename> - the Mixin config file, I think users should manually specify this
		 *
		 *     System Property:
		 *       mixin.env.disableRefMap = true - Disable ref maps. Would be the preferred option once Renamer supports renaming mixins
		 *       mixin.env.remapRefMap = true/false - True if we need to remap refmaps,
		 *       mixin.env.refMapRemappingFile = <map file> - The map needs to be Dependency runtime -> Dev Runtime So typically SRG->Named
		 *
		 *  Compile:
		 *    -AreobfTsrgFile=
		 *    -AoutTsrgFile=
		 *    -AoutRefMapfile=
		 *    -AdisableTargetValidator=true
		 *    -AdisableTargetExport=true
		 *    -AdisableOverwriteChecker=true
		 *    -Aquiet=true
		 *    -DshowMessageTypes=true
		 *    -AoverwriteErrorLevel=level
		 *    -AdefaultObfuscationEnv=searge
		 *    -AmappingTypes=tsrg
		 *    -Atokens=key=value;
		 *    -AreobfTsrgFiles=ExtraMapping.tsrg;
		 *    -AMSG_{NAME}={value}
		 *    Not Supported:
		 *      -ApluginVersion=MixinGradlePlugin.VERSION
		 *      -AdependencyTargetsFile
		 */
	}

	@Override
	public TaskProvider<MergeMappings> getGeneratedMappings() {
		return this.generatedMappings;
	}

	@Override
	public TaskProvider<ConvertMappings> getMappings() {
		return this.formatMappings;
	}

	@Override
	public MixinSourceSetConfig source(SourceSet source, String name, Action<? super MixinSourceSetConfig> action) {
		if (this.sourceSets.containsKey(source.getName()))
			throw new IllegalStateException("Can not register the same sourceset multiple times");
		LOGGER.info("[Renamer][Mixin] Configuring SourceSet: " + source.getName());
		var ret = ext.getProject().getObjects().newInstance(MixinSourceSetConfigImpl.class, this.ext, this, name, source);
		this.sourceSets.put(source.getName(), ret);
		action.execute(ret);
		return ret;
	}

	@Override
	public void run(Object runConfig) {
		LOGGER.info("[Renamer][Mixin] Configuring Run: " + InvokerHelper.getProperty(runConfig, "name"));
		InvokerHelper.invokeMethod(runConfig, "args", this.getConfigs().map(list -> {
			var ret = new ArrayList<String>(list.size() * 2);
			for (var cfg : list) {
				ret.add("--mixin.config");
				ret.add(cfg);
			}
			return ret;
		}));
		//InvokerHelper.invokeMethod(runConfig, "systemProperty", new String[]{"mixin.env.disableRefMap", "true"});
		InvokerHelper.invokeMethod(runConfig, "systemProperties", this.generatedMappings.flatMap(task -> task.getOutput()).map(
			file -> Map.of(
				"mixin.env.remapRefMap", "true",
				"mixin.env.refMapRemappingFile", file.getAsFile().getAbsolutePath()
			)
		));
	}

	@Override
	public void jar(TaskProvider<Jar> provider) {
		provider.configure(task -> {
        	// Add MixinConfigs Manifest entry
    		if (!task.getManifest().getAttributes().containsKey("MixinConfigs"))
    			task.getManifest().attributes(Map.of("MixinConfigs", String.join(",", this.getConfigs().get())));

    		// Add the refmap files
    		for (var inst : this.sourceSets.values()) {
    			var ext = inst.sourceSet.getExtensions().getExtraProperties();
    			@SuppressWarnings("unchecked")
				var file = (Provider<File>)ext.get("refMapFile");
    			LOGGER.info("[Renamer][Mixin] Adding " + inst.getRefMap().get() + " to " +  task.getName() + " from " + inst.sourceSet.getCompileJavaTaskName());
    			task.from(file, cfg -> cfg.rename(name -> inst.getRefMap().get()));
    		}
    	});
	}
}
