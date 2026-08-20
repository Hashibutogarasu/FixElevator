package com.karasu256.fixelevator.mixin.client;

import com.karasu256.fixelevator.compat.SubLevelBlockAccess;
import com.vsngarcia.Config;
import com.vsngarcia.ElevatorHandler;
import com.vsngarcia.network.ClientPacketSender;
import com.vsngarcia.network.TeleportPacket;

import com.llamalad7.mixinextras.sugar.Local;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reprojects the local player into a Create: Simulated / Aeronautics
 * sub-level's local coordinates while {@code tryTeleport} scans for
 * elevator blocks, so the scan resolves elevator blocks that are actually
 * stored in a sub-level's plot rather than at the player's global position.
 */
@Mixin(ElevatorHandler.class)
public abstract class ElevatorHandlerMixin {
    @Unique
    private static final ThreadLocal<SubLevel> fixelevator$activeSubLevel = new ThreadLocal<>();

    @Inject(method = "tryTeleport", at = @At("HEAD"))
    private static void fixelevator$pushSubLevel(final LocalPlayer player, final Direction facing,
            final ClientPacketSender sender, final CallbackInfo ci) {
        if (fixelevator$findsElevatorNearby(player, player.level())) {
            return;
        }

        final SubLevel subLevel = SubLevelBlockAccess.findSubLevelWhere(player,
                level -> fixelevator$findsElevatorNearby(player, level));
        if (subLevel != null) {
            SubLevelHelper.pushEntityLocal(subLevel, player);
            fixelevator$activeSubLevel.set(subLevel);
        }
    }

    @Redirect(
            method = "tryTeleport",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lcom/vsngarcia/network/TeleportPacket;"
            )
    )
    private static TeleportPacket fixelevator$packetWithGlobalPositions(final BlockPos fromPos, final BlockPos toPos,
            @Local(argsOnly = true) final LocalPlayer player) {
        final SubLevel subLevel = fixelevator$activeSubLevel.get();
        if (subLevel == null) {
            return new TeleportPacket(fromPos, toPos);
        }

        final Vec3 globalFrom = SubLevelBlockAccess.localToGlobal(subLevel, player, Vec3.atBottomCenterOf(fromPos));
        final Vec3 globalTo = SubLevelBlockAccess.localToGlobal(subLevel, player, Vec3.atBottomCenterOf(toPos));
        return new TeleportPacket(BlockPos.containing(globalFrom), BlockPos.containing(globalTo));
    }

    @Inject(method = "tryTeleport", at = @At("RETURN"))
    private static void fixelevator$popSubLevel(final LocalPlayer player, final Direction facing,
            final ClientPacketSender sender, final CallbackInfo ci) {
        final SubLevel subLevel = fixelevator$activeSubLevel.get();
        if (subLevel != null) {
            SubLevelHelper.popEntityLocal(subLevel, player);
            fixelevator$activeSubLevel.remove();
        }
    }

    @Unique
    private static boolean fixelevator$findsElevatorNearby(final LocalPlayer player, final Level level) {
        BlockPos pos = player.blockPosition();
        for (int i = 0; i < Config.GENERAL.activationRange.getAsInt(); i++) {
            if (SubLevelBlockAccess.findNearbyBlock(level, pos,
                    state -> TeleportPacket.getElevator(state) != null) != null) {
                return true;
            }
            pos = pos.below();
        }
        return false;
    }
}
