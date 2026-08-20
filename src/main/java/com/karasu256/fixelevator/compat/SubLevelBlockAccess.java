package com.karasu256.fixelevator.compat;

import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Locates the Create: Simulated / Aeronautics sub-level an entity is
 * standing on and temporarily reprojects the entity's reported position
 * into that sub-level's local storage coordinates, so vanilla
 * {@link Level#getBlockState} lookups made relative to the entity resolve
 * the block that is actually there.
 *
 * <p>The Sable API type {@link SubLevel} implements an interface shipped in
 * a separate companion jar that FixElevator does not depend on, so this
 * class never calls an instance method on a {@code SubLevel} reference; it
 * only ever passes it opaquely to {@link SubLevelHelper}'s static methods.
 */
public final class SubLevelBlockAccess {
    private SubLevelBlockAccess() {
    }

    /**
     * The maximum angle, in degrees, between a sub-level's local up axis and
     * the world's up axis for the sub-level to still be treated as an
     * elevator shaft. Beyond this tilt, "up"/"down" no longer correspond to
     * meaningful floor changes, so the fix must not engage and the elevator
     * should fall back to its original (non-functional) behaviour rather
     * than teleport the player through what is now a mostly horizontal gap.
     */
    private static final double MAX_SHAFT_TILT_DEGREES = 45.0D;

    /**
     * A sub-level's rigid body can rest at a small residual tilt relative to
     * the world axes, so a single voxel sampled from a translated position
     * can land a few cells short of the block it was aimed at. This is the
     * search radius {@link #findNearbyBlock} uses by default to absorb that.
     */
    public static final int DEFAULT_SEARCH_RADIUS = 2;

    public static SubLevel findSubLevelWhere(final Entity entity, final Predicate<Level> probe) {
        for (final SubLevel candidate : SubLevelContainer.getContainer(entity.level()).getAllSubLevels()) {
            if (!isUpright(candidate, entity)) {
                continue;
            }

            SubLevelHelper.pushEntityLocal(candidate, entity);
            try {
                if (probe.test(entity.level())) {
                    return candidate;
                }
            } finally {
                SubLevelHelper.popEntityLocal(candidate, entity);
            }
        }
        return null;
    }

    /**
     * Whether {@code subLevel}'s local up axis, once transformed into world
     * space, still points close enough to world up to be usable as an
     * elevator shaft. See {@link #MAX_SHAFT_TILT_DEGREES}.
     */
    public static boolean isUpright(final SubLevel subLevel, final Entity entity) {
        final Vec3 base = localToGlobal(subLevel, entity, Vec3.ZERO);
        final Vec3 up = localToGlobal(subLevel, entity, new Vec3(0.0D, 1.0D, 0.0D));
        final Vec3 axis = up.subtract(base);
        final double length = axis.length();
        if (length < 1.0e-6D) {
            return false;
        }

        final double cosineToWorldUp = axis.y() / length;
        return cosineToWorldUp >= Math.cos(Math.toRadians(MAX_SHAFT_TILT_DEGREES));
    }

    public static <T> T runInLocalSpace(final SubLevel subLevel, final Entity entity, final Supplier<T> action) {
        SubLevelHelper.pushEntityLocal(subLevel, entity);
        try {
            return action.get();
        } finally {
            SubLevelHelper.popEntityLocal(subLevel, entity);
        }
    }

    /**
     * Moves {@code entity} to {@code pos} for the duration of an internal,
     * always-restored calculation, without going through
     * {@link Entity#setPos(double, double, double)}. That vanilla setter is
     * meant for actual, persisting entity movement, and third-party safety
     * nets (e.g. mods guarding against runaway teleports) may legitimately
     * reject a jump to a sub-level's raw plot coordinates (millions of
     * blocks away) even though this call restores the entity's real
     * position within the same method before anything else ever observes
     * the change. Using Sable's own raw position field setter here bypasses
     * no safety net that matters: nothing external ever sees the entity
     * sitting at {@code pos}.
     */
    private static void setPosRaw(final Entity entity, final Vec3 pos) {
        ((EntityExtension) entity).sable$setPosSuperRaw(pos);
    }

    public static Vec3 localToGlobal(final SubLevel subLevel, final Entity entity, final Vec3 localPos) {
        final Vec3 originalGlobal = entity.position();

        try {
            SubLevelHelper.pushEntityLocal(subLevel, entity);
            setPosRaw(entity, localPos);
            SubLevelHelper.popEntityLocal(subLevel, entity);
            return entity.position();
        } finally {
            setPosRaw(entity, originalGlobal);
        }
    }

    public static Vec3 globalToLocal(final SubLevel subLevel, final Entity entity, final Vec3 globalPos) {
        final Vec3 originalGlobal = entity.position();

        try {
            setPosRaw(entity, globalPos);
            SubLevelHelper.pushEntityLocal(subLevel, entity);
            final Vec3 result = entity.position();
            SubLevelHelper.popEntityLocal(subLevel, entity);
            return result;
        } finally {
            setPosRaw(entity, originalGlobal);
        }
    }

    /**
     * Equivalent to {@link #findNearbyBlock(Level, BlockPos, int, Predicate)}
     * using {@link #DEFAULT_SEARCH_RADIUS}.
     */
    public static BlockPos findNearbyBlock(final Level level, final BlockPos center, final Predicate<BlockState> matches) {
        return findNearbyBlock(level, center, DEFAULT_SEARCH_RADIUS, matches);
    }

    /**
     * Searches the neighbourhood of {@code center} for a block matching
     * {@code matches}, closest cells first, to absorb the residual tilt
     * described at {@link #DEFAULT_SEARCH_RADIUS}.
     */
    public static BlockPos findNearbyBlock(final Level level, final BlockPos center, final int radius,
            final Predicate<BlockState> matches) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) {
                            continue;
                        }
                        final BlockPos candidate = center.offset(dx, dy, dz);
                        if (matches.test(level.getBlockState(candidate))) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }
}
