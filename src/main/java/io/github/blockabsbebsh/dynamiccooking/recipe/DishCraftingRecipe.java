package io.github.blockabsbebsh.dynamiccooking.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import io.github.blockabsbebsh.dynamiccooking.cooking.CookingMethod;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * Makes every dish type with {@code "method": "crafting"} on a crafting table, from one ingredient per slot in any shape.
 * Which dish comes out is decided by the same rules as the pot, so new crafted dishes only need data.
 */
public class DishCraftingRecipe extends CustomRecipe {
	public static final DishCraftingRecipe INSTANCE = new DishCraftingRecipe();
	public static final MapCodec<DishCraftingRecipe> CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, DishCraftingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<DishCraftingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

	/**
	 * {@link #assemble} gets no registry access, so it reuses the data that {@link #matches} looked up on the same thread.
	 * The game always checks a recipe matches before assembling it.
	 */
	private static final ThreadLocal<CookingService> LAST_MATCHED = new ThreadLocal<>();

	@Override
	public boolean matches(CraftingInput input, Level level) {
		CookingService cooking = CookingService.create(level.registryAccess());
		LAST_MATCHED.set(cooking);
		return cooking.match(CookingMethod.CRAFTING, ingredients(input)).isPresent();
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		CookingService cooking = LAST_MATCHED.get();
		Optional<ItemStack> dish = cooking == null ? Optional.empty() : cooking.craft(ingredients(input));
		return dish.orElse(ItemStack.EMPTY);
	}

	private static List<ItemStack> ingredients(CraftingInput input) {
		return input.items().stream().filter(stack -> !stack.isEmpty()).toList();
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return SERIALIZER;
	}
}
