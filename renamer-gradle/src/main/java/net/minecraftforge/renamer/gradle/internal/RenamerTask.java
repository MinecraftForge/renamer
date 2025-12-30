package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import net.minecraftforge.gradleutils.shared.EnhancedTask;
import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@SuppressWarnings("ClassEscapesDefinedScope")
public interface RenamerTask extends EnhancedTask<RenamerProblems> {
    @Override
    default Class<? extends EnhancedPlugin<? super Project>> pluginType() {
        return RenamerPlugin.class;
    }

    @Override
    default Class<RenamerProblems> problemsType() {
        return RenamerProblems.class;
    }
}
