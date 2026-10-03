package io.github.blockabsbebsh.dynamiccooking.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;

/**
 * One item per dish type. A stack's name, food value, buff and look come from its components, set when it is cooked;
 * the defaults here only apply to stacks that were never cooked, such as ones pulled from the creative menu.
 */
public final class ModItems {
	private static final List<Item> DISHES = new ArrayList<>();

	public static final Item CAKE = dish("cake", 4, 0.6f, UnaryOperator.identity());
	public static final Item PIE = dish("pie", 4, 0.6f, UnaryOperator.identity());
	public static final Item COOKIES = dish("cookies", 2, 0.2f, UnaryOperator.identity());
	public static final Item JUICE = drink("juice", 2, 0.2f);
	public static final Item STEW = dish("stew", 6, 0.6f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item SOUP = dish("soup", 4, 0.6f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item SALAD = dish("salad", 3, 0.3f, properties -> properties.usingConvertsTo(Items.BOWL));
	public static final Item SANDWICH = dish("sandwich", 6, 0.6f, UnaryOperator.identity());
	public static final Item KELP_ROLL = dish("kelp_roll", 4, 0.5f, UnaryOperator.identity());
	public static final Item SKEWER = dish("skewer", 5, 0.6f, UnaryOperator.identity());
	public static final Item OMELETTE = dish("omelette", 4, 0.6f, UnaryOperator.identity());
	public static final Item ROAST = dish("roast", 7, 0.8f, UnaryOperator.identity());
	public static final Item DUBIOUS_MUSH = dish("dubious_mush", 2, 0.1f, UnaryOperator.identity());

	/** Dishes that are drunk rather than eaten. */
	public static final Set<Item> DRINKS = Set.of(JUICE);

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

	private static Item drink(String name, int nutrition, float saturationModifier) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
		return register(name, new Item.Properties().stacksTo(16).food(food, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.GLASS_BOTTLE));
	}

	private static Item register(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DynamicCooking.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
		DISHES.add(item);
		return item;
	}
}
