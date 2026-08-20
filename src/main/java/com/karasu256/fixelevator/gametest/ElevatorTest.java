package com.karasu256.fixelevator.gametest;

import com.karasu256.fixelevator.FixOpenBlocksElevatorSimulated;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.vsngarcia.ElevatorBlockBase;
import com.vsngarcia.neoforge.init.Registry;
import com.vsngarcia.network.TeleportPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reproduces and verifies the fix for OpenBlocks Elevator failing to resolve
 * its "to" elevator block when the elevator column is part of a Create:
 * Simulated sub-level rather than the overworld level directly.
 *
 * <p>Both tests load {@code data/fixelevator/structure/elevator.nbt}, an
 * actual in-game structure export (oak-log shaft, elevator pair on the
 * central axis, wall-mounted physics assembler still switched off) rather
 * than a fixture built block-by-block from the test code.
 */
@GameTestHolder(FixOpenBlocksElevatorSimulated.MODID)
public class ElevatorTest {
    private static final BlockPos BOTTOM_ELEVATOR = new BlockPos(1, 1, 1);
    private static final BlockPos TOP_ELEVATOR = new BlockPos(1, 5, 1);
    private static final BlockPos ASSEMBLER = new BlockPos(1, 1, 3);

    private static final BlockPos[] STRUCTURE_BLOCKS = {
            new BlockPos(0, 1, 0), new BlockPos(1, 1, 0), new BlockPos(2, 1, 0),
            new BlockPos(0, 1, 1), BOTTOM_ELEVATOR, new BlockPos(2, 1, 1),
            new BlockPos(0, 1, 2), new BlockPos(1, 1, 2), new BlockPos(2, 1, 2),
            new BlockPos(0, 2, 0), new BlockPos(2, 2, 0), new BlockPos(0, 2, 2), new BlockPos(2, 2, 2),
            new BlockPos(0, 3, 0), new BlockPos(2, 3, 0), new BlockPos(0, 3, 2), new BlockPos(2, 3, 2),
            new BlockPos(0, 4, 0), new BlockPos(2, 4, 0), new BlockPos(0, 4, 2), new BlockPos(2, 4, 2),
            new BlockPos(0, 5, 0), new BlockPos(1, 5, 0), new BlockPos(2, 5, 0),
            new BlockPos(0, 5, 1), TOP_ELEVATOR, new BlockPos(2, 5, 1),
            new BlockPos(0, 5, 2), new BlockPos(1, 5, 2), new BlockPos(2, 5, 2)
    };

    /**
     * {@code elevator2}: the same shaft as {@code elevator}, but with only a
     * single block of headroom between the two elevator blocks instead of
     * three, reproducing a tight-gap column.
     */
    private static final BlockPos BOTTOM_ELEVATOR_2 = new BlockPos(1, 1, 1);
    private static final BlockPos TOP_ELEVATOR_2 = new BlockPos(1, 3, 1);
    private static final BlockPos ASSEMBLER_2 = new BlockPos(1, 1, 3);

    private static final BlockPos[] STRUCTURE_BLOCKS_2 = {
            new BlockPos(0, 1, 0), new BlockPos(1, 1, 0), new BlockPos(2, 1, 0),
            new BlockPos(0, 1, 1), BOTTOM_ELEVATOR_2, new BlockPos(2, 1, 1),
            new BlockPos(0, 1, 2), new BlockPos(1, 1, 2), new BlockPos(2, 1, 2),
            new BlockPos(0, 2, 0), new BlockPos(2, 2, 0), new BlockPos(0, 2, 2), new BlockPos(2, 2, 2),
            new BlockPos(0, 3, 0), new BlockPos(1, 3, 0), new BlockPos(2, 3, 0),
            new BlockPos(0, 3, 1), TOP_ELEVATOR_2, new BlockPos(2, 3, 1),
            new BlockPos(0, 3, 2), new BlockPos(1, 3, 2), new BlockPos(2, 3, 2)
    };

    @GameTest(template = "elevator")
    public static void elevatorWorksInOverworld(final GameTestHelper helper) {
        BlockPos absoluteTo = helper.absolutePos(TOP_ELEVATOR);
        BlockState toState = helper.getLevel().getBlockState(absoluteTo);
        if (!(toState.getBlock() instanceof ElevatorBlockBase)) {
            throw new GameTestAssertException("Expected an elevator block at " + absoluteTo + " but found " + toState);
        }

        helper.succeed();
    }

