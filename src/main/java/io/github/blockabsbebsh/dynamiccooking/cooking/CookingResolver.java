package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides what a set of ingredients cooks into.
 *
 * <p>Only dish types made with the given {@link CookingMethod} are considered. They are tried in priority order
 * and the first one whose requirements can all be filled wins. Ingredients left over after the requirements are filled
 * must suit the dish: a flavor role, a role the dish already asks for, or an extra any dish takes, like a seasoning.
 * So a stick next to bread makes neither a skewer nor a sandwich.
 *
 * <p>The pot cooks raw ingredients into their cooked versions first. Crafted dishes keep them raw, which costs food
 * points and may add side effects, unless the dish type allows them raw.
 */
public final class CookingResolver {
	private static final Comparator<DishType> ORDER = Comparator.comparingInt(DishType::priority).thenComparing(DishType::item);

	private final List<DishType> dishTypes;
	private final CookingRules rules;
	private final Function<String, Optional<IngredientProfile>> profiles;

	/**
	 * @param profiles looks up the profile of an item id, used to find what a raw ingredient cooks into
	 */
	public CookingResolver(Collection<DishType> dishTypes, CookingRules rules, Function<String, Optional<IngredientProfile>> profiles) {
		this.dishTypes = dishTypes.stream().sorted(ORDER).toList();
		this.rules = rules;
		this.profiles = profiles;
	}

	/** A resolver that can't look up cooked versions; raw ingredients cooked in the pot keep their own profile. */
	public CookingResolver(Collection<DishType> dishTypes, CookingRules rules) {
		this(dishTypes, rules, id -> Optional.empty());
	}

	public CookingRules rules() {
		return rules;
	}

	/** Every dish type, in the order they are tried. */
	public List<DishType> dishTypes() {
		return dishTypes;
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

		List<CookingInput> cooked = cooked(inputs);
		return match(CookingMethod.POT, cooked).orElseGet(() -> fallback(cooked));
	}

	/** Swaps every raw ingredient for its cooked version, when there is one with a profile. */
	public List<CookingInput> cooked(List<CookingInput> inputs) {
		return inputs.stream().map(this::cooked).toList();
	}

	private CookingInput cooked(CookingInput input) {
		return input.profile().raw()
				.flatMap(IngredientProfile.Raw::cooksInto)
				.flatMap(id -> profiles.apply(id).map(profile -> new CookingInput(id, profile)))
				.orElse(input);
	}

	/**
	 * What the pot makes from copies of one raw ingredient and nothing else: its cooked version, one for each copy, the way
	 * a furnace would. So raw potatoes come out as baked potatoes rather than Dubious Mush. Empty for anything else.
	 */
	public Optional<String> cookedAlone(List<CookingInput> inputs) {
		if (inputs.isEmpty() || inputs.stream().map(CookingInput::itemId).distinct().count() != 1) {
			return Optional.empty();
		}

		return inputs.getFirst().profile().raw().flatMap(IngredientProfile.Raw::cooksInto);
	}

	/** Whether cooking these ingredients again would cook any of them, like a skewer made with raw chicken. */
	public boolean canRecook(List<CookingInput> inputs) {
		return !inputs.equals(cooked(inputs));
	}

	/**
	 * Cooks a finished dish again: its raw ingredients become cooked and it is rebuilt as the same kind of dish.
	 * Empty when nothing in it can be cooked, or the cooked ingredients no longer make that dish.
	 */
	public Optional<DishResult> recook(String item, List<CookingInput> inputs) {
		if (!canRecook(inputs)) {
			return Optional.empty();
		}

		List<CookingInput> cooked = cooked(inputs);

		for (CookingMethod method : CookingMethod.values()) {
			Optional<DishResult> result = match(method, cooked).filter(dish -> dish.item().equals(item));

			if (result.isPresent()) {
				return result;
			}
		}

		return Optional.empty();
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

			Optional<DishResult> result = tryCook(type, method, inputs);

			if (result.isPresent()) {
				return result;
			}
		}

