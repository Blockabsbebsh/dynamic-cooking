package io.github.blockabsbebsh.dynamiccooking.cooking;

/**
 * One slot a dish type needs filled.
 *
 * @param matcher which ingredients can fill it
 * @param count   how many ingredients it takes
 * @param flavor  whether the ingredients filling it name and colour the dish
 */
public record Requirement(Matcher matcher, int count, boolean flavor) {
	public Requirement {
		if (count < 1) {
			throw new IllegalArgumentException("count must be at least 1, got " + count);
		}
	}
}
