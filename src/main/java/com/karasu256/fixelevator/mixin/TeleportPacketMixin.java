package com.karasu256.fixelevator.mixin;

import com.karasu256.fixelevator.compat.SubLevelBlockAccess;
import com.vsngarcia.network.TeleportPacket;

import com.llamalad7.mixinextras.sugar.Local;

import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * Redirects the block lookups {@code TeleportPacket} performs against a
 * player's global (visual) position so they also resolve correctly when the
 * elevator column is stored inside a Create: Simulated / Aeronautics
 * sub-level's local plot rather than at that same coordinate in the real
 * {@link Level}.
 */
@Mixin(TeleportPacket.class)
public abstract class TeleportPacketMixin {
    @Redirect(
            method = "isBadTeleportPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private static BlockState fixelevator$getBlockStateForValidation(final Level level, final BlockPos pos,
            @Local(argsOnly = true) final TeleportPacket message, @Local(argsOnly = true) final Player player) {
        return fixelevator$resolveState(level, pos, message, player);
    }

    @Redirect(
            method = "isBadTeleportPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/vsngarcia/network/TeleportPacket;isValidPos(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private static boolean fixelevator$isValidPosForSubLevel(final BlockGetter world, final BlockPos pos,
            @Local(argsOnly = true) final TeleportPacket message, @Local(argsOnly = true) final Player player) {
        if (!(world instanceof final Level level)) {
            return TeleportPacket.isValidPos(world, pos);
        }

        final BlockPos resolvedPos = fixelevator$resolvePos(level, pos, message, player);
        return TeleportPacket.isValidPos(level, resolvedPos);
    }

    @Redirect(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private static BlockState fixelevator$getBlockStateForHandle(final ServerLevel level, final BlockPos pos,
            @Local(argsOnly = true) final TeleportPacket message, @Local(argsOnly = true) final ServerPlayer player) {
        return fixelevator$resolveState(level, pos, message, player);
    }

    @Redirect(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z"
            )
    )
    private static boolean fixelevator$teleportViaSubLevel(final ServerPlayer player, final ServerLevel level,
            final double x, final double y, final double z, final Set<RelativeMovement> relatives,
            final float yaw, final float pitch, @Local(argsOnly = true) final TeleportPacket message) {
        if (TeleportPacket.getElevator(level.getBlockState(message.to())) != null) {
            return player.teleportTo(level, x, y, z, relatives, yaw, pitch);
        }

        final SubLevel subLevel = fixelevator$findSubLevel(player, message);
        if (subLevel == null) {
            return player.teleportTo(level, x, y, z, relatives, yaw, pitch);
        }

        final BlockPos resolvedLocalPos = fixelevator$resolvePos(level, message.to(), message, player);
        final double relativeYOffset = y - message.to().getY();
        final Vec3 resolvedLocalCenter = Vec3.atCenterOf(resolvedLocalPos);
        final Vec3 localTarget = new Vec3(resolvedLocalCenter.x(), resolvedLocalPos.getY() + relativeYOffset, resolvedLocalCenter.z());
        final Vec3 globalTarget = SubLevelBlockAccess.localToGlobal(subLevel, player, localTarget);

        return player.teleportTo(level, globalTarget.x(), globalTarget.y(), globalTarget.z(), relatives, yaw, pitch);
    }

    @Unique
    private static BlockState fixelevator$resolveState(final Level level, final BlockPos pos,
            final TeleportPacket message, final Player player) {
        final BlockState direct = level.getBlockState(pos);
        if (TeleportPacket.getElevator(direct) != null || !(player instanceof final ServerPlayer serverPlayer)) {
            return direct;
        }

        final BlockPos resolved = fixelevator$resolvePos(level, pos, message, player);
        return level.getBlockState(resolved);
    }

    @Unique
    private static BlockPos fixelevator$resolvePos(final Level level, final BlockPos pos,
            final TeleportPacket message, final Player player) {
        if (TeleportPacket.getElevator(level.getBlockState(pos)) != null || !(player instanceof final ServerPlayer serverPlayer)) {
            return pos;
        }

        final SubLevel subLevel = fixelevator$findSubLevel(serverPlayer, message);
        if (subLevel == null) {
            return pos;
        }

        final Vec3 localPos = SubLevelBlockAccess.globalToLocal(subLevel, serverPlayer, Vec3.atCenterOf(pos));
        final BlockPos nearbyElevator = SubLevelBlockAccess.findNearbyBlock(level, BlockPos.containing(localPos),
                state -> TeleportPacket.getElevator(state) != null);
        return nearbyElevator != null ? nearbyElevator : BlockPos.containing(localPos);
    }

    @Unique
    private static SubLevel fixelevator$findSubLevel(final ServerPlayer player, final TeleportPacket message) {
        return SubLevelBlockAccess.findSubLevelWhere(player, level ->
                SubLevelBlockAccess.findNearbyBlock(level, player.blockPosition(),
                        state -> TeleportPacket.getElevator(state) != null) != null);
    }

}