		return Optional.empty();
	}

	private Optional<DishResult> tryCook(DishType type, CookingMethod method, List<CookingInput> inputs) {
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

		for (int i = 0; i < inputs.size(); i++) {
			if (assignment[i] == null && !suitsAsExtra(type, inputs.get(i))) {
				return Optional.empty();
			}
		}

		Map<String, Integer> flavorCounts = new LinkedHashMap<>();
		Map<String, Integer> flavorColors = new HashMap<>();
		Set<String> rawFlavors = new HashSet<>();
		List<SideEffect> sideEffects = new ArrayList<>();
		boolean heldRaw = false;
		int nutrition = type.bonusNutrition();
		float saturation = type.bonusSaturation();

		for (int i = 0; i < inputs.size(); i++) {
			CookingInput input = inputs.get(i);
			IngredientProfile profile = input.profile();
			nutrition += profile.nutrition();
			saturation += profile.saturation();
			sideEffects.addAll(profile.effects());

			if (profile.roles().stream().noneMatch(rules.containerRoles()::contains)) {
				nutrition += rules.perIngredientNutrition();
				saturation += rules.perIngredientSaturation();
			}

			// The pot cooks everything, so only crafted dishes can still be raw.
			boolean raw = method == CookingMethod.CRAFTING && profile.raw().isPresent();

			if (raw && type.rawOk().stream().noneMatch(matcher -> matcher.matches(input))) {
				IngredientProfile.Raw penalty = profile.raw().get();
				nutrition -= penalty.nutritionPenalty();
				saturation -= penalty.saturationPenalty();
				sideEffects.addAll(penalty.effects());
				heldRaw = true;
			}

			if (givesFlavor(type, profile, assignment[i]) && profile.flavor().isPresent()) {
				flavorCounts.merge(profile.flavor().get(), 1, Integer::sum);
				profile.color().ifPresent(color -> flavorColors.putIfAbsent(profile.flavor().get(), color));

				if (raw) {
					rawFlavors.add(profile.flavor().get());
				}
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
		// A batch of several, like kelp rolls, shares the food out; rounding up so no piece is worth nothing.
		nutrition = Math.clamp(Math.ceilDiv(nutrition, type.makes()), 1, rules.maxNutrition());
		saturation = Math.clamp(saturation / type.makes(), 0.0f, (float) rules.maxNutrition());

		return Optional.of(new DishResult(
				type.item(),
				itemIds(inputs),
				flavors,
				flavors.stream().map(flavor -> flavorColors.getOrDefault(flavor, DishResult.NO_COLOR)).toList(),
				flavors.stream().map(rawFlavors::contains).toList(),
				nameFlavors,
				nutrition,
				saturation,
				resolveBuff(inputs),
				combineEffects(sideEffects, type.makes()),
				heldRaw,
				false,
				type.liquid(),
				type.servedWith(),
				type.servings(),
				type.makes()
		));
	}

	/** Whether an ingredient no requirement took still belongs in the dish. */
	private boolean suitsAsExtra(DishType type, CookingInput input) {
		for (String role : input.profile().roles()) {
			if (type.flavorRoles().contains(role) || rules.extraRoles().contains(role)) {
				return true;
			}
		}

		return type.requires().stream().anyMatch(requirement -> requirement.matcher().matches(input));
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

	/**
	 * Every ingredient's effects carry over in full, so a dish with an enchanted golden apple gives all of that apple's effects.
	 * Copies of the same effect add their durations, and a batch like kelp rolls shares the duration out between its pieces.
	 */
	static List<SideEffect> combineEffects(List<SideEffect> effects, int makes) {
		Map<SideEffect, Integer> durations = new LinkedHashMap<>();

		for (SideEffect effect : effects) {
			durations.merge(new SideEffect(effect.effect(), effect.amplifier(), 0, effect.chance()), effect.durationTicks(), Integer::sum);
		}

		List<SideEffect> combined = new ArrayList<>();
		durations.forEach((effect, ticks) -> combined.add(
				new SideEffect(effect.effect(), effect.amplifier(), Math.max(20, Math.ceilDiv(ticks, makes)), effect.chance())));
		return combined;
	}

	private DishResult fallback(List<CookingInput> inputs) {
		return new DishResult(
				rules.fallbackItem(),
				itemIds(inputs),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				rules.fallbackNutrition(),
				rules.fallbackSaturation(),
				Optional.empty(),
				combineEffects(inputs.stream().flatMap(input -> input.profile().effects().stream()).toList(), 1),
				false,
				true,
				false,
				Optional.empty(),
				1,
				1
		);
	}

	private static List<String> itemIds(List<CookingInput> inputs) {
		return inputs.stream().map(CookingInput::itemId).toList();
	}
}
