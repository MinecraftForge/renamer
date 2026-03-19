package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("test")
public final class ExampleMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod(FMLJavaModLoadingContext context) {
        LOGGER.info("If this shows up we can reference obfuscated code: " + Blocks.DIRT.getDescriptionId());
    }
}
