package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.renamer.gradle.RenamerConfiguration;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;

abstract class RenamerContainerImpl implements RenamerContainerInternal {
    private final String name;

    // Renamer inputs
    private final ConfigurableFileCollection mappings = getObjects().fileCollection();

    // Renamer outputs
    private final AdhocComponentWithVariants softwareComponent;
    private final TaskProvider<RenameJar> renamedJar;
    private TaskProvider<? extends AbstractArchiveTask> renamedJarInput;
    private FileCollection renamedJarLibraries;

    protected abstract @Inject Project getProject();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject DependencyFactory getDependencies();

    protected abstract @Inject SoftwareComponentFactory getSoftwareComponents();

    @Inject
    public RenamerContainerImpl(SourceSet sourceSet, String name) {
        var project = getProject();
        var tasks = project.getTasks();
        var configurations = project.getConfigurations();

        this.name = name.isEmpty() ? sourceSet.getTaskName("renamed", "") : name;
        this.renamedJarInput = tasks.named(sourceSet.getJarTaskName(), Jar.class);
        this.renamedJarLibraries = sourceSet.getCompileClasspath();

        project.getComponents().add(this.softwareComponent = getSoftwareComponents().adhoc(this.name + "java"));
        this.renamedJar = tasks.register(this.name + "Jar", RenameJar.class, task -> {
            task.dependsOn(this.mappings);
            task.getMap().setFrom(this.mappings);

            var jar = tasks.named(sourceSet.getJarTaskName(), Jar.class);
            task.setManifest(jar.get().getManifest());
        });

        var runtimeClasspath = configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);

        // TODO [Renamer] Add to Java component with special net.minecraftforge.obfuscation variant
        var apiElements = configurations.named(sourceSet.getApiElementsConfigurationName());
        var renamedApiElements = configurations.consumable(this.getApiElementsConfigurationName(), configuration -> {
            configuration.getAttributes().addAllLater(apiElements.get().getAttributes());
            configuration.getOutgoing().artifact(this.renamedJar);
            configuration.setExtendsFrom(apiElements.get().getExtendsFrom());

            this.softwareComponent.addVariantsFromConfiguration(configuration, variant -> variant.mapToMavenScope("compile"));
        });

        var runtimeElements = configurations.named(sourceSet.getRuntimeElementsConfigurationName());
        var renamedRuntimeElements = configurations.consumable(this.getRuntimeElementsConfigurationName(), configuration -> {
            configuration.getAttributes().addAllLater(runtimeElements.get().getAttributes());
            configuration.getOutgoing().artifact(this.renamedJar);
            configuration.setExtendsFrom(runtimeElements.get().getExtendsFrom());

            this.softwareComponent.addVariantsFromConfiguration(configuration, variant -> variant.mapToMavenScope("runtime"));
        });

        getProject().afterEvaluate(this::finish);
    }

    private void finish(Project project) {
        this.renamedJar.configure(task -> {
            var input = this.renamedJarInput;
            var libraries = this.renamedJarLibraries;

            task.dependsOn(input.map(Task::getDependsOn), libraries);
            task.with(input.get());
            task.getLibraries().setFrom(libraries);
        });
    }

    @Override
    public String getName() {
        return this.name;
    }

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
    public void mappings(Provider<? extends Dependency> dependency) {
        var configuration = getProject().getConfigurations().detachedConfiguration();
        configuration.getDependencies().addLater(dependency);
        configuration.setTransitive(false);

        this.setMappings(configuration);
    }

    @Override
    public void setMappings(FileCollection files) {
        this.mappings.setFrom(files);
    }

    public void classes(Action<? super RenamerConfiguration> action) {
        var configuration = getObjects().newInstance(RenamerConfigurationImpl.class);
        action.execute(configuration);

        if (configuration.getInput() != null) {
            this.renamedJarInput = configuration.getInput();
            this.renamedJar.configure(task -> {
                if (configuration.getInput().get() instanceof org.gradle.jvm.tasks.Jar jar)
                    task.setManifest(jar.getManifest());
            });
        }

        if (configuration.getClasspath() != null)
            this.renamedJarLibraries = configuration.getClasspath();

        if (configuration.getAction() != null)
            this.renamedJar.configure(configuration.getAction());
    }
}
