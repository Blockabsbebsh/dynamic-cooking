package io.github.blockabsbebsh.dynamiccooking.cooking;

import static io.github.blockabsbebsh.dynamiccooking.cooking.TestPantry.craft;
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
		DishResult result = resolver().resolve(inputs("water_bucket", "beef", "potato"));

		assertEquals("dynamic_cooking:stew", result.item());
		assertEquals(List.of("beef", "potato"), result.nameFlavors());
	}

	@Test
	void runnyDishesAreServedWithABowl() {
		DishResult stew = resolver().resolve(inputs("water_bucket", "beef", "potato"));

		assertEquals(Optional.of("minecraft:bowl"), stew.servedWith());
		assertEquals(1, stew.servings());
		assertTrue(resolver().resolve(inputs("wheat", "sugar", "egg", "carrot")).servedWith().isEmpty());
		assertTrue(resolver().resolve(inputs("sugar", "sugar")).servedWith().isEmpty());
	}

	@Test
	void waterTellsAStewFromARoast() {
		assertEquals("dynamic_cooking:stew", resolver().resolve(inputs("water_bucket", "beef", "carrot")).item());
		assertEquals("dynamic_cooking:roast", resolver().resolve(inputs("beef", "carrot")).item());
	}

	@Test
	void roastsTakeAnyTwoOfMeatVegOrMushroom() {
		DishResult veggie = resolver().resolve(inputs("carrot", "brown_mushroom"));

		assertEquals("dynamic_cooking:roast", veggie.item());
		assertEquals(List.of("carrot", "mushroom"), veggie.nameFlavors());
		assertEquals("dynamic_cooking:roast", resolver().resolve(inputs("potato", "potato")).item());
		assertEquals("dynamic_cooking:roast", resolver().resolve(inputs("beef", "beef")).item());
		assertTrue(resolver().resolve(inputs("carrot")).dubious());
	}

	@Test
	void theMostCommonFlavorNamesTheDish() {
		DishResult result = craft("stick", "carrot", "beef", "beef");

		assertEquals("dynamic_cooking:skewer", result.item());
		assertEquals(List.of("beef", "carrot"), result.flavors());
		assertEquals(List.of("beef"), result.nameFlavors());
	}

	@Test
	void eachFlavorCarriesItsIngredientColor() {
		assertEquals(List.of(0x9A4A33, 0xE58A1F), craft("stick", "carrot", "beef", "beef").flavorColors());
		assertEquals(List.of(DishResult.NO_COLOR), craft("stick", "potato").flavorColors());
	}

	@Test
	void forbiddenIngredientsSkipADishType() {
		assertEquals("dynamic_cooking:soup", resolver().resolve(inputs("water_bucket", "carrot")).item());
		assertEquals("dynamic_cooking:salad", craft("bowl", "apple", "melon_slice").item());
		assertEquals("dynamic_cooking:stew", resolver().resolve(inputs("water_bucket", "apple", "melon_slice", "beef")).item());
	}

	@Test
	void leftoversMustSuitTheDish() {
		assertEquals("dynamic_cooking:skewer", craft("stick", "melon_slice").item());
		assertTrue(resolver().match(CookingMethod.CRAFTING, inputs("stick", "melon_slice", "bowl")).isEmpty());
		assertEquals("dynamic_cooking:skewer", craft("stick", "stick", "beef").item());
		assertEquals("dynamic_cooking:skewer", craft("stick", "beef", "magma_cream").item());
		assertEquals("dynamic_cooking:cake", resolver().resolve(inputs("wheat", "sugar", "sugar", "egg", "carrot")).item());
		assertTrue(resolver().resolve(inputs("wheat", "egg", "carrot", "stick")).dubious());
	}

	@Test
	void craftedDishesAreNotCookedInThePot() {
		assertTrue(resolver().resolve(inputs("stick", "beef")).dubious());
		assertEquals("dynamic_cooking:soup", resolver().resolve(inputs("water_bucket", "apple", "melon_slice")).item());
	}

	@Test
	void potDishesAreNotCrafted() {
		assertTrue(resolver().match(CookingMethod.CRAFTING, inputs("wheat", "sugar", "egg", "carrot")).isEmpty());
		assertTrue(resolver().match(CookingMethod.CRAFTING, inputs("sugar", "sugar")).isEmpty());
	}

	@Test
	void craftingRejectsTooManyIngredientsQuietly() {
		assertTrue(resolver().match(CookingMethod.CRAFTING, inputs("stick", "beef", "beef", "beef", "beef", "beef")).isEmpty());
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
		IngredientProfile x = new IngredientProfile(List.of("test:x"), Set.of("x"), Optional.of("x"), 1, 0, Optional.empty(), Optional.empty());
		IngredientProfile y = new IngredientProfile(List.of("test:y"), Set.of("y"), Optional.of("y"), 1, 0, Optional.empty(), Optional.empty());
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
		// beef 3 + potato 1 + stew bonus 2 + variety 1 for the second flavor; the water bucket only holds it, so no extra for a third ingredient
		assertEquals(7, resolver().resolve(inputs("water_bucket", "beef", "potato")).nutrition());
		// + carrot 3 + 1 for the third ingredient + variety 1 for the third flavor
		assertEquals(12, resolver().resolve(inputs("water_bucket", "beef", "potato", "carrot")).nutrition());
	}

	@Test
	void nutritionIsCapped() {
		DishResult result = craft("stick", "golden_carrot", "golden_carrot", "golden_carrot", "golden_carrot");

		assertEquals(CookingRules.DEFAULT.maxNutrition(), result.nutrition());
	}

	@Test
	void tooManyIngredientsIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve(inputs("wheat", "wheat", "wheat", "wheat", "wheat", "wheat")));
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve(List.of()));
	}
}
