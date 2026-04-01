/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.process.ExecResult;
import org.jspecify.annotations.Nullable;

import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IRenamer;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.INode;
import net.minecraftforge.srgutils.IMappingFile.IParameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

public abstract class RenameSourcesApply extends RenameSourceBase implements PublishArtifact {
	private static final Pattern SRG_PATTERN = Pattern.compile("(?:[fF]unc_\\d+_[a-zA-Z_]+|m_\\d+_|[fF]ield_\\d+_[a-zA-Z_]+|f_\\d+_|p_\\w+_\\d+_|p_\\d+_)");
    public abstract @InputFiles ConfigurableFileCollection getSources();
    public abstract @OutputFile RegularFileProperty getOutput();

    public abstract @Input @Optional Property<String> getArchiveClassifier();
    public abstract @Input Property<String> getArchiveExtension();
    protected abstract @Internal Property<Date> getArchiveDate();

    public abstract @InputFiles @Optional ConfigurableFileCollection getExcFiles();
    public abstract @InputFiles ConfigurableFileCollection getMap();

    public abstract @InputFile @Optional RegularFileProperty getRangeMap();
    public abstract @Input @Optional Property<Boolean> getKeepImports();
    public abstract @Input @Optional @Deprecated Property<Boolean> getAnnotate();

    public abstract @Input @Optional Property<Boolean> getSortImports();
    public abstract @Input @Optional Property<Boolean> getGuessLambdas();
    public abstract @Input @Optional Property<Boolean> getGuessLocals();

    @Inject
    public RenameSourcesApply(RenamerExtensionImpl renamer) {
    	var base = getProject().getExtensions().findByType(BasePluginExtension.class);
    	this.getArchiveExtension().convention("jar");
        this.getOutput().convention(base.getLibsDirectory().file(getProject().provider(() -> {
    		var buf = new StringBuilder();
    		buf.append(base.getArchivesName().getOrElse(getProject().getName()));
    		buf.append('-').append(getProject().getVersion());
    		if (this.getArchiveClassifier().isPresent())
    			buf.append('-').append(this.getArchiveClassifier().get());
    		buf.append('.').append(this.getArchiveExtension().get());
    		return buf.toString();
        })));
        this.getKeepImports().convention(true);
        this.getSortImports().convention(false);
        this.getGuessLambdas().convention(false);
        this.getGuessLocals().convention(false);
    }

    @Override
    protected ExecResult exec() throws IOException {
    	if (!this.getRangeMap().isPresent()) {
    		renameNaive();
    		return null;
    	}
        return super.exec().rethrowFailure().assertNormalExitValue();
    }

    @Override
    protected void addArguments() {
        this.args("--apply");

        this.args("--in", this.getSources());
        this.args("--out", this.getOutput());
        this.args("--exc", this.getExcFiles());
        this.args("--srg", this.getMap());

        this.args("--range", this.getRangeMap());
        this.args("--keepImports", this.getKeepImports());
        //this.args("--annotate", this.annotate);

        this.args("--sortImports", this.getSortImports());
        this.args("--guessLambdas", this.getGuessLambdas());
        this.args("--guessLocals", this.getGuessLocals());

        super.addArguments();
    }

    private void renameNaive() throws IOException {
    	var naiveSrgMap = new HashMap<String, String>();
    	for (var mapFile : this.getMap()) {
    		var map = IMappingFile.load(mapFile);
    		var srg = SRG_PATTERN.asPredicate();
	    	map.rename(new IRenamer() {
	    		@Override public String rename(IField value) { return capture(value); }
	    		@Override public String rename(IMethod value) { return capture(value); }
	    		@Override public String rename(IParameter value) { return capture(value); }
	    		private String capture(INode value) {
	    			if (srg.test(value.getOriginal()))
	    				naiveSrgMap.put(value.getOriginal(), value.getMapped());
	    			return value.getMapped();
	    		}
	    	});
    	}

    	var output = getOutput().getAsFile().get();
    	if (output.getParentFile() != null)
    		Files.createDirectories(output.getParentFile().toPath());

    	var seen = new HashSet<String>();
    	try (var zout = new ZipOutputStream(new FileOutputStream(output))) {
        	for (var file : this.getSources()) {
        		if (file.isDirectory()) {
        			var children = Files.walk(file.toPath()).filter(Files::isRegularFile).map(Path::toFile).toList();
        			var prefix = file.getAbsolutePath();
        			if (!prefix.endsWith(File.separator))
        				prefix += File.separatorChar;

        			for (var child : children) {
        				var relative = child.getAbsolutePath().substring(prefix.length());
        				if (!seen.add(relative))
        					continue;
        				if (relative.endsWith(".java")) {
        					var str = Files.readString(child.toPath(), StandardCharsets.UTF_8);
        					str = SRG_PATTERN.matcher(str).replaceAll(m -> naiveSrgMap.getOrDefault(m.group(), m.group()));
            				zout.putNextEntry(new ZipEntry(relative));
            				zout.write(str.getBytes(StandardCharsets.UTF_8));
            				zout.closeEntry();
        				}
        			}
        		} else if (file.getName().endsWith(".zip") || file.getName().endsWith(".jar")) {
        			try (var zin = new ZipInputStream(new FileInputStream(file))) {
        				ZipEntry entry = null;
        				while ((entry = zin.getNextEntry()) != null) {
        					if (!seen.add(entry.getName()))
        						continue;

            				if (entry.getName().endsWith(".java")) {
            					var str = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
            					str = SRG_PATTERN.matcher(str).replaceAll(m -> naiveSrgMap.getOrDefault(m.group(), m.group()));
                				zout.putNextEntry(new ZipEntry(entry.getName()));
                				zout.write(str.getBytes(StandardCharsets.UTF_8));
                				zout.closeEntry();
            				}
        				}
        			}
        		} else {
        			throw new IllegalArgumentException("Unknown input file, only directories and archives supported: " + file.getAbsolutePath());
        		}
        	}
    	}
    }

    public void mappings(String artifact) {
        this.mappings(getDependencyFactory().create(artifact));
    }

    public void mappings(Dependency dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
    }

    public void mappings(Provider<?> provider) {
    	this.getMap().setFrom(Util.toConfiguration(getProject(), provider));
    }

    public void mappings(TaskProvider<?> task) {
    	this.getMap().setFrom(Util.toFile(task));
    }

    public void setMappings(FileCollection files) {
        this.getMap().setFrom(files);
    }


    @Override
    @Deprecated
    public @Internal @Nullable String getClassifier() {
        return this.getArchiveClassifier().getOrNull();
    }

    @Override
    @Deprecated
    public @Internal String getExtension() {
        return this.getArchiveExtension().get();
    }

    @Override
    @Deprecated
    public @Internal String getType() {
        return this.getExtension();
    }

    @Override
    @Deprecated
    public @Internal File getFile() {
        return this.getOutput().getAsFile().get();
    }

    @Override
    @Deprecated
    public @Internal Date getDate() {
        return this.getArchiveDate().getOrNull();
    }

    @Override
    public @Internal TaskDependency getBuildDependencies() {
        return task -> Set.of(this);
    }
}
