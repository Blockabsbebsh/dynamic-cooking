package io.github.blockabsbebsh.dynamiccooking.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;
import io.github.blockabsbebsh.dynamiccooking.item.ModItems;

/**
 * Lets furnaces, smokers and campfires cook raw dishes, like a skewer made with raw chicken, into the same cooked dish the pot gives.
 * Each recipe matches any dish item but only accepts stacks the pot could recook, so cooked dishes stay put.
 */
public final class DishCookingRecipes {
	private static final float EXPERIENCE = 0.35f;

	/**
	 * {@code assemble} gets no registry access, so it reuses the data that {@code matches} looked up on the same thread.
	 * Furnaces and campfires always check a recipe matches before assembling it.
	 */
	private static final ThreadLocal<CookingService> LAST_MATCHED = new ThreadLocal<>();

	private static Smelting smelting;
	private static Smoking smoking;
	private static Campfire campfire;

	public static final RecipeSerializer<SmeltingRecipe> SMELTING = serializer(() -> smelting);
	public static final RecipeSerializer<SmokingRecipe> SMOKING = serializer(() -> smoking);
	public static final RecipeSerializer<CampfireCookingRecipe> CAMPFIRE = serializer(() -> campfire);

	private DishCookingRecipes() {
	}

	/** Builds the recipes once the dish items exist. */
	static void initialize() {
		smelting = new Smelting(dishes(), result(), EXPERIENCE, 200);
		smoking = new Smoking(dishes(), result(), EXPERIENCE, 100);
		campfire = new Campfire(dishes(), result(), EXPERIENCE, 600);
	}

	private static Ingredient dishes() {
		return Ingredient.of(ModItems.dishes().toArray(ItemLike[]::new));
	}

	/** Only shown in the recipe book; the real result comes from the dish being cooked. */
	private static ItemStackTemplate result() {
		return new ItemStackTemplate(ModItems.ROAST);
	}

	private static Recipe.CommonInfo info() {
		return new Recipe.CommonInfo(false);
	}

	private static AbstractCookingRecipe.CookingBookInfo bookInfo() {
		return new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.FOOD, "");
	}

	private static <T extends Recipe<?>> RecipeSerializer<T> serializer(java.util.function.Supplier<? extends T> instance) {
		MapCodec<T> codec = MapCodec.unit(instance::get);
		StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = StreamCodec.of((buf, recipe) -> {
		}, buf -> instance.get());
		return new RecipeSerializer<>(codec, streamCodec);
	}

	private static boolean matches(SingleRecipeInput input, Level level) {
		CookingService cooking = CookingService.create(level.registryAccess());
		LAST_MATCHED.set(cooking);
		return cooking.isRecookable(input.item());
	}

	private static ItemStack assemble(SingleRecipeInput input) {
		CookingService cooking = LAST_MATCHED.get();
		return cooking == null ? input.item().copyWithCount(1) : cooking.recook(input.item());
	}

	static final class Smelting extends SmeltingRecipe {
		Smelting(Ingredient input, ItemStackTemplate result, float experience, int cookingTime) {
			super(info(), bookInfo(), input, result, experience, cookingTime);
		}

		@Override
		public boolean matches(SingleRecipeInput input, Level level) {
			return DishCookingRecipes.matches(input, level);
		}

		@Override
		public ItemStack assemble(SingleRecipeInput input) {
			return DishCookingRecipes.assemble(input);
		}

		@Override
		public RecipeSerializer<SmeltingRecipe> getSerializer() {
			return SMELTING;
		}
	}

	static final class Smoking extends SmokingRecipe {
		Smoking(Ingredient input, ItemStackTemplate result, float experience, int cookingTime) {
			super(info(), bookInfo(), input, result, experience, cookingTime);
		}

		@Override
		public boolean matches(SingleRecipeInput input, Level level) {
			return DishCookingRecipes.matches(input, level);
		}

		@Override
		public ItemStack assemble(SingleRecipeInput input) {
			return DishCookingRecipes.assemble(input);
		}

		@Override
		public RecipeSerializer<SmokingRecipe> getSerializer() {
			return SMOKING;
		}
	}

	static final class Campfire extends CampfireCookingRecipe {
		Campfire(Ingredient input, ItemStackTemplate result, float experience, int cookingTime) {
			super(info(), bookInfo(), input, result, experience, cookingTime);
		}

		@Override
		public boolean matches(SingleRecipeInput input, Level level) {
			return DishCookingRecipes.matches(input, level);
		}

		@Override
		public ItemStack assemble(SingleRecipeInput input) {
			return DishCookingRecipes.assemble(input);
		}

		@Override
		public RecipeSerializer<CampfireCookingRecipe> getSerializer() {
			return CAMPFIRE;
		}
	}
}
