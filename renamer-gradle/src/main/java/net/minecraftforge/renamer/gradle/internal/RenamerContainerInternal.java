package net.minecraftforge.renamer.gradle.internal;

import net.minecraftforge.renamer.gradle.RenamerContainer;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

interface RenamerContainerInternal extends RenamerContainer, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(RenamerContainer.class);
    }

    default String getApiElementsConfigurationName() {
        return this.getName() + "ApiElements";
    }

    default String getRuntimeElementsConfigurationName() {
        return this.getName() + "RuntimeElements";
    }
}
