package io.github.blockabsbebsh.dynamiccooking.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import io.github.blockabsbebsh.dynamiccooking.cooking.CookingMethod;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishResult;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.cooking.Matcher;
import io.github.blockabsbebsh.dynamiccooking.cooking.Requirement;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * How a dish relates to the others, found by cooking variations of a simple recipe for it with the real rules: adding an
 * ingredient of each kind, leaving out what a requirement asks for, or using another kind instead. So the guide can say
 * that water and vegetables make soup rather than stew, and only call an ingredient wrong when it really spoils the dish.
 *
 * @param probed  false when no simple recipe for the dish could be found, so nothing below was tested
 * @param related the other dishes a change makes, by item id, with how to get each
 * @param spoils  roles whose ingredients make the dish fail rather than turn into another one
 */
record DishRelations(boolean probed, Map<String, List<Component>> related, Set<String> spoils) {
	static final DishRelations UNKNOWN = new DishRelations(false, Map.of(), Set.of());

	/**
	 * @param examples one ingredient standing in for each role
	 * @param items    every item a matcher accepts, in the guide's order
	 * @param label    how the guide names a matcher, like "Meat/Fish or Mushroom"
	 */
	static DishRelations of(CookingService cooking, DishType type, Map<String, ItemStack> examples,
			Function<Matcher, List<ItemStack>> items, Function<Matcher, Component> label) {
		// Each requirement may take several kinds of ingredient, like a roast of meat or of mushrooms, and they can relate
		// to different dishes: water turns one into stew, the other into soup. So every kind gets its own simple recipe.
		List<List<ItemStack>> choices = new ArrayList<>();

		for (Requirement requirement : type.requires()) {
			List<ItemStack> kinds = requirement.matcher().roles().stream().sorted().filter(examples::containsKey).map(examples::get).distinct().toList();

			if (kinds.isEmpty()) {
				// Only named items: use the first.
				List<ItemStack> accepted = items.apply(requirement.matcher());

				if (accepted.isEmpty()) {
					return UNKNOWN;
				}

				kinds = List.of(accepted.getFirst());
			}

			choices.add(kinds);
		}

		Map<String, Map<String, Set<String>>> found = new LinkedHashMap<>();
		Set<String> spoils = null;

		for (List<ItemStack> picks : combinations(choices)) {
			List<List<ItemStack>> parts = new ArrayList<>();

			for (int i = 0; i < picks.size(); i++) {
				parts.add(Collections.nCopies(type.requires().get(i).count(), picks.get(i)));
			}

			if (!type.item().equals(make(cooking, type.method(), flatten(parts, -1, null)).orElse(""))) {
				continue;
			}

			Set<String> spoilsHere = new LinkedHashSet<>();

			// One more ingredient of each kind.
			examples.forEach((role, example) -> {
				List<ItemStack> recipe = flatten(parts, -1, null);
				recipe.add(example);
				Optional<String> made = make(cooking, type.method(), recipe);

				if (made.isEmpty()) {
					spoilsHere.add(role);
				} else if (!made.get().equals(type.item())) {
					note(found, made.get(), "with", role);
				}
			});

			// Only what spoils every version of the dish counts as spoiling it.
			if (spoils == null) {
				spoils = spoilsHere;
			} else {
				spoils.retainAll(spoilsHere);
			}

			for (int i = 0; i < parts.size(); i++) {
				Matcher matcher = type.requires().get(i).matcher();
				String what = label.apply(matcher).getString();

				// Without what this requirement asks for.
				make(cooking, type.method(), flatten(parts, i, null))
						.filter(made -> !made.equals(type.item()))
						.ifPresent(made -> note(found, made, "without", what));

				// Another kind of ingredient in its place.
				for (Map.Entry<String, ItemStack> example : examples.entrySet()) {
					if (matcher.roles().contains(example.getKey())) {
						continue;
					}

					make(cooking, type.method(), flatten(parts, i, example.getValue()))
							.filter(made -> !made.equals(type.item()))
							.ifPresent(made -> note(found, made, "instead:" + what, example.getKey()));
				}
			}
		}

		if (spoils == null) {
			return UNKNOWN;
		}

		Map<String, Set<String>> members = members(cooking);
		Map<String, List<Component>> related = new LinkedHashMap<>();
		found.forEach((dish, ways) -> {
			Set<String> leftOut = ways.getOrDefault("without", Set.of());
			List<Component> how = new ArrayList<>();
			ways.forEach((way, roles) -> {
				// Leaving something out already says it; swapping it for anything else adds nothing.
				if (way.startsWith("instead:") && leftOut.contains(way.substring("instead:".length()))) {
					return;
				}

				how.add(describe(way, way.equals("without") ? roles : broadest(roles, members)));
			});
			related.put(dish, how);
		});

		return new DishRelations(true, related, spoils);
	}

	/** Every item id with each role. */
	private static Map<String, Set<String>> members(CookingService cooking) {
		Map<String, Set<String>> out = new LinkedHashMap<>();
		cooking.profiles().forEach(profile -> profile.roles().forEach(role -> out.computeIfAbsent(role, key -> new LinkedHashSet<>()).addAll(profile.items())));
		return out;
	}

