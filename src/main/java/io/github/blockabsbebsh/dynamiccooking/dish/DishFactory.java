package io.github.blockabsbebsh.dynamiccooking.dish;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.component.DishContents;
import io.github.blockabsbebsh.dynamiccooking.component.ModComponents;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishResult;
import io.github.blockabsbebsh.dynamiccooking.item.ModItems;

/**
 * Turns a {@link DishResult} into an item stack with its name, food value, buff, look and contents set.
 */
public final class DishFactory {
	/** Buff id for a chorus-fruit style random teleport instead of a status effect. */
	public static final String TELEPORT = DynamicCooking.id("teleport").toString();

	private DishFactory() {
	}

	public static ItemStack create(DishResult result) {
		Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(result.item())).orElse(ModItems.DUBIOUS_MUSH);
		ItemStack stack = new ItemStack(item);

		stack.set(ModComponents.DISH, new DishContents(result.ingredients(), result.flavors()));
		stack.set(DataComponents.FOOD, new FoodProperties(result.nutrition(), result.saturation(), false));
		// The item model picks one recolored sprite per layer from these strings, strongest flavor first.
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), result.flavors(), List.of()));

		result.buff().flatMap(DishFactory::consumeEffect).ifPresent(effect -> {
			Consumable.Builder consumable = ModItems.DRINKS.contains(item) ? Consumables.defaultDrink() : Consumables.defaultFood();
			stack.set(DataComponents.CONSUMABLE, consumable.onConsume(effect).build());
		});

		if (!result.dubious()) {
			DishNames.name(result).ifPresent(name -> stack.set(DataComponents.ITEM_NAME, name));
		}

		return stack;
	}

	private static Optional<ConsumeEffect> consumeEffect(DishResult.Buff buff) {
		if (buff.effect().equals(TELEPORT)) {
			return Optional.of(new TeleportRandomlyConsumeEffect());
		}

		return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(buff.effect()))
				.<ConsumeEffect>map(effect -> new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(effect, buff.durationTicks(), buff.amplifier()), 1.0f));
	}
}
