package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.Set;

/**
 * Tunable numbers for cooking. Kept in one place so balancing never means hunting through logic.
 *
 * @param maxIngredients       most ingredients the pot accepts
 * @param maxNutrition         cap on a dish's food points
 * @param varietyBonus         extra food points per distinct flavor after the first
 * @param perIngredientNutrition extra food points for every ingredient used, so each one added makes the dish more filling
 * @param perIngredientSaturation extra saturation for every ingredient used
 * @param containerRoles       roles of ingredients that only hold or serve the dish, like a bowl or a water bucket; they don't
 *                             count as an ingredient used
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
		int perIngredientNutrition,
		float perIngredientSaturation,
		Set<String> containerRoles,
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
			5, 20, 1, 1, 0.5f, Set.of("water", "bowl", "bottle", "stick"), 2,
			"dynamic_cooking:dubious_mush", 2, 1.0f,
			30 * 20, 30 * 20, 5 * 60 * 20, 4,
			Set.of("seasoning", "seeds", "dairy")
	);

	public CookingRules {
		containerRoles = Set.copyOf(containerRoles);
		extraRoles = Set.copyOf(extraRoles);
	}
}
