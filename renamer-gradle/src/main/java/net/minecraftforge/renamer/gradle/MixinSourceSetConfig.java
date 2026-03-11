/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public interface MixinSourceSetConfig {
    /**
     * {@inheritDoc}
     * The reference mapping for this source set.
     * Will inform Mixin of which symbols are mapped to which names at runtime
     */
	Property<String> getRefMap();

    /**
     * {@inheritDoc}
     * Stops the Mixin annotation processor from throwing an error when it finds an invalid Mixin target
     */
	Property<Boolean> getDisableTargetValidator();

    /**
     * {@inheritDoc}
     * Prevents the annotation processor from creating a small {@code .csv} file with data pertaining to annotation processing.
     * Keeping this on will make the AP run slightly faster, but may be worth turning off if you are getting AP file access errors
     */
	Property<Boolean> getDisableTargetExport();

    /**
     * {@inheritDoc}
     * By default, when a Mixin target is annotated with {@code @Overwrite}, it is enforced that a Javadoc block above it
     * contains at least an {@code @author} and {@code @reason} tag. Turning this off will prevent Mixin from complaining
     * (ignore, warn or error, see {@link #getOverwriteErrorLevel()}) in such a case where these tags are missing
     */
	Property<Boolean> getDisableOverwriteChecker();

    /**
     * {@inheritDoc}
     * The level of complaining the Mixin annotation processor will do when it finds an {@code @Overwrite} annotated target
     * lacking the {@code @author} and {@code @reason} tags. Can be any of the following:
     * <ul>
     *     <li>{@code ignore}: Do nothing</li>
     *     <li>{@code warning}: Write a warning message to the console</li>
     *     <li>{@code error}: Throw an error</li>
     * </ul>
     */
	Property<String> getOverwriteErrorLevel();

    /**
     * {@inheritDoc}
     * The current workspace mapping type, used so that Mixin can translate your obfuscated names for creating the refmap
     */
	Property<String> getDefaultObfuscationEnv();

    /**
     * {@inheritDoc}
     * The other half of {@link #getDefaultObfuscationEnv()}, the output mapping type to translate to for creating entries in the refmap
     */
    ListProperty<String> getMappingTypes();

    /**
     * {@inheritDoc}
     * Extra data passed to the compiler as key-value pairs. These extra data can then be used in annotation parameters.
     * <p>
     * Example :{@code @Inject(method = "foo" at = @At("HEAD"), constraints="myToken(myValue)"}
     */
	MapProperty<String, String> getTokens();

    /**
     * {@inheritDoc}
     * Extra mapping files to use for creating refmaps, usually for obfuscated external dependencies
     */
	ConfigurableFileCollection getExtraMappings();

    /**
     * {@inheritDoc}
     * "Quietens" the console by suppressing trivial and often unneeded messages
     */
	Property<Boolean> getQuiet();

    /**
     * {@inheritDoc}
     * Prefix mixin logs with the type of message, such as {@code [MIXIN_0100]} which refers to a missing {@code @author} tag
     */
	Property<Boolean> getShowMessageTypes();

    /**
     * {@inheritDoc}
     * Which message types to hide, warn about or simply display. These can be seen by enabling {@link #getShowMessageTypes()}
     * <p>
     * Example: {@code "MIXIN_0100": "ignore"}
     */
	MapProperty<String, String> getMessages();
}