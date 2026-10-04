package io.github.blockabsbebsh.dynamiccooking.item;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;
import io.github.blockabsbebsh.dynamiccooking.dish.DishServing;
import io.github.blockabsbebsh.dynamiccooking.dish.Vessel;

/**
 * The mod's creative tab: the pot, the cooking guide, then sample dishes made through the real rules, exactly as the pot or a
 * crafting table would make them from the listed ingredients. Plain never-cooked dish items are left out, since they
 * have no buff or flavor and would look like broken dishes.
 */
public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, DynamicCooking.id("dishes"));

	/**
	 * Each sample is made on a crafting table if it can be, otherwise in the pot. Grouped by dish, meals first, then baked
	 * goods, then drinks, with Dubious Mush last. Only cooked dishes are shown: raw versions would look like duplicates.
	 */
	private static final List<List<Item>> SAMPLES = List.of(
			// Roasts
			List.of(Items.COOKED_BEEF, Items.BAKED_POTATO),
			List.of(Items.RABBIT, Items.CARROT),
			List.of(Items.CARROT, Items.POTATO),
			List.of(Items.SHELF_MUSHROOM, Items.BEETROOT),
			// Soups
			List.of(Items.WATER_BUCKET, Items.BEEF, Items.POTATO),
			List.of(Items.WATER_BUCKET, Items.PUMPKIN),
			List.of(Items.WATER_BUCKET, Items.BROWN_MUSHROOM, Items.RED_MUSHROOM),
			List.of(Items.WATER_BUCKET, Items.BEETROOT),
			// Skewers
			List.of(Items.STICK, Items.COOKED_CHICKEN, Items.CARROT),
			List.of(Items.STICK, Items.COOKED_PORKCHOP, Items.BROWN_MUSHROOM),
			List.of(Items.STICK, Items.GOLDEN_CARROT, Items.COOKED_BEEF),
			List.of(Items.STICK, Items.MAGMA_CREAM, Items.COOKED_BEEF),
			// Sandwiches
			List.of(Items.BREAD, Items.COOKED_PORKCHOP),
			List.of(Items.BREAD, Items.COOKED_CHICKEN, Items.CARROT),
			List.of(Items.BREAD, Items.EGG),
			// Kelp rolls
			List.of(Items.DRIED_KELP, Items.SALMON),
			List.of(Items.DRIED_KELP, Items.COD, Items.CARROT),
			List.of(Items.DRIED_KELP, Items.SHELF_MUSHROOM),
			List.of(Items.DRIED_KELP, Items.PUFFERFISH),
			// Omelettes
			List.of(Items.EGG, Items.EGG),
			List.of(Items.EGG, Items.BROWN_MUSHROOM),
			List.of(Items.EGG, Items.COOKED_PORKCHOP),
			// Salads
			List.of(Items.BOWL, Items.APPLE, Items.SWEET_BERRIES),
			List.of(Items.BOWL, Items.MELON_SLICE, Items.GLOW_BERRIES, Items.PUMPKIN_SEEDS),
			List.of(Items.BOWL, Items.CARROT, Items.BEETROOT),
			// Pies
			List.of(Items.WHEAT, Items.EGG, Items.APPLE),
			List.of(Items.WHEAT, Items.EGG, Items.MUTTON),
			List.of(Items.PUMPKIN, Items.SUGAR, Items.EGG),
			// Cakes
			List.of(Items.WHEAT, Items.SUGAR, Items.EGG, Items.CARROT),
			List.of(Items.WHEAT, Items.SUGAR, Items.EGG, Items.CHORUS_FRUIT),
			List.of(Items.WHEAT, Items.SUGAR, Items.EGG, Items.GOLDEN_CARROT),
			// Cookies
			List.of(Items.WHEAT, Items.COCOA_BEANS),
			List.of(Items.WHEAT, Items.SUGAR, Items.SWEET_BERRIES),
			// Juices
			List.of(Items.GLASS_BOTTLE, Items.MELON_SLICE),
			List.of(Items.GLASS_BOTTLE, Items.APPLE),
			List.of(Items.GLASS_BOTTLE, Items.GLISTERING_MELON_SLICE),
			// Porridge
			List.of(Items.WATER_BUCKET, Items.WHEAT),
			List.of(Items.MILK_BUCKET, Items.WHEAT, Items.SWEET_BERRIES),
			List.of(Items.MILK_BUCKET, Items.PUMPKIN_SEEDS, Items.HONEY_BOTTLE),
			// Teas
			List.of(Items.WATER_BUCKET, Items.POPPY),
			List.of(Items.WATER_BUCKET, Items.CHERRY_LEAVES, Items.HONEY_BOTTLE),
			List.of(Items.WATER_BUCKET, Items.CORNFLOWER),
			// Warm milk
			List.of(Items.MILK_BUCKET, Items.COCOA_BEANS),
			List.of(Items.MILK_BUCKET, Items.HONEY_BOTTLE),
			// Jellies
			List.of(Items.SLIME_BALL, Items.SWEET_BERRIES),
			List.of(Items.SLIME_BALL, Items.MELON_SLICE),
			List.of(Items.SLIME_BALL, Items.CHORUS_FRUIT),
			// Ice cream
			List.of(Items.BOWL, Items.SNOWBALL, Items.SUGAR),
			List.of(Items.BOWL, Items.SNOWBALL, Items.COCOA_BEANS),
			List.of(Items.BOWL, Items.SNOWBALL, Items.SUGAR, Items.SWEET_BERRIES),
			// Dubious Mush
			List.of(Items.SUGAR, Items.SUGAR)
	);

	/** Soups shown simmered into stews, the way a pot left on the heat makes them. */
	private static final List<List<Item>> STEWS = List.of(
			List.of(Items.WATER_BUCKET, Items.BEEF, Items.POTATO),
			List.of(Items.WATER_BUCKET, Items.RABBIT, Items.CARROT, Items.SHELF_MUSHROOM),
			List.of(Items.WATER_BUCKET, Items.COD, Items.CARROT),
			List.of(Items.WATER_BUCKET, Items.CARROT, Items.POTATO, Items.BROWN_MUSHROOM)
	);

	private ModCreativeTab() {
	}

	public static void initialize() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModBlocks.COOKING_POT))
				.title(Component.translatable("itemGroup.dynamic_cooking.dishes"))
				.displayItems((parameters, output) -> {
					output.accept(ModBlocks.COOKING_POT);
					output.accept(ModItems.COOKBOOK);

					CookingService cooking = CookingService.create(parameters.holders());

					for (List<Item> sample : SAMPLES) {
						List<ItemStack> stacks = sample.stream().map(ItemStack::new).toList();

						if (stacks.stream().allMatch(cooking::isIngredient)) {
							// Crafted dishes first; everything else goes through the pot, which also shows Dubious Mush.
							// One of each, even for dishes made in batches like kelp rolls.
							output.accept(cooking.craft(stacks).orElseGet(() -> cooking.cookPot(stacks).dish()).copyWithCount(1));
						} else {
							DynamicCooking.LOGGER.warn("Sample dish {} uses an item with no ingredient profile", Arrays.toString(sample.toArray()));
						}
					}

					// The same dishes in other containers: soup in a bottle, stew in a bucket.
					ItemStack soup = cooking.cookPot(List.of(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.PUMPKIN))).dish();
					output.accept(DishServing.fill(soup, Vessel.BOTTLE, 1, Optional.of(Vessel.BOWL)));
					output.accept(DishServing.fill(soup, Vessel.BUCKET, Vessel.BUCKET_SERVINGS, Optional.of(Vessel.BOWL)));

					for (List<Item> sample : STEWS) {
						List<ItemStack> stacks = sample.stream().map(ItemStack::new).toList();

						if (stacks.stream().allMatch(cooking::isIngredient)) {
							cooking.simmer(cooking.cookPot(stacks).dish()).ifPresent(output::accept);
						}
					}
				})
				.build();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
	}
}
