package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of cooking.
 *
 * @param item         item id of the dish
 * @param ingredients  item ids that went in, in the order they were added
 * @param flavors      flavor keys, strongest first; drive texture layers
 * @param nameFlavors  the one or two flavors that appear in the dish name
 * @param nutrition    food points
 * @param saturation   saturation points
 * @param buff         the buff, if the ingredients agreed on one
 * @param dubious      true when no dish type matched
 */
public record DishResult(
		String item,
		List<String> ingredients,
		List<String> flavors,
		List<String> nameFlavors,
		int nutrition,
		float saturation,
		Optional<Buff> buff,
		boolean dubious
) {
	public DishResult {
		ingredients = List.copyOf(ingredients);
		flavors = List.copyOf(flavors);
		nameFlavors = List.copyOf(nameFlavors);
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
