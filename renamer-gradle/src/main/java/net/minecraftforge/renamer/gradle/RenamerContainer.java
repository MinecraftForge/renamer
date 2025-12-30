package net.minecraftforge.renamer.gradle;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public interface RenamerContainer extends Named {
    default void mappings(String channel, String version) {
        mappings("net.minecraft:mappings_" + channel + ':' + version + "@tsrg.gz");
    }

    void mappings(String artifact);

    void mappings(Dependency dependency);

    void mappings(Provider<? extends Dependency> dependency);

    default void mappings(ProviderConvertible<? extends Dependency> dependency) {
        this.mappings(dependency.asProvider());
    }

    void setMappings(FileCollection files);

    void classes(Action<? super RenamerConfiguration> action);

    default void classes(AbstractArchiveTask input) {
        this.classes(it -> it.setInput(input));
    }

    default void classes(AbstractArchiveTask input, Action<? super RenamerConfiguration> action) {
        this.classes(it -> {
            action.execute(it);
            it.setInput(input);
        });
    }

    default void classes(TaskProvider<? extends AbstractArchiveTask> input) {
        this.classes(it -> it.setInput(input));
    }

    default void classes(TaskProvider<? extends AbstractArchiveTask> input, Action<? super RenamerConfiguration> action) {
        this.classes(it -> {
            action.execute(it);
            it.setInput(input);
        });
    }
}
