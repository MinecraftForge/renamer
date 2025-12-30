package net.minecraftforge.renamer.gradle.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

abstract class RenameJar extends Jar implements RenamerTask {
    protected abstract @InputFiles ConfigurableFileCollection getMap();

    protected abstract @InputFiles @Optional @Classpath ConfigurableFileCollection getLibraries();

    //region JavaExec
    public abstract @InputFiles @Classpath ConfigurableFileCollection getClasspath();

    public abstract @Input @Optional Property<String> getMainClass();

    public abstract @Nested Property<JavaLauncher> getJavaLauncher();

    protected abstract @Nested Property<JavaLauncher> getToolchainLauncher();

    public abstract @Input @Optional Property<Boolean> getPreferToolchainJvm();

    public abstract @Internal DirectoryProperty getWorkingDir();

    protected abstract @Internal MapProperty<String, String> getForkProperties();
    //endregion

    //region Logging
    @Deprecated
    @Override public LoggingManager getLogging() {
        return super.getLogging();
    }

    protected abstract @Console Property<LogLevel> getStandardOutputLogLevel();

    protected abstract @Console Property<LogLevel> getStandardErrorLogLevel();

    protected abstract @Internal RegularFileProperty getLogFile();
    //endregion

    private final RenamerProblems problems = getObjectFactory().newInstance(RenamerProblems.class);

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject ExecOperations getExecOperations();

    protected abstract @Inject JavaToolchainService getJavaToolchains();

    @Inject
    public RenameJar() {
        var resolved = this.getTool(Tools.FART);

        this.getClasspath().setFrom(resolved.getClasspath());

        if (resolved.hasMainClass())
            this.getMainClass().set(resolved.getMainClass());
        this.getJavaLauncher().set(resolved.getJavaLauncher());

        this.getToolchainLauncher().convention(getJavaToolchains().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.current())));
        getProject().getPluginManager().withPlugin("java", javaAppliedPlugin ->
            this.getToolchainLauncher().set(getJavaToolchains().launcherFor(getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain()))
        );

        this.getForkProperties().set(Util.getForkProperties(getProviders()));

        this.getStandardOutputLogLevel().convention(LogLevel.LIFECYCLE);
        this.getStandardErrorLogLevel().convention(LogLevel.ERROR);

        this.getWorkingDir().convention(this.getDefaultOutputDirectory());
        this.getLogFile().convention(this.getDefaultLogFile());
    }

    @Override
    protected void copy() {
        super.copy();
        this.exec();
    }

    private List<String> getArguments() {
        var args = new ArrayList<>(List.of(
            "--input", this.getArchiveFile().get().getAsFile().toString(),
            "--map", this.getMapFile().toString()
        ));

        for (var library : this.getLibraries()) {
            args.add("--lib");
            args.add(library.toString());
        }

        return args;
    }

    // TODO [GradleUtils] Find a way to make a ToolExec interface that can be implemented by non ToolExecBase tasks
    private void exec() {
        var logger = getLogger();

        var args = getArguments();
        var systemProperties = new HashMap<String, String>();
        for (var property : this.getForkProperties().get().entrySet()) {
            systemProperties.putIfAbsent(property.getKey(), property.getValue());
        }

        var stdOutLevel = this.getStandardOutputLogLevel().get();
        var stdErrLevel = this.getStandardErrorLogLevel().get();

        JavaLauncher javaLauncher;
        if (getPreferToolchainJvm().getOrElse(false)) {
            var candidateLauncher = getJavaLauncher().get();
            var toolchainLauncher = getToolchainLauncher().get();
            javaLauncher = toolchainLauncher.getMetadata().getLanguageVersion().canCompileOrRun(candidateLauncher.getMetadata().getLanguageVersion())
                ? toolchainLauncher
                : candidateLauncher;
        } else {
            javaLauncher = getJavaLauncher().get();
        }

        var workingDirectory = this.getWorkingDir().map(problems.ensureFileLocation()).get().getAsFile();

        try (var log = new PrintWriter(new FileWriter(this.getLogFile().getAsFile().get()), true)) {
            getExecOperations().javaexec(spec -> {
                spec.setIgnoreExitValue(true);

                spec.setWorkingDir(workingDirectory);
                spec.setClasspath(this.getClasspath());
                spec.getMainClass().set(this.getMainClass());
                spec.setExecutable(javaLauncher.getExecutablePath().getAsFile().getAbsolutePath());
                spec.setArgs(args);
                spec.setSystemProperties(systemProperties);

                spec.setStandardOutput(Util.toLog(
                    line -> {
                        logger.log(stdOutLevel, line);
                        log.println(line);
                    }
                ));
                spec.setErrorOutput(Util.toLog(
                    line -> {
                        logger.log(stdErrLevel, line);
                        log.println(line);
                    }
                ));

                log.print("Java Launcher: ");
                log.println(spec.getExecutable());
                log.print("Working directory: ");
                log.println(spec.getWorkingDir().getAbsolutePath());
                log.print("Main class: ");
                log.println(spec.getMainClass().getOrElse("AUTOMATIC"));
                log.println("Arguments:");
                for (var s : spec.getArgs()) {
                    log.print("  ");
                    log.println(s);
                }
                log.println("JVM Arguments:");
                for (var s : spec.getAllJvmArgs()) {
                    log.print("  ");
                    log.println(s);
                }
                log.println("Classpath:");
                for (var f : getClasspath()) {
                    log.print("  ");
                    log.println(f.getAbsolutePath());
                }
                log.println("====================================");
            }).rethrowFailure().assertNormalExitValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File getMapFile() {
        try {
            return this.getMap().getSingleFile();
        } catch (IllegalStateException exception) {
            throw problems.reportMultipleMapFiles(exception, this);
        }
    }
}