    @GameTest(template = "elevator", timeoutTicks = 400)
    public static void elevatorWorksInSubLevel(final GameTestHelper helper) {
        glueStructure(helper);
        triggerAssembler(helper);

        AtomicReference<DeployerFakePlayer> playerHolder = new AtomicReference<>();
        GameTestSequence sequence = helper.startSequence();
        sequence.thenWaitUntil(() -> {
            if (!helper.getLevel().getBlockState(helper.absolutePos(TOP_ELEVATOR)).isAir()
                    || !helper.getLevel().getBlockState(helper.absolutePos(BOTTOM_ELEVATOR)).isAir()) {
                throw new GameTestAssertException("Expected the elevator column to have been assembled out of the overworld level");
            }
        });
        sequence.thenExecute(() -> {
            DeployerFakePlayer player = new DeployerFakePlayer(helper.getLevel(), DeployerFakePlayer.fallbackID);
            BlockPos absoluteFrom = helper.absolutePos(BOTTOM_ELEVATOR);
            player.setPos(absoluteFrom.getX() + 0.5D, absoluteFrom.getY(), absoluteFrom.getZ() + 0.5D);
            helper.getLevel().addFreshEntity(player);
            playerHolder.set(player);
        });
        sequence.thenWaitUntil(() -> {
            DeployerFakePlayer player = playerHolder.get();
            BlockPos absoluteFrom = helper.absolutePos(BOTTOM_ELEVATOR);
            BlockPos absoluteTo = helper.absolutePos(TOP_ELEVATOR);
            TeleportPacket packet = new TeleportPacket(absoluteFrom, absoluteTo);
            if (isBadTeleportPacket(packet, player)) {
                throw new GameTestAssertException(
                        "Expected TeleportPacket.isBadTeleportPacket to accept the packet once the sub-level is resolved");
            }
        });
        sequence.thenSucceed();
    }

    private static void glueStructure(final GameTestHelper helper) {
        glueStructure(helper, STRUCTURE_BLOCKS);
    }

    private static void glueStructure(final GameTestHelper helper, final BlockPos[] structureBlocks) {
        for (final BlockPos a : structureBlocks) {
            for (final BlockPos b : structureBlocks) {
                if (a.getX() + 1 == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ()
                        || a.getX() == b.getX() && a.getY() + 1 == b.getY() && a.getZ() == b.getZ()
                        || a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() + 1 == b.getZ()) {
                    glue(helper, a, b);
                }
            }
        }
    }

    private static void glue(final GameTestHelper helper, final BlockPos a, final BlockPos b) {
        AABB span = SuperGlueEntity.span(helper.absolutePos(a), helper.absolutePos(b));
        helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(), span));
    }

    private static void triggerAssembler(final GameTestHelper helper) {
        triggerAssembler(helper, ASSEMBLER);
    }

    private static void triggerAssembler(final GameTestHelper helper, final BlockPos assembler) {
        DeployerFakePlayer deployer = new DeployerFakePlayer(helper.getLevel(), DeployerFakePlayer.fallbackID);
        BlockPos absoluteAssembler = helper.absolutePos(assembler);
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(absoluteAssembler), Direction.UP, absoluteAssembler, false);
        helper.useBlock(assembler, deployer, hitResult);
    }

    /**
     * With only one block of headroom between the two elevators, a player
     * teleporting up should land standing on the top elevator (squeezed
     * into that single block, as they would above an overworld elevator
     * with the same gap) rather than being displaced elsewhere by the
     * sub-level position resolution.
     */
    @GameTest(template = "elevator2", timeoutTicks = 400)
    public static void elevatorHandlesTightGapInSubLevel(final GameTestHelper helper) {
        glueStructure(helper, STRUCTURE_BLOCKS_2);
        triggerAssembler(helper, ASSEMBLER_2);

        AtomicReference<ServerPlayer> playerHolder = new AtomicReference<>();
        GameTestSequence sequence = helper.startSequence();
        sequence.thenWaitUntil(() -> {
            if (!helper.getLevel().getBlockState(helper.absolutePos(TOP_ELEVATOR_2)).isAir()
                    || !helper.getLevel().getBlockState(helper.absolutePos(BOTTOM_ELEVATOR_2)).isAir()) {
                throw new GameTestAssertException("Expected the tight-gap elevator column to have been assembled out of the overworld level");
            }
        });
        sequence.thenExecute(() -> {
            DeployerFakePlayer player = new DeployerFakePlayer(helper.getLevel(), DeployerFakePlayer.fallbackID);
            BlockPos absoluteFrom = helper.absolutePos(BOTTOM_ELEVATOR_2);
            player.setPos(absoluteFrom.getX() + 0.5D, absoluteFrom.getY(), absoluteFrom.getZ() + 0.5D);
            helper.getLevel().addFreshEntity(player);
            playerHolder.set(player);
        });
        sequence.thenExecute(() -> {
            ServerPlayer player = playerHolder.get();
            BlockPos absoluteFrom = helper.absolutePos(BOTTOM_ELEVATOR_2);
            BlockPos absoluteTo = helper.absolutePos(TOP_ELEVATOR_2);
            TeleportPacket packet = new TeleportPacket(absoluteFrom, absoluteTo);
            TeleportPacket.handle(packet, player, Registry.TELEPORT_SOUND.get());
        });
        sequence.thenWaitUntil(() -> {
            ServerPlayer player = playerHolder.get();
            BlockPos absoluteTo = helper.absolutePos(TOP_ELEVATOR_2);
            BlockPos landedAt = player.blockPosition();
            if (landedAt.getX() != absoluteTo.getX() || landedAt.getY() != absoluteTo.getY() || landedAt.getZ() != absoluteTo.getZ()) {
                throw new GameTestAssertException(
                        "Expected the player to land on the top elevator at " + absoluteTo + " but landed at " + landedAt);
            }
        });
        sequence.thenSucceed();
    }

    private static boolean isBadTeleportPacket(final TeleportPacket packet, final Player player) {
        try {
            Method method = TeleportPacket.class.getDeclaredMethod("isBadTeleportPacket", TeleportPacket.class, Player.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, packet, player);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
