package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A kind of dish and the ingredients it needs. Loaded from {@code data/<namespace>/dynamic_cooking/dish_type/*.json}.
 *
 * @param item              the item id the dish is made as, e.g. {@code dynamic_cooking:cake}
 * @param priority          lower is checked first, so specific dishes should have lower numbers than general ones
 * @param method            where the dish is made, the pot or a crafting table
 * @param requires          slots that must all be filled, each by different ingredients
 * @param forbids           if any ingredient matches one of these, the dish type does not apply
 * @param flavorRoles       leftover ingredients with one of these roles also flavor the dish
 * @param bonusNutrition    food points added on top of the ingredients
 * @param bonusSaturation   saturation added on top of the ingredients
 * @param liquid            whether the dish is runny, like a stew; the pot then shows liquid instead of a thick mash
 * @param servedWith        item id of the container that takes the dish out of the pot, like {@code minecraft:bowl};
 *                          when empty the dish pops out of the pot by itself
 * @param servings          how many times the pot can be served from before it is empty, when {@code servedWith} is set
 * @param rawOk             raw ingredients matching one of these are fine uncooked in this dish, like raw fish in a kelp roll
 * @param makes             how many items one batch makes, like 4 kelp rolls; the dish's food is shared out between them
 * @param simmersInto       the dish this one turns into when left on the heat once cooked, like soup into stew
 * @param simmerSeconds     how long that takes, from when the dish is ready
 * @param burnSeconds       how long the dish can wait on the heat before it burns into the fallback dish; 0 for the
 *                          default in {@link CookingRules}
 * @param melts             whether the dish melts when cooked again, like jelly: the pot and furnaces won't take it, so
 *                          raw ingredients in it stay raw
 */
public record DishType(
		String item,
		int priority,
		CookingMethod method,
		List<Requirement> requires,
		List<Matcher> forbids,
		Set<String> flavorRoles,
		int bonusNutrition,
		float bonusSaturation,
		boolean liquid,
		Optional<String> servedWith,
		int servings,
		List<Matcher> rawOk,
		int makes,
		Optional<String> simmersInto,
		int simmerSeconds,
		int burnSeconds,
		boolean melts
) {
	public static final Set<String> DEFAULT_FLAVOR_ROLES = Set.of("produce", "protein", "mushroom");

	public DishType {
		requires = List.copyOf(requires);
		forbids = List.copyOf(forbids);
		flavorRoles = Set.copyOf(flavorRoles);
		rawOk = List.copyOf(rawOk);

		if (servings < 1) {
			throw new IllegalArgumentException("servings must be at least 1, got " + servings);
		}

		if (makes < 1) {
			throw new IllegalArgumentException("makes must be at least 1, got " + makes);
		}

		if (simmersInto.isPresent() && simmerSeconds < 1) {
			throw new IllegalArgumentException("a dish that simmers into another needs simmer_seconds");
		}
	}

	/** A dish type that doesn't melt. */
	public DishType(String item, int priority, CookingMethod method, List<Requirement> requires, List<Matcher> forbids, Set<String> flavorRoles,
			int bonusNutrition, float bonusSaturation, boolean liquid, Optional<String> servedWith, int servings, List<Matcher> rawOk, int makes,
			Optional<String> simmersInto, int simmerSeconds, int burnSeconds) {
		this(item, priority, method, requires, forbids, flavorRoles, bonusNutrition, bonusSaturation, liquid, servedWith, servings, rawOk, makes,
				simmersInto, simmerSeconds, burnSeconds, false);
	}

	public DishType withMelts(boolean melts) {
		return new DishType(item, priority, method, requires, forbids, flavorRoles, bonusNutrition, bonusSaturation, liquid, servedWith, servings,
				rawOk, makes, simmersInto, simmerSeconds, burnSeconds, melts);
	}

	/** A dish type that doesn't simmer into anything and burns after the default time. */
	public DishType(String item, int priority, CookingMethod method, List<Requirement> requires, List<Matcher> forbids, Set<String> flavorRoles,
			int bonusNutrition, float bonusSaturation, boolean liquid, Optional<String> servedWith, int servings, List<Matcher> rawOk, int makes) {
		this(item, priority, method, requires, forbids, flavorRoles, bonusNutrition, bonusSaturation, liquid, servedWith, servings, rawOk, makes,
				Optional.empty(), 0, 0);
	}

	/** A dish type where no raw ingredient is excused. */
	public DishType(String item, int priority, CookingMethod method, List<Requirement> requires, List<Matcher> forbids, Set<String> flavorRoles,
			int bonusNutrition, float bonusSaturation, boolean liquid, Optional<String> servedWith, int servings) {
		this(item, priority, method, requires, forbids, flavorRoles, bonusNutrition, bonusSaturation, liquid, servedWith, servings, List.of(), 1);
	}
}
