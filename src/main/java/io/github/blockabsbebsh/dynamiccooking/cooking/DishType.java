package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.List;
import java.util.Set;

/**
 * A kind of dish and the ingredients it needs. Loaded from {@code data/<namespace>/dynamic_cooking/dish_type/*.json}.
 *
 * @param item              the item id the dish is made as, e.g. {@code dynamic_cooking:cake}
 * @param priority          lower is checked first, so specific dishes should have lower numbers than general ones
 * @param requires          slots that must all be filled, each by different ingredients
 * @param forbids           if any ingredient matches one of these, the dish type does not apply
 * @param flavorRoles       leftover ingredients with one of these roles also flavor the dish
 * @param bonusNutrition    food points added on top of the ingredients
 * @param bonusSaturation   saturation added on top of the ingredients
 */
public record DishType(
		String item,
		int priority,
		List<Requirement> requires,
		List<Matcher> forbids,
		Set<String> flavorRoles,
		int bonusNutrition,
		float bonusSaturation
) {
	public static final Set<String> DEFAULT_FLAVOR_ROLES = Set.of("produce", "protein", "mushroom");

	public DishType {
		requires = List.copyOf(requires);
		forbids = List.copyOf(forbids);
		flavorRoles = Set.copyOf(flavorRoles);
	}
}
