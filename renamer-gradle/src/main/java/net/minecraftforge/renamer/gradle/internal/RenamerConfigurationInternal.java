package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.renamer.gradle.RenamerConfiguration;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.jspecify.annotations.Nullable;

interface RenamerConfigurationInternal extends RenamerConfiguration {
    @Nullable TaskProvider<? extends AbstractArchiveTask> getInput();

    @Nullable FileCollection getClasspath();

    @Nullable Action<? super Jar> getAction();
}
