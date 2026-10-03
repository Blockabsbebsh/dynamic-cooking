package io.github.blockabsbebsh.dynamiccooking.item;

import java.util.Arrays;
import java.util.List;

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
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * The mod's creative tab: the plain dish items, then sample dishes cooked through the real rules so the data can be
 * checked in game before the cooking pot exists.
 */
public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, DynamicCooking.id("dishes"));

	private static final List<List<Item>> SAMPLES = List.of(
			List.of(Items.WHEAT, Items.SUGAR, Items.EGG, Items.CARROT),
			List.of(Items.WHEAT, Items.SUGAR, Items.EGG, Items.CHORUS_FRUIT),
			List.of(Items.WHEAT, Items.SUGAR, Items.EGG, Items.GOLDEN_CARROT),
			List.of(Items.WHEAT, Items.EGG, Items.APPLE),
			List.of(Items.WHEAT, Items.SUGAR, Items.SWEET_BERRIES),
			List.of(Items.GLASS_BOTTLE, Items.MELON_SLICE),
			List.of(Items.BOWL, Items.BEEF, Items.POTATO),
			List.of(Items.BOWL, Items.APPLE, Items.SWEET_BERRIES),
			List.of(Items.BOWL, Items.PUMPKIN),
			List.of(Items.BREAD, Items.COOKED_PORKCHOP),
			List.of(Items.DRIED_KELP, Items.COD),
			List.of(Items.STICK, Items.CHICKEN, Items.CARROT),
			List.of(Items.EGG, Items.BROWN_MUSHROOM),
			List.of(Items.RABBIT, Items.POTATO),
			List.of(Items.STICK, Items.MAGMA_CREAM, Items.GOLDEN_CARROT, Items.BEEF),
			List.of(Items.SUGAR, Items.SUGAR)
	);

	private ModCreativeTab() {
	}

	public static void initialize() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModItems.CAKE))
				.title(Component.translatable("itemGroup.dynamic_cooking.dishes"))
				.displayItems((parameters, output) -> {
					ModItems.dishes().forEach(output::accept);

					CookingService cooking = CookingService.create(parameters.holders());

					for (List<Item> sample : SAMPLES) {
						List<ItemStack> stacks = sample.stream().map(ItemStack::new).toList();

						if (stacks.stream().allMatch(cooking::isIngredient)) {
							output.accept(cooking.cook(stacks));
						} else {
							DynamicCooking.LOGGER.warn("Sample dish {} uses an item with no ingredient profile", Arrays.toString(sample.toArray()));
						}
					}
				})
				.build();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
	}
}
