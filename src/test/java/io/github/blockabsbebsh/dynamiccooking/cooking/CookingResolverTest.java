package io.github.blockabsbebsh.dynamiccooking.cooking;

import static io.github.blockabsbebsh.dynamiccooking.cooking.TestPantry.inputs;
import static io.github.blockabsbebsh.dynamiccooking.cooking.TestPantry.resolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CookingResolverTest {
	@Test
	void carrotCake() {
		DishResult result = resolver().resolve(inputs("wheat", "sugar", "egg", "carrot"));

		assertEquals("dynamic_cooking:cake", result.item());
		assertEquals(List.of("carrot"), result.nameFlavors());
		assertFalse(result.dubious());
	}

	@Test
	void ingredientOrderDoesNotChangeTheDish() {
		DishResult result = resolver().resolve(inputs("carrot", "egg", "sugar", "wheat"));

		assertEquals("dynamic_cooking:cake", result.item());
		assertEquals(List.of("carrot"), result.nameFlavors());
	}

	@Test
	void requiredIngredientsDoNotFlavorTheDish() {
		DishResult result = resolver().resolve(inputs("wheat", "sugar", "egg", "apple"));

		assertEquals(List.of("apple"), result.flavors());
	}

	@Test
	void cakeBeatsPieWhenSugarIsPresent() {
		assertEquals("dynamic_cooking:cake", resolver().resolve(inputs("wheat", "sugar", "egg", "apple")).item());
		assertEquals("dynamic_cooking:pie", resolver().resolve(inputs("wheat", "egg", "apple")).item());
	}

	@Test
	void twoEqualFlavorsGiveADoubleName() {
		DishResult result = resolver().resolve(inputs("bowl", "beef", "potato"));

		assertEquals("dynamic_cooking:stew", result.item());
		assertEquals(List.of("beef", "potato"), result.nameFlavors());
	}

	@Test
	void theMostCommonFlavorNamesTheDish() {
		DishResult result = resolver().resolve(inputs("stick", "carrot", "beef", "beef"));

		assertEquals("dynamic_cooking:skewer", result.item());
		assertEquals(List.of("beef", "carrot"), result.flavors());
		assertEquals(List.of("beef"), result.nameFlavors());
	}

	@Test
	void forbiddenIngredientsSkipADishType() {
		assertEquals("dynamic_cooking:soup", resolver().resolve(inputs("bowl", "carrot")).item());
		assertEquals("dynamic_cooking:salad", resolver().resolve(inputs("bowl", "apple", "melon_slice")).item());
		assertEquals("dynamic_cooking:stew", resolver().resolve(inputs("bowl", "apple", "melon_slice", "beef")).item());
	}

	@Test
	void nothingMatchingGivesDubiousMush() {
		DishResult result = resolver().resolve(inputs("sugar", "sugar"));

		assertEquals(CookingRules.DEFAULT.fallbackItem(), result.item());
		assertTrue(result.dubious());
		assertEquals(CookingRules.DEFAULT.fallbackNutrition(), result.nutrition());
	}

	@Test
	void slotsBacktrackWhenAGreedyChoiceBlocksALaterSlot() {
		// The first slot accepts either role, the second only "x". Greedy would give the x-ingredient to the first slot.
		IngredientProfile x = new IngredientProfile(List.of("test:x"), Set.of("x"), Optional.of("x"), 1, 0, Optional.empty());
		IngredientProfile y = new IngredientProfile(List.of("test:y"), Set.of("y"), Optional.of("y"), 1, 0, Optional.empty());
		DishType type = TestPantry.type("tricky", 1, 0, 0,
				List.of(TestPantry.req(Matcher.role("x", "y")), TestPantry.req(Matcher.role("x"))), List.of());

		DishResult result = new CookingResolver(List.of(type), CookingRules.DEFAULT)
				.resolve(List.of(new CookingInput("test:x", x), new CookingInput("test:y", y)));

		assertEquals("dynamic_cooking:tricky", result.item());
	}

	@Test
	void itemMatchersWorkAlongsideRoles() {
		DishType type = TestPantry.type("apple_only", 1, 0, 0, List.of(TestPantry.flavor(Matcher.item("minecraft:apple"))), List.of());
		CookingResolver resolver = new CookingResolver(List.of(type), CookingRules.DEFAULT);

		assertEquals("dynamic_cooking:apple_only", resolver.resolve(inputs("apple")).item());
		assertTrue(resolver.resolve(inputs("melon_slice")).dubious());
	}

	@Test
	void nutritionAddsIngredientsBonusAndVariety() {
		// beef 3 + potato 1 + stew bonus 2 + variety 1 for the second flavor
		assertEquals(7, resolver().resolve(inputs("bowl", "beef", "potato")).nutrition());
	}

	@Test
	void nutritionIsCapped() {
		DishResult result = resolver().resolve(inputs("stick", "golden_carrot", "golden_carrot", "golden_carrot", "golden_carrot"));

		assertEquals(CookingRules.DEFAULT.maxNutrition(), result.nutrition());
	}

	@Test
	void tooManyIngredientsIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve(inputs("wheat", "wheat", "wheat", "wheat", "wheat", "wheat")));
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve(List.of()));
	}
}
