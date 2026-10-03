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
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingMethod;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingResolver;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishResult;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.registry.ModRegistries;

/**
 * Connects the cooking rules to the game: looks up the loaded data, cooks a list of item stacks, and builds the dish stack.
 * The cooking pot, the crafting recipe and anything else that makes dishes goes through here.
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
	 * Cooks one of each given stack in the pot. Every stack must be an ingredient, see {@link #isIngredient}.
	 */
	public DishResult resolve(List<ItemStack> ingredients) {
		List<CookingInput> inputs = new ArrayList<>();

		for (ItemStack stack : ingredients) {
			inputs.add(input(stack).orElseThrow(() -> new IllegalArgumentException(stack.getItem() + " is not a cooking ingredient")));
		}

		return resolver.resolve(inputs);
	}

	public ItemStack cook(List<ItemStack> ingredients) {
		return DishFactory.create(resolve(ingredients));
	}

	/**
	 * Finds the dish one of each given stack makes with the given method. Unlike the pot, there is no fallback:
	 * any stack that isn't an ingredient, or a mix no dish type fits, gives nothing.
	 */
	public Optional<DishResult> match(CookingMethod method, List<ItemStack> ingredients) {
		List<CookingInput> inputs = new ArrayList<>();

		for (ItemStack stack : ingredients) {
			Optional<CookingInput> input = input(stack);

			if (input.isEmpty()) {
				return Optional.empty();
			}

			inputs.add(input.get());
		}

		return resolver.match(method, inputs);
	}

	public Optional<ItemStack> craft(List<ItemStack> ingredients) {
		return match(CookingMethod.CRAFTING, ingredients).map(DishFactory::create);
	}

	private Optional<CookingInput> input(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}

		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return Optional.ofNullable(profiles.get(id)).map(profile -> new CookingInput(id, profile));
	}
}
