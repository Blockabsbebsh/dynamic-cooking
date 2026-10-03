package io.github.blockabsbebsh.dynamiccooking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;

import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.cooking.PotColors;

public class DynamicCookingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Tint index 0 on the pot is the liquid, colored by what's in it; indexes 1 to 5 are the chunks floating in it, one per ingredient.
		BlockColorRegistry.register((state, level, pos, tints) -> {
			int[] colors = ((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof int[] synced ? synced : new int[0];

			for (int i = 0; i <= CookingRules.DEFAULT.maxIngredients(); i++) {
				tints.add(0xFF000000 | (i < colors.length ? colors[i] : PotColors.WATER));
			}
		}, ModBlocks.COOKING_POT);
	}
}
