package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of cooking.
 *
 * @param item         item id of the dish
 * @param ingredients  item ids that went in, in the order they were added; the pot lists raw ingredients as their cooked versions
 * @param flavors      flavor keys, strongest first; drive texture layers
 * @param flavorColors one {@code 0xRRGGBB} color per flavor, from the first ingredient that gave it; tints texture layers that have no palette
 * @param rawFlavors   one entry per flavor, true when an ingredient giving it is still raw, so the texture can show it raw
 * @param nameFlavors  the one or two flavors that appear in the dish name
 * @param nutrition    food points
 * @param saturation   saturation points
 * @param buff         the buff, if the ingredients agreed on one
 * @param sideEffects  effects that may hit the eater, from raw or spoiled ingredients
 * @param raw          true when a raw ingredient is held against the dish; cooking it in the pot fixes that
 * @param dubious      true when no dish type matched
 * @param liquid       whether the dish is runny, see {@link DishType#liquid()}
 * @param servedWith   item id that takes the dish out of the pot, see {@link DishType#servedWith()}
 * @param servings     how many dishes the pot serves, see {@link DishType#servings()}
 */
public record DishResult(
		String item,
		List<String> ingredients,
		List<String> flavors,
		List<Integer> flavorColors,
		List<Boolean> rawFlavors,
		List<String> nameFlavors,
		int nutrition,
		float saturation,
		Optional<Buff> buff,
		List<SideEffect> sideEffects,
		boolean raw,
		boolean dubious,
		boolean liquid,
		Optional<String> servedWith,
		int servings
) {
	/** Color for an ingredient that has none, so its texture layer keeps the template's grey. */
	public static final int NO_COLOR = 0xFFFFFF;

	public DishResult {
		ingredients = List.copyOf(ingredients);
		flavors = List.copyOf(flavors);
		flavorColors = List.copyOf(flavorColors);
		rawFlavors = List.copyOf(rawFlavors);
		nameFlavors = List.copyOf(nameFlavors);
		sideEffects = List.copyOf(sideEffects);
	}

	/**
	 * A resolved buff.
	 *
	 * @param effect        effect id
	 * @param amplifier     0 for level I, 1 for level II
	 * @param durationTicks duration in ticks, 20 per second
	 */
	public record Buff(String effect, int amplifier, int durationTicks) {
	}
}
