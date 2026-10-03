package io.github.blockabsbebsh.dynamiccooking.cooking;

import static io.github.blockabsbebsh.dynamiccooking.cooking.TestPantry.inputs;
import static io.github.blockabsbebsh.dynamiccooking.cooking.TestPantry.resolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuffRulesTest {
	private static final CookingRules RULES = CookingRules.DEFAULT;

	@Test
	void oneBuffIngredientGivesTheBaseBuff() {
		DishResult.Buff buff = resolver().resolve(inputs("wheat", "sugar", "egg", "golden_carrot")).buff().orElseThrow();

		assertEquals("minecraft:night_vision", buff.effect());
		assertEquals(0, buff.amplifier());
		assertEquals(RULES.buffBaseTicks(), buff.durationTicks());
	}

	@Test
	void moreOfTheSameBuffLastsLonger() {
		DishResult.Buff buff = resolver().resolve(inputs("stick", "golden_carrot", "golden_carrot")).buff().orElseThrow();

		assertEquals(RULES.buffBaseTicks() + RULES.buffTicksPerPotency(), buff.durationTicks());
		assertEquals(0, buff.amplifier());
	}

	@Test
	void enoughOfTheSameBuffBecomesLevelTwo() {
		DishResult.Buff buff = resolver().resolve(inputs("stick", "golden_carrot", "golden_carrot", "golden_carrot", "golden_carrot")).buff().orElseThrow();

		assertEquals(1, buff.amplifier());
	}

	@Test
	void differentBuffsCancelOut() {
		assertTrue(resolver().resolve(inputs("stick", "golden_carrot", "magma_cream")).buff().isEmpty());
	}

	@Test
	void chorusFruitCakeTeleports() {
		DishResult result = resolver().resolve(inputs("wheat", "sugar", "egg", "chorus_fruit"));

		assertEquals("dynamic_cooking:cake", result.item());
		assertEquals("dynamic_cooking:teleport", result.buff().orElseThrow().effect());
	}
}
