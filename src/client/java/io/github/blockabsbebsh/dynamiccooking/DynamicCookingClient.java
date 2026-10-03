package io.github.blockabsbebsh.dynamiccooking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;

import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;
import io.github.blockabsbebsh.dynamiccooking.cooking.PotColors;

public class DynamicCookingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Tint index 0 on the pot is the liquid, colored by what's in it.
		BlockColorRegistry.register((state, level, pos, tints) -> {
			int color = ((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof Integer liquid ? liquid : PotColors.WATER;
			tints.add(0xFF000000 | color);
		}, ModBlocks.COOKING_POT);
	}
}
