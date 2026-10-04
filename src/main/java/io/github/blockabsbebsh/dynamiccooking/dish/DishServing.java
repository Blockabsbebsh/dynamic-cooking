package io.github.blockabsbebsh.dynamiccooking.dish;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.UseRemainder;

import io.github.blockabsbebsh.dynamiccooking.component.DishVessel;
import io.github.blockabsbebsh.dynamiccooking.component.ModComponents;

/**
 * Puts a dish from the pot into the container it is taken out with. In its usual container, like soup in a bowl, the dish
 * is left as it is. In another one it is renamed ("Bottle of Pumpkin Soup"), drawn in that container, drunk from a bottle
 * or bucket, and gives the container back. A bucket of several servings gives back the same bucket with one fewer.
 */
public final class DishServing {
	private DishServing() {
	}

	/**
	 * @param dish     one serving, as the pot holds it
	 * @param usual    the item the dish is normally served with, like a bowl
	 * @param servings how many servings the container holds, at most its capacity
	 */
	public static ItemStack fill(ItemStack dish, Vessel vessel, int servings, Optional<Vessel> usual) {
		ItemStack out = dish.copyWithCount(1);

		if (usual.filter(vessel::equals).isPresent() && servings == 1) {
			return out;
		}

		out.set(ModComponents.VESSEL, new DishVessel(vessel, servings));
		out.set(DataComponents.ITEM_NAME, Component.translatable("item.dynamic_cooking.in_" + vessel.getSerializedName(), dish.getHoverName()));
		out.set(DataComponents.USE_REMAINDER, new UseRemainder(servings > 1
				? ItemStackTemplate.fromNonEmptyStack(fill(dish, vessel, servings - 1, usual))
				: new ItemStackTemplate(vessel.item())));

		if (vessel == Vessel.BUCKET) {
			out.set(DataComponents.MAX_STACK_SIZE, 1);
		}

		CustomModelData model = out.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(vessel.modelCode()), model.flags(), model.strings(), model.colors()));

		if (vessel.drunk()) {
			Consumable.Builder drink = Consumables.defaultDrink();
			Consumable eaten = out.get(DataComponents.CONSUMABLE);

			if (eaten != null) {
				eaten.onConsumeEffects().forEach(drink::onConsume);
			}

			out.set(DataComponents.CONSUMABLE, drink.build());
		}

		return out;
	}

	/** How many servings a filled container holds: a bucket's count, else 1. */
	public static int servings(ItemStack stack) {
		DishVessel vessel = stack.get(ModComponents.VESSEL);
		return vessel == null ? 1 : vessel.servings();
	}

	/** Whether this is a bucket of a dish, which the pot tops up. */
	public static boolean isDishBucket(ItemStack stack) {
		DishVessel vessel = stack.get(ModComponents.VESSEL);
		return vessel != null && vessel.vessel() == Vessel.BUCKET;
	}

	/** Whether a filled container holds the same dish as the pot: the same kind, made from the same ingredients. */
	public static boolean sameDish(ItemStack filled, ItemStack dish) {
		return filled.is(dish.getItem()) && Objects.equals(filled.get(ModComponents.DISH), dish.get(ModComponents.DISH));
	}

	/** The containers a dish can be taken out with: a bottle only takes runny dishes. */
	public static List<Vessel> takes(boolean runny) {
		List<Vessel> out = new ArrayList<>(List.of(Vessel.values()));

		if (!runny) {
			out.remove(Vessel.BOTTLE);
		}

		return out;
	}
}
