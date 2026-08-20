package com.karasu256.fixelevator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry point of the FixElevator compatibility mod.
 */
@Mod(FixOpenBlocksElevatorSimulated.MODID)
public class FixOpenBlocksElevatorSimulated {
    public static final String MODID = "fixelevator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FixOpenBlocksElevatorSimulated(IEventBus modEventBus, ModContainer modContainer) {
    }
}
