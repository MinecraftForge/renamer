package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.jspecify.annotations.Nullable;

final class Util extends SharedUtil {
    static final Spec<? super String> STRING_IS_PRESENT = s -> !s.isBlank();

    static @Nullable SourceSet findSourceSetFromJar(Project project, String jarTaskName) {
        var candidates = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().matching(sourceSet -> sourceSet.getJarTaskName().equals(jarTaskName)).iterator();
        return candidates.hasNext() ? candidates.next() : null;
    }
}
