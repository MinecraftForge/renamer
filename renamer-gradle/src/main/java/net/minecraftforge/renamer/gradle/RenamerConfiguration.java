package net.minecraftforge.renamer.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

public interface RenamerConfiguration {
    void setInput(TaskProvider<? extends AbstractArchiveTask> input);

    void setInput(AbstractArchiveTask task);

    void setClasspath(FileCollection classpath);

    void archive(Action<? super Jar> action);
}
