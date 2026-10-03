package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What an ingredient means to the cooking pot. Loaded from {@code data/<namespace>/dynamic_cooking/ingredient/*.json}.
 *
 * @param items      item ids this profile applies to, e.g. {@code minecraft:carrot}
 * @param roles      roles the ingredient can fill in a dish type, e.g. {@code produce}, {@code flour}, {@code bowl}
 * @param flavor     flavor key used for dish names and texture palettes; empty for flavorless ingredients like sugar
 * @param nutrition  food points this ingredient adds to a dish
 * @param saturation saturation points this ingredient adds to a dish
 * @param buff       optional buff this ingredient adds
 */
public record IngredientProfile(
		List<String> items,
		Set<String> roles,
		Optional<String> flavor,
		int nutrition,
		float saturation,
		Optional<BuffSource> buff
) {
	public IngredientProfile {
		items = List.copyOf(items);
		roles = Set.copyOf(roles);
	}

	public boolean hasRole(String role) {
		return roles.contains(role);
	}

	/**
	 * A buff an ingredient contributes.
	 *
	 * @param effect  effect id, e.g. {@code minecraft:night_vision}
	 * @param potency how much this ingredient counts towards duration and strength
	 */
	public record BuffSource(String effect, int potency) {
	}
}
