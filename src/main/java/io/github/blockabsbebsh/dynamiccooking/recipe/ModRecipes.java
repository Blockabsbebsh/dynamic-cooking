package io.github.blockabsbebsh.dynamiccooking.recipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;

public final class ModRecipes {
	private ModRecipes() {
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, DynamicCooking.id("dish_crafting"), DishCraftingRecipe.SERIALIZER);
	}
}
