package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A small hand-built pantry mirroring the shipped data, so tests read like recipes.
 */
final class TestPantry {
	private static final Map<String, IngredientProfile> PROFILES = new HashMap<>();

	static {
		add("wheat", null, 0, 0, null, "flour");
		add("sugar", null, 0, 0, null, "sweet");
		add("egg", "egg", 1, 0.5f, null, "egg");
		add("bowl", null, 0, 0, null, "bowl");
		add("water_bucket", null, 0, 0, null, "water");
		add("brown_mushroom", "mushroom", 1, 0.6f, null, "mushroom");
		add("stick", null, 0, 0, null, "stick");
		add("carrot", "carrot", 3, 3.6f, null, "produce");
		add("potato", "potato", 1, 0.6f, null, "produce");
		add("apple", "apple", 4, 2.4f, null, "produce", "fresh");
		add("melon_slice", "melon", 2, 1.2f, null, "produce", "fresh");
		add("beef", "beef", 3, 1.8f, null, "protein");
		add("golden_carrot", "carrot", 6, 14.4f, new IngredientProfile.BuffSource("minecraft:night_vision", 1), "produce");
		add("chorus_fruit", "chorus_fruit", 4, 2.4f, new IngredientProfile.BuffSource("dynamic_cooking:teleport", 1), "produce", "fresh");
		add("magma_cream", null, 0, 0, new IngredientProfile.BuffSource("minecraft:fire_resistance", 1), "seasoning");
	}

	static final DishType CAKE = type("cake", 1, 4, 2.0f,
			List.of(req(Matcher.role("flour")), req(Matcher.role("sweet")), req(Matcher.role("egg")), flavor(Matcher.role("produce"))),
			List.of());
	static final DishType PIE = type("pie", 2, 3, 1.5f,
			List.of(req(Matcher.role("flour")), req(Matcher.role("egg")), flavor(Matcher.role("produce", "protein"))),
			List.of());
	static final DishType STEW = served("stew", 5, 2, 2.0f,
			List.of(req(Matcher.role("water")), flavor(Matcher.role("protein")), flavor(Matcher.role("produce", "mushroom"))),
			List.of());
	static final DishType SALAD = crafted("salad", 6, 1, 1.0f,
			List.of(req(Matcher.role("bowl")), new Requirement(Matcher.role("fresh"), 2, true)),
			List.of(Matcher.role("protein", "mushroom")));
	static final DishType SOUP = served("soup", 7, 2, 1.0f,
			List.of(req(Matcher.role("water")), flavor(Matcher.role("produce", "mushroom"))),
			List.of(Matcher.role("protein")));
	static final DishType SKEWER = crafted("skewer", 10, 1, 1.0f,
			List.of(req(Matcher.role("stick")), flavor(Matcher.role("protein", "produce"))),
			List.of());

	static final DishType ROAST = type("roast", 12, 2, 2.0f,
			List.of(new Requirement(Matcher.role("protein", "produce", "mushroom"), 2, true)),
			List.of(Matcher.role("water", "bowl", "stick", "flour", "sweet")));

	static final List<DishType> ALL = List.of(SKEWER, ROAST, SOUP, SALAD, STEW, PIE, CAKE);

	private TestPantry() {
	}

	static CookingResolver resolver() {
		return new CookingResolver(ALL, CookingRules.DEFAULT);
	}

	/** Crafts the named ingredients, failing the test if they make nothing. */
	static DishResult craft(String... names) {
		return resolver().match(CookingMethod.CRAFTING, inputs(names)).orElseThrow(() -> new AssertionError(List.of(names) + " crafts nothing"));
	}

	static List<CookingInput> inputs(String... names) {
		return Arrays.stream(names).map(TestPantry::input).toList();
	}

	static CookingInput input(String name) {
		IngredientProfile profile = PROFILES.get(name);

		if (profile == null) {
			throw new IllegalArgumentException("No test profile for " + name);
		}

		return new CookingInput("minecraft:" + name, profile);
	}

	static DishType type(String name, int priority, int bonusNutrition, float bonusSaturation, List<Requirement> requires, List<Matcher> forbids) {
		return new DishType("dynamic_cooking:" + name, priority, CookingMethod.POT, requires, forbids, DishType.DEFAULT_FLAVOR_ROLES, bonusNutrition, bonusSaturation, false, Optional.empty(), 1);
	}

	/** A runny pot dish that stays in the pot until it is served with a bowl. */
	static DishType served(String name, int priority, int bonusNutrition, float bonusSaturation, List<Requirement> requires, List<Matcher> forbids) {
		return new DishType("dynamic_cooking:" + name, priority, CookingMethod.POT, requires, forbids, DishType.DEFAULT_FLAVOR_ROLES, bonusNutrition, bonusSaturation, true, Optional.of("minecraft:bowl"), 1);
	}

	static DishType crafted(String name, int priority, int bonusNutrition, float bonusSaturation, List<Requirement> requires, List<Matcher> forbids) {
		return new DishType("dynamic_cooking:" + name, priority, CookingMethod.CRAFTING, requires, forbids, DishType.DEFAULT_FLAVOR_ROLES, bonusNutrition, bonusSaturation, false, Optional.empty(), 1);
	}

	static Requirement req(Matcher matcher) {
		return new Requirement(matcher, 1, false);
	}

	static Requirement flavor(Matcher matcher) {
		return new Requirement(matcher, 1, true);
	}

	private static void add(String name, String flavor, int nutrition, float saturation, IngredientProfile.BuffSource buff, String... roles) {
		PROFILES.put(name, new IngredientProfile(
				List.of("minecraft:" + name),
				Set.of(roles),
				Optional.ofNullable(flavor),
				nutrition,
				saturation,
				Optional.ofNullable(buff),
				Optional.empty()
		));
	}
}
