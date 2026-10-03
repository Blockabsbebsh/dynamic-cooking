package io.github.blockabsbebsh.dynamiccooking.dish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.blockabsbebsh.dynamiccooking.cooking.CookingInput;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingResolver;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishResult;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.registry.ModRegistries;

/**
 * Connects the cooking rules to the game: looks up the loaded data, cooks a list of item stacks, and builds the dish stack.
 * The cooking pot and anything else that cooks goes through here.
 */
public final class CookingService {
	private final Map<String, IngredientProfile> profiles;
	private final CookingResolver resolver;

	private CookingService(Map<String, IngredientProfile> profiles, CookingResolver resolver) {
		this.profiles = profiles;
		this.resolver = resolver;
	}

	/**
	 * Builds a service from the currently loaded data packs. Cheap enough to call per cook.
	 */
	public static CookingService create(HolderLookup.Provider registries) {
		Map<String, IngredientProfile> profiles = new HashMap<>();

		registries.lookupOrThrow(ModRegistries.INGREDIENT).listElements().map(Holder::value).forEach(profile -> {
			for (String item : profile.items()) {
				profiles.put(item, profile);
			}
		});

		List<DishType> dishTypes = registries.lookupOrThrow(ModRegistries.DISH_TYPE).listElements().map(Holder::value).toList();

		return new CookingService(profiles, new CookingResolver(dishTypes, CookingRules.DEFAULT));
	}

	public Optional<IngredientProfile> profile(Item item) {
		return Optional.ofNullable(profiles.get(BuiltInRegistries.ITEM.getKey(item).toString()));
	}

	/** Whether the pot should accept this item at all. */
	public boolean isIngredient(ItemStack stack) {
		return !stack.isEmpty() && profile(stack.getItem()).isPresent();
	}

	public int maxIngredients() {
		return resolver.rules().maxIngredients();
	}

	/**
	 * Cooks one of each given stack. Every stack must be an ingredient, see {@link #isIngredient}.
	 */
	public DishResult resolve(List<ItemStack> ingredients) {
		List<CookingInput> inputs = new ArrayList<>();

		for (ItemStack stack : ingredients) {
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			IngredientProfile profile = profiles.get(id);

			if (profile == null) {
				throw new IllegalArgumentException(id + " is not a cooking ingredient");
			}

			inputs.add(new CookingInput(id, profile));
		}

		return resolver.resolve(inputs);
	}

	public ItemStack cook(List<ItemStack> ingredients) {
		return DishFactory.create(resolve(ingredients));
	}
}
