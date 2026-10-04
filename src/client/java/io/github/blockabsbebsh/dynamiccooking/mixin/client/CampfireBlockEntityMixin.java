package io.github.blockabsbebsh.dynamiccooking.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;

/**
 * A campfire under a cooking pot smokes far less, so the smoke doesn't hide the pot or its status panel. It keeps one
 * puff in four, so it still reads as a lit fire.
 */
@Mixin(CampfireBlockEntity.class)
abstract class CampfireBlockEntityMixin {
	@Inject(method = "particleTick", at = @At("HEAD"), cancellable = true)
	private static void dynamicCooking$lessSmokeUnderPot(Level level, BlockPos pos, BlockState state, CampfireBlockEntity campfire, CallbackInfo info) {
		if (level.getBlockState(pos.above()).is(ModBlocks.COOKING_POT) && level.getRandom().nextInt(4) != 0) {
			info.cancel();
		}
	}
}
