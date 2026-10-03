package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.Set;

/**
 * Tunable numbers for cooking. Kept in one place so balancing never means hunting through logic.
 *
 * @param maxIngredients       most ingredients the pot accepts
 * @param maxNutrition         cap on a dish's food points
 * @param varietyBonus         extra food points per distinct flavor after the first
 * @param nameFlavorCount      how many flavors can appear in a dish name
 * @param fallbackItem         item id made when nothing matches
 * @param fallbackNutrition    food points of the fallback dish
 * @param fallbackSaturation   saturation of the fallback dish
 * @param buffBaseTicks        buff duration from one unit of potency
 * @param buffTicksPerPotency  extra duration per further unit of potency
 * @param buffMaxTicks         cap on buff duration
 * @param buffLevelTwoPotency  potency at which the buff becomes level II
 * @param extraRoles           roles any dish accepts as extras on top of its requirements, like seasonings
 */
public record CookingRules(
		int maxIngredients,
		int maxNutrition,
		int varietyBonus,
		int nameFlavorCount,
		String fallbackItem,
		int fallbackNutrition,
		float fallbackSaturation,
		int buffBaseTicks,
		int buffTicksPerPotency,
		int buffMaxTicks,
		int buffLevelTwoPotency,
		Set<String> extraRoles
) {
	public static final CookingRules DEFAULT = new CookingRules(
			5, 20, 1, 2,
			"dynamic_cooking:dubious_mush", 2, 1.0f,
			30 * 20, 30 * 20, 5 * 60 * 20, 4,
			Set.of("seasoning", "seeds")
	);

	public CookingRules {
		extraRoles = Set.copyOf(extraRoles);
	}
}
