package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides what a set of ingredients cooks into.
 *
 * <p>Only dish types made with the given {@link CookingMethod} are considered. They are tried in priority order
 * and the first one whose requirements can all be filled wins.
 */
public final class CookingResolver {
	private static final Comparator<DishType> ORDER = Comparator.comparingInt(DishType::priority).thenComparing(DishType::item);

	private final List<DishType> dishTypes;
	private final CookingRules rules;

	public CookingResolver(Collection<DishType> dishTypes, CookingRules rules) {
		this.dishTypes = dishTypes.stream().sorted(ORDER).toList();
		this.rules = rules;
	}

	public CookingRules rules() {
		return rules;
	}

	/**
	 * Cooks in the pot. If no pot dish fits, the result is the fallback dish from {@link CookingRules}.
	 */
	public DishResult resolve(List<CookingInput> inputs) {
		if (inputs.isEmpty()) {
			throw new IllegalArgumentException("Nothing to cook");
		}

		if (inputs.size() > rules.maxIngredients()) {
			throw new IllegalArgumentException("At most " + rules.maxIngredients() + " ingredients, got " + inputs.size());
		}

		return match(CookingMethod.POT, inputs).orElseGet(() -> fallback(inputs));
	}

	/**
	 * Finds the dish these ingredients make with the given method, or nothing if no dish type fits.
	 */
	public Optional<DishResult> match(CookingMethod method, List<CookingInput> inputs) {
		if (inputs.isEmpty() || inputs.size() > rules.maxIngredients()) {
			return Optional.empty();
		}

		for (DishType type : dishTypes) {
			if (type.method() != method) {
				continue;
			}

			Optional<DishResult> result = tryCook(type, inputs);

			if (result.isPresent()) {
				return result;
			}
		}

		return Optional.empty();
	}

	private Optional<DishResult> tryCook(DishType type, List<CookingInput> inputs) {
		for (CookingInput input : inputs) {
			for (Matcher forbidden : type.forbids()) {
				if (forbidden.matches(input)) {
					return Optional.empty();
				}
			}
		}

		List<Requirement> slots = new ArrayList<>();

		for (Requirement requirement : type.requires()) {
			for (int i = 0; i < requirement.count(); i++) {
				slots.add(requirement);
			}
		}

		Requirement[] assignment = new Requirement[inputs.size()];

		if (!assign(slots, 0, inputs, assignment)) {
			return Optional.empty();
		}

		Map<String, Integer> flavorCounts = new LinkedHashMap<>();
		int nutrition = type.bonusNutrition();
		float saturation = type.bonusSaturation();

		for (int i = 0; i < inputs.size(); i++) {
			IngredientProfile profile = inputs.get(i).profile();
			nutrition += profile.nutrition();
			saturation += profile.saturation();

			if (givesFlavor(type, profile, assignment[i]) && profile.flavor().isPresent()) {
				flavorCounts.merge(profile.flavor().get(), 1, Integer::sum);
			}
		}

		// Stable sort keeps first-added order between flavors with the same count.
		List<Map.Entry<String, Integer>> ranked = new ArrayList<>(flavorCounts.entrySet());
		ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

		List<String> flavors = ranked.stream().map(Map.Entry::getKey).toList();
		List<String> nameFlavors = ranked.stream()
				.filter(entry -> entry.getValue().equals(ranked.getFirst().getValue()))
				.limit(rules.nameFlavorCount())
				.map(Map.Entry::getKey)
				.toList();

		nutrition += rules.varietyBonus() * Math.max(0, flavors.size() - 1);
		nutrition = Math.clamp(nutrition, 1, rules.maxNutrition());
		saturation = Math.clamp(saturation, 0.0f, (float) rules.maxNutrition());

		return Optional.of(new DishResult(
				type.item(),
				itemIds(inputs),
				flavors,
				nameFlavors,
				nutrition,
				saturation,
				resolveBuff(inputs),
				false,
				type.liquid()
		));
	}

	private static boolean givesFlavor(DishType type, IngredientProfile profile, Requirement filled) {
		if (filled != null) {
			return filled.flavor();
		}

		for (String role : type.flavorRoles()) {
			if (profile.hasRole(role)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Fills each slot with a different ingredient, backtracking when a greedy choice blocks a later slot.
	 * The pot holds at most a handful of ingredients, so the search stays tiny.
	 */
	private static boolean assign(List<Requirement> slots, int slot, List<CookingInput> inputs, Requirement[] assignment) {
		if (slot == slots.size()) {
			return true;
		}

		Requirement requirement = slots.get(slot);

		for (int i = 0; i < inputs.size(); i++) {
			if (assignment[i] == null && requirement.matcher().matches(inputs.get(i))) {
				assignment[i] = requirement;

				if (assign(slots, slot + 1, inputs, assignment)) {
					return true;
				}

				assignment[i] = null;
			}
		}

		return false;
	}

	/**
	 * BotW rules: buffs of the same effect add up into a longer, then stronger buff; two different effects cancel out.
	 */
	Optional<DishResult.Buff> resolveBuff(List<CookingInput> inputs) {
		Set<String> effects = new HashSet<>();
		int potency = 0;

		for (CookingInput input : inputs) {
			if (input.profile().buff().isPresent()) {
				IngredientProfile.BuffSource buff = input.profile().buff().get();
				effects.add(buff.effect());
				potency += buff.potency();
			}
		}

		if (effects.size() != 1 || potency <= 0) {
			return Optional.empty();
		}

		int duration = Math.min(rules.buffMaxTicks(), rules.buffBaseTicks() + rules.buffTicksPerPotency() * (potency - 1));
		int amplifier = potency >= rules.buffLevelTwoPotency() ? 1 : 0;

		return Optional.of(new DishResult.Buff(effects.iterator().next(), amplifier, duration));
	}

	private DishResult fallback(List<CookingInput> inputs) {
		return new DishResult(
				rules.fallbackItem(),
				itemIds(inputs),
				List.of(),
				List.of(),
				rules.fallbackNutrition(),
				rules.fallbackSaturation(),
				Optional.empty(),
				true,
				false
		);
	}

	private static List<String> itemIds(List<CookingInput> inputs) {
		return inputs.stream().map(CookingInput::itemId).toList();
	}
}
