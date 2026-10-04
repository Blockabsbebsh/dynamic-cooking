package io.github.blockabsbebsh.dynamiccooking;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.CampfireBlock;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;

import io.github.blockabsbebsh.dynamiccooking.block.DishCakeBlockEntity;
import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;
import io.github.blockabsbebsh.dynamiccooking.client.CampfireUnderPotModel;
import io.github.blockabsbebsh.dynamiccooking.client.GuideScreen;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.cooking.PotColors;
import io.github.blockabsbebsh.dynamiccooking.item.GuideItem;

public class DynamicCookingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GuideItem.opener = () -> Minecraft.getInstance().setScreenAndShow(new GuideScreen());

		// Campfires, soul campfires and modded ones: flames shrink to fit under a pot sitting in them.
		ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) ->
				context.state().getBlock() instanceof CampfireBlock ? new CampfireUnderPotModel(model) : model));

		// Tint index 0 on the pot is the liquid, colored by what's in it; indexes 1 to 5 are the chunks floating in it, one per ingredient.
		BlockColorRegistry.register((state, level, pos, tints) -> {
			int[] colors = ((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof int[] synced ? synced : new int[0];

			for (int i = 0; i <= CookingRules.DEFAULT.maxIngredients(); i++) {
				tints.add(0xFF000000 | (i < colors.length ? colors[i] : PotColors.WATER));
			}
		}, ModBlocks.COOKING_POT);

		// A placed cake: tint index 0 is the fruit on top, 1 the filling between the layers.
		BlockColorRegistry.register((state, level, pos, tints) -> {
			int[] colors = ((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof int[] synced ? synced : new int[0];

			for (int i = 0; i < 2; i++) {
				tints.add(0xFF000000 | (i < colors.length ? colors[i] : DishCakeBlockEntity.DEFAULT_COLOR));
			}
		}, ModBlocks.CAKE);
	}
}
