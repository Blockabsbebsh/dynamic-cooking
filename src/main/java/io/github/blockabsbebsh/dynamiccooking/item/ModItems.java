package io.github.blockabsbebsh.dynamiccooking.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;

/**
 * One item per dish type, plus the cooking guide. A stack's name, food value, buff and look come from its components, set when it is cooked;
 * the defaults here only apply to stacks that were never cooked, such as ones pulled from the creative menu.
 */
public final class ModItems {
	private static final List<Item> DISHES = new ArrayList<>();

	public static final Item CAKE = cake("cake", 4, 0.6f);
	public static final Item PIE = dish("pie", 4, 0.6f, UnaryOperator.identity());
	public static final Item COOKIES = dish("cookies", 2, 0.2f, UnaryOperator.identity());
	public static final Item JUICE = drink("juice", 2, 0.2f);
	public static final Item STEW = dish("stew", 6, 0.6f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item SOUP = dish("soup", 4, 0.6f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item SALAD = dish("salad", 3, 0.3f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item SANDWICH = dish("sandwich", 6, 0.6f, UnaryOperator.identity());
	public static final Item KELP_ROLL = dish("kelp_roll", 4, 0.5f, UnaryOperator.identity());
	public static final Item SKEWER = dish("skewer", 5, 0.6f, properties -> properties.usingConvertsTo(Items.STICK));
	public static final Item OMELETTE = dish("omelette", 4, 0.6f, UnaryOperator.identity());
	public static final Item ROAST = dish("roast", 7, 0.8f, UnaryOperator.identity());
	public static final Item DUBIOUS_MUSH = dish("dubious_mush", 2, 0.1f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item PORRIDGE = dish("porridge", 5, 0.6f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item TEA = drink("tea", 2, 0.3f);
	public static final Item WARM_MILK = drink("warm_milk", 3, 0.4f);
	public static final Item JELLY = dish("jelly", 3, 0.3f, UnaryOperator.identity());
	public static final Item ICE_CREAM = dish("ice_cream", 3, 0.3f, properties -> properties.usingConvertsTo(Items.BOWL));

	/**
	 * A slice cut from a placed cake with a sword. A slice of a cooked cake carries that cake's name, buff and colors; one cut
	 * from a vanilla cake keeps these defaults, a vanilla cake slice's food value.
	 */
	public static final Item CAKE_SLICE = registerCakeSlice();

	public static final Item COOKBOOK = registerCookbook();

	/** Dishes that are drunk rather than eaten. */
	public static final Set<Item> DRINKS = Set.of(JUICE, TEA, WARM_MILK);

	private ModItems() {
	}

	public static List<Item> dishes() {
		return Collections.unmodifiableList(DISHES);
	}

	public static void initialize() {
		DynamicCooking.LOGGER.info("Registered {} dish items", DISHES.size());
	}

	private static Item dish(String name, int nutrition, float saturationModifier, UnaryOperator<Item.Properties> extra) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
		return register(name, extra.apply(new Item.Properties().stacksTo(16).food(food)));
	}

	/** A dish placed as a block and eaten a slice at a time. It keeps its food value for the slices but can't be eaten from the hand. */
	private static Item cake(String name, int nutrition, float saturationModifier) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
		return register(name, new Item.Properties().stacksTo(16).component(DataComponents.FOOD, food), properties -> new DishCakeItem(ModBlocks.CAKE, properties));
	}

	private static Item drink(String name, int nutrition, float saturationModifier) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
		return register(name, new Item.Properties().stacksTo(16).food(food, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.GLASS_BOTTLE));
	}

	private static Item registerCakeSlice() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DynamicCooking.id("cake_slice"));
		FoodProperties food = new FoodProperties.Builder().nutrition(2).saturationModifier(0.1f).build();
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().food(food).setId(key)));
	}

	private static Item registerCookbook() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DynamicCooking.id("cookbook"));
		return Registry.register(BuiltInRegistries.ITEM, key, new GuideItem(new Item.Properties().stacksTo(1).setId(key)));
	}

	private static Item register(String name, Item.Properties properties) {
		return register(name, properties, Item::new);
	}

	private static Item register(String name, Item.Properties properties, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DynamicCooking.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		DISHES.add(item);
		return item;
	}
}
