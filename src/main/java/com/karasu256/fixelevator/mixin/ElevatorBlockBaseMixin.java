package com.karasu256.fixelevator.mixin;

import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.vsngarcia.ElevatorBlockBase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops the elevator block's own click handling from swallowing Create's
 * Super Glue interaction, so right-clicking a "karakuri-ized" elevator
 * (one exposed through a Create: Simulated / Aeronautics sub-level) with
 * glue in hand attaches the glue instead of opening the elevator GUI.
 *
 * <p>On an elevator block placed directly in the world, {@code Create}'s own
 * {@code PlayerInteractEvent.RightClickBlock} listener already stops the
 * block's {@code useItemOn} from running while glue is held. That listener
 * inspects the block at the event's hit position in the outer level, which
 * does not resolve to the elevator block when it is actually stored inside
 * a sub-level's local space, so the event fires as if no block were there
 * and the elevator's own {@code useItemOn} still runs and opens its GUI.
 */
@Mixin(ElevatorBlockBase.class)
public abstract class ElevatorBlockBaseMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void fixelevator$skipMenuForSuperGlue(final ItemStack itemStack, final BlockState state,
            final Level worldIn, final BlockPos pos, final Player player, final InteractionHand handIn,
            final BlockHitResult hit, final CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (fixelevator$isGlueItem(itemStack.getItem())) {
            cir.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
        }
    }

    /**
     * Whether {@code item} is Create's Super Glue, or the vanilla Slime Ball
     * it is crafted from. Both are treated as glue here since players
     * commonly refer to Super Glue by its slime-ball appearance.
     */
    private static boolean fixelevator$isGlueItem(final Item item) {
        return item instanceof SuperGlueItem || item == Items.SLIME_BALL;
    }
}
