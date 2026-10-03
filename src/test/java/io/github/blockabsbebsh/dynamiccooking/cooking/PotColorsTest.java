package io.github.blockabsbebsh.dynamiccooking.cooking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PotColorsTest {
	@Test
	void colorsAverage() {
		assertEquals(0x7F4020, PotColors.mix(List.of(colored(0xFF0000), colored(0x008040))));
	}

	@Test
	void ingredientsWithoutAColorAreIgnored() {
		assertEquals(0x123456, PotColors.mix(List.of(colored(0x123456), plain())));
	}

	@Test
	void onlyPlainIngredientsGiveWater() {
		assertEquals(PotColors.WATER, PotColors.mix(List.of(plain())));
		assertEquals(PotColors.WATER, PotColors.mix(List.of()));
	}

	private static IngredientProfile colored(int color) {
		return new IngredientProfile(List.of("test:x"), Set.of("produce"), Optional.empty(), 0, 0, Optional.empty(), Optional.of(color));
	}

	private static IngredientProfile plain() {
		return new IngredientProfile(List.of("test:bowl"), Set.of("bowl"), Optional.empty(), 0, 0, Optional.empty(), Optional.empty());
	}
}
