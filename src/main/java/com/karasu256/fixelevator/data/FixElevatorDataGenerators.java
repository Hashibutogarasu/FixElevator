package com.karasu256.fixelevator.data;

import com.karasu256.fixelevator.FixOpenBlocksElevatorSimulated;

import java.nio.file.Path;
import java.util.List;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.structures.SnbtToNbt;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Registers data generation providers used to compile the mod's GameTest
 * structure fixtures from SNBT into NBT.
 */
@EventBusSubscriber(modid = FixOpenBlocksElevatorSimulated.MODID)
public final class FixElevatorDataGenerators {
    private FixElevatorDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        Path structureFolder = event.getModContainer().getModInfo().getOwningFile()
                .getFile().findResource("data", FixOpenBlocksElevatorSimulated.MODID, "structure");
        Path resourceRoot = structureFolder.getParent().getParent().getParent();
        DataProvider.Factory<SnbtToNbt> factory = output -> new SnbtToNbt(output, List.of(resourceRoot));
        generator.addProvider(event.includeServer(), factory);
    }
}
