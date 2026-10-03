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
 * @param color      optional {@code 0xRRGGBB} color this ingredient gives the liquid in the pot; containers and seasonings have none
 * @param raw        set when the item is raw, like raw beef; the pot cooks it, crafted dishes keep it raw
 * @param effects    side effects this ingredient always gives, cooked or not, like hunger from rotten flesh
 */
public record IngredientProfile(
		List<String> items,
		Set<String> roles,
		Optional<String> flavor,
		int nutrition,
		float saturation,
		Optional<BuffSource> buff,
		Optional<Integer> color,
		Optional<Raw> raw,
		List<SideEffect> effects
) {
	public IngredientProfile {
		items = List.copyOf(items);
		roles = Set.copyOf(roles);
		effects = List.copyOf(effects);
	}

	/** A cooked or never-cooked ingredient with no side effects. */
	public IngredientProfile(List<String> items, Set<String> roles, Optional<String> flavor, int nutrition, float saturation,
			Optional<BuffSource> buff, Optional<Integer> color) {
		this(items, roles, flavor, nutrition, saturation, buff, color, Optional.empty(), List.of());
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

	/**
	 * How a raw ingredient differs from its cooked version. Eaten raw in a crafted dish it is worth less and may make
	 * the eater sick, unless the dish type allows it raw (fish in a kelp roll).
	 *
	 * @param cooksInto         item id the pot turns it into, e.g. {@code minecraft:cooked_beef}; empty if it can't be cooked
	 * @param nutritionPenalty  food points taken off while raw
	 * @param saturationPenalty saturation taken off while raw
	 * @param effects           side effects while raw, like hunger from raw chicken
	 */
	public record Raw(Optional<String> cooksInto, int nutritionPenalty, float saturationPenalty, List<SideEffect> effects) {
		public Raw {
			effects = List.copyOf(effects);
		}
	}
}
