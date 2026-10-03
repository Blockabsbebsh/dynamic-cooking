package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.List;
import java.util.Optional;

/**
 * Mixes the colors of the ingredients in the pot into one liquid color.
 */
public final class PotColors {
	/** Plain water, for a pot holding only ingredients without a color, like a bowl. */
	public static final int WATER = 0x3F76E4;

	private PotColors() {
	}

	/**
	 * Averages the colors of the given ingredients, each counted once per time it was added.
	 */
	public static int mix(List<IngredientProfile> ingredients) {
		int red = 0;
		int green = 0;
		int blue = 0;
		int count = 0;

		for (IngredientProfile ingredient : ingredients) {
			Optional<Integer> color = ingredient.color();

			if (color.isPresent()) {
				red += (color.get() >> 16) & 0xFF;
				green += (color.get() >> 8) & 0xFF;
				blue += color.get() & 0xFF;
				count++;
			}
		}

		if (count == 0) {
			return WATER;
		}

		return (red / count) << 16 | (green / count) << 8 | (blue / count);
	}
}