	/** Leaves out a role when another one in the list already covers all its ingredients, like fresh fruit by fruit/veg. */
	private static Set<String> broadest(Set<String> roles, Map<String, Set<String>> members) {
		Set<String> out = new LinkedHashSet<>();

		for (String role : roles) {
			Set<String> mine = members.getOrDefault(role, Set.of());
			boolean covered = roles.stream().anyMatch(other -> !other.equals(role)
					&& members.getOrDefault(other, Set.of()).containsAll(mine)
					&& (!mine.containsAll(members.getOrDefault(other, Set.of())) || other.compareTo(role) < 0));

			if (!covered) {
				out.add(role);
			}
		}

		return out;
	}

	/** Every way to pick one item from each list, at most 16, so data packs with wide requirements stay quick. */
	private static List<List<ItemStack>> combinations(List<List<ItemStack>> choices) {
		List<List<ItemStack>> out = new ArrayList<>(List.of(List.of()));

		for (List<ItemStack> options : choices) {
			List<List<ItemStack>> next = new ArrayList<>();

			for (List<ItemStack> partial : out) {
				for (ItemStack option : options) {
					if (next.size() < 16) {
						List<ItemStack> longer = new ArrayList<>(partial);
						longer.add(option);
						next.add(longer);
					}
				}
			}

			out = next;
		}

		return out;
	}

	/**
	 * Adds what tasting can't find: the dish this one simmers into on the heat, and for a simmered dish, the ones it
	 * comes from.
	 */
	DishRelations withSimmering(DishType type, List<DishType> all) {
		Map<String, List<Component>> out = new LinkedHashMap<>(related);
		type.simmersInto().ifPresent(into -> out.computeIfAbsent(into, key -> new ArrayList<>())
				.add(Component.translatable("guide.dynamic_cooking.related.simmers")));

		for (DishType other : all) {
			if (other.simmersInto().filter(type.item()::equals).isPresent()) {
				out.computeIfAbsent(other.item(), key -> new ArrayList<>()).add(Component.translatable("guide.dynamic_cooking.related.simmered"));
			}
		}

		return new DishRelations(probed, out, spoils);
	}

	private static void note(Map<String, Map<String, Set<String>>> found, String dish, String way, String what) {
		found.computeIfAbsent(dish, key -> new LinkedHashMap<>()).computeIfAbsent(way, key -> new LinkedHashSet<>()).add(what);
	}

	/** "with Meat/Fish or Mushroom", "without Water Bucket" or "Fruit/Veg instead of Meat/Fish". */
	private static Component describe(String way, Set<String> what) {
		if (way.equals("without")) {
			return Component.translatable("guide.dynamic_cooking.related.without", what.iterator().next());
		}

		List<Component> roles = what.stream().map(GuideScreen::roleName).toList();
		Component kinds = GuideScreen.joinOr(roles);

		if (way.equals("with")) {
			return Component.translatable("guide.dynamic_cooking.related.with", kinds);
		}

		return Component.translatable("guide.dynamic_cooking.related.instead", kinds, way.substring("instead:".length()));
	}

	/** The recipe with one requirement's items left out (replacement null) or swapped for one other ingredient. */
	private static List<ItemStack> flatten(List<List<ItemStack>> parts, int skip, ItemStack replacement) {
		List<ItemStack> out = new ArrayList<>();

		for (int i = 0; i < parts.size(); i++) {
			if (i != skip) {
				out.addAll(parts.get(i));
			} else if (replacement != null) {
				out.add(replacement);
			}
		}

		return out;
	}

	/** The dish a recipe makes, or empty when it makes Dubious Mush or, on a crafting table, nothing. */
	private static Optional<String> make(CookingService cooking, CookingMethod method, List<ItemStack> recipe) {
		if (recipe.isEmpty()) {
			return Optional.empty();
		}

		if (method == CookingMethod.CRAFTING) {
			return cooking.match(method, recipe).map(DishResult::item);
		}

		try {
			DishResult result = cooking.resolve(recipe);
			return result.dubious() ? Optional.empty() : Optional.of(result.item());
		} catch (IllegalArgumentException notAnIngredient) {
			return Optional.empty();
		}
	}

	/**
	 * One ingredient for each role the dish types mention. It prefers ones with no other role, so swapping it in only
	 * changes one thing (cocoa is sweet but also produce), then ones that need no cooking.
	 */
	static Map<String, ItemStack> examples(CookingService cooking, Function<String, ItemStack> stack) {
		Set<String> used = new LinkedHashSet<>();

		for (DishType type : cooking.dishTypes()) {
			type.requires().forEach(requirement -> used.addAll(requirement.matcher().roles()));
			type.forbids().forEach(matcher -> used.addAll(matcher.roles()));
			used.addAll(type.flavorRoles());
		}

		Map<String, ItemStack> out = new TreeMap<>();
		List<IngredientProfile> profiles = cooking.profiles().stream()
				.sorted(Comparator.comparingInt((IngredientProfile profile) -> profile.roles().size()).thenComparing(profile -> profile.raw().isPresent()))
				.toList();

		for (IngredientProfile profile : profiles) {
			for (String role : profile.roles()) {
				if (used.contains(role) && !out.containsKey(role)) {
					profile.items().stream().map(stack).filter(item -> !item.isEmpty()).findFirst().ifPresent(item -> out.put(role, item));
				}
			}
		}

		return out;
	}
}
