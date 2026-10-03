package io.github.blockabsbebsh.dynamiccooking.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;

/**
 * Data pack registries for cooking. Entries live at
 * {@code data/<namespace>/dynamic_cooking/ingredient/*.json} and {@code data/<namespace>/dynamic_cooking/dish_type/*.json},
 * so data packs and other mods can add ingredients and dishes without code.
 */
public final class ModRegistries {
	public static final ResourceKey<Registry<IngredientProfile>> INGREDIENT = ResourceKey.createRegistryKey(DynamicCooking.id("ingredient"));
	public static final ResourceKey<Registry<DishType>> DISH_TYPE = ResourceKey.createRegistryKey(DynamicCooking.id("dish_type"));

	private ModRegistries() {
	}

	public static void initialize() {
		// Synced so the client can build the creative tab's sample dishes and, later, recipe hints.
		DynamicRegistries.registerSynced(INGREDIENT, CookingCodecs.INGREDIENT_PROFILE);
		DynamicRegistries.registerSynced(DISH_TYPE, CookingCodecs.DISH_TYPE);
	}
}
