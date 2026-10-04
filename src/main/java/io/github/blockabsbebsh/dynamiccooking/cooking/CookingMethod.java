package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.Locale;

/**
 * Where a dish is made. Each dish type picks one in its data with {@code "method"}.
 */
public enum CookingMethod {
	/** Cooked in the cooking pot over heat. Anything the pot can't place becomes the fallback dish. */
	POT,
	/** Put together on a crafting table, one ingredient per slot. No fallback: an unknown mix just doesn't craft. */
	CRAFTING,
	/**
	 * Made by leaving another pot dish on the heat, like stew from soup, never straight from ingredients. Its
	 * requirements still describe what it takes, for the guide.
	 */
	SIMMER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static CookingMethod byId(String id) {
		return valueOf(id.toUpperCase(Locale.ROOT));
	}
}
