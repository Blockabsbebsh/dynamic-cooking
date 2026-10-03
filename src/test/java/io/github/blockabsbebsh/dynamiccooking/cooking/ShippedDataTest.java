package io.github.blockabsbebsh.dynamiccooking.cooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Cooks sample recipes against the JSON the mod actually ships, to catch data mistakes without starting the game.
 * The parsing here mirrors the defaults in {@code CookingCodecs}.
 */
class ShippedDataTest {
	private static final Path DATA = Path.of("src/main/resources/data/dynamic_cooking/dynamic_cooking");

	private static final Map<String, IngredientProfile> PROFILES = new HashMap<>();
	private static CookingResolver resolver;

	@BeforeAll
	static void load() throws IOException {
		for (JsonObject json : readAll(DATA.resolve("ingredient"))) {
			IngredientProfile profile = new IngredientProfile(
					strings(json.getAsJsonArray("items")),
					new HashSet<>(strings(json.getAsJsonArray("roles"))),
					json.has("flavor") ? Optional.of(json.get("flavor").getAsString()) : Optional.empty(),
					json.has("nutrition") ? json.get("nutrition").getAsInt() : 0,
					json.has("saturation") ? json.get("saturation").getAsFloat() : 0.0f,
					json.has("buff") ? Optional.of(buff(json.getAsJsonObject("buff"))) : Optional.empty()
			);

			for (String item : profile.items()) {
				IngredientProfile previous = PROFILES.put(item, profile);
				assertEquals(null, previous, item + " has two ingredient profiles");
			}
		}

		List<DishType> types = new ArrayList<>();

		for (JsonObject json : readAll(DATA.resolve("dish_type"))) {
			List<Requirement> requires = new ArrayList<>();

			for (JsonElement element : json.getAsJsonArray("requires")) {
				JsonObject requirement = element.getAsJsonObject();
				requires.add(new Requirement(
						matcher(requirement.getAsJsonObject("match")),
						requirement.has("count") ? requirement.get("count").getAsInt() : 1,
						requirement.has("flavor") && requirement.get("flavor").getAsBoolean()
				));
			}

			List<Matcher> forbids = new ArrayList<>();

			if (json.has("forbids")) {
				json.getAsJsonArray("forbids").forEach(element -> forbids.add(matcher(element.getAsJsonObject())));
			}

			types.add(new DishType(
					json.get("item").getAsString(),
					json.get("priority").getAsInt(),
					json.has("method") ? CookingMethod.byId(json.get("method").getAsString()) : CookingMethod.POT,
					requires,
					forbids,
					json.has("flavor_roles") ? new HashSet<>(strings(json.getAsJsonArray("flavor_roles"))) : DishType.DEFAULT_FLAVOR_ROLES,
					json.has("bonus_nutrition") ? json.get("bonus_nutrition").getAsInt() : 0,
					json.has("bonus_saturation") ? json.get("bonus_saturation").getAsFloat() : 0.0f
			));
		}

		resolver = new CookingResolver(types, CookingRules.DEFAULT);
	}

	@Test
	void everyDishTypeHasADistinctPriority() throws IOException {
		Set<Integer> priorities = new HashSet<>();

		for (JsonObject json : readAll(DATA.resolve("dish_type"))) {
			assertTrue(priorities.add(json.get("priority").getAsInt()), "Duplicate priority in " + json);
		}
	}

	@Test
	void sampleRecipes() {
		assertDish("cake", List.of("carrot"), "wheat", "sugar", "egg", "carrot");
		assertDish("cake", List.of("chorus_fruit"), "wheat", "sugar", "brown_egg", "chorus_fruit");
		assertDish("pie", List.of("apple"), "wheat", "egg", "apple");
		assertDish("pie", List.of("mutton"), "wheat", "egg", "cooked_mutton");
		assertDish("cookies", List.of("sweet_berry"), "wheat", "sugar", "sweet_berries");
		assertCrafted("juice", List.of("melon"), "glass_bottle", "melon_slice");
		assertDish("stew", List.of("beef", "potato"), "bowl", "beef", "potato");
		assertCrafted("salad", List.of("apple", "sweet_berry"), "bowl", "apple", "sweet_berries");
		assertDish("soup", List.of("pumpkin"), "bowl", "pumpkin");
		assertDish("soup", List.of("mushroom"), "bowl", "red_mushroom", "brown_mushroom");
		assertCrafted("sandwich", List.of("porkchop"), "bread", "cooked_porkchop");
		assertCrafted("kelp_roll", List.of("cod"), "dried_kelp", "cod");
		assertCrafted("skewer", List.of("chicken", "carrot"), "stick", "chicken", "carrot");
		assertDish("omelette", List.of("mushroom"), "egg", "brown_mushroom");
		assertDish("roast", List.of("rabbit", "potato"), "rabbit", "baked_potato");
	}

	@Test
	void buffsComeThroughFromData() {
		DishResult cake = cook("wheat", "sugar", "egg", "golden_carrot");

		assertEquals("minecraft:night_vision", cake.buff().orElseThrow().effect());
		assertTrue(craft("stick", "beef", "magma_cream", "rabbit_foot").buff().isEmpty());
	}

	@Test
	void flavorlessMixesAreDubious() {
		assertTrue(cook("sugar", "sugar").dubious());
		assertTrue(cook("bowl", "stick").dubious());
	}

	@Test
	void craftedDishesNeedACraftingTable() {
		assertTrue(cook("bread", "cooked_porkchop").dubious());
		assertTrue(cook("dried_kelp", "cod").dubious());
		assertTrue(resolver.match(CookingMethod.CRAFTING, inputs("wheat", "sugar", "egg", "carrot")).isEmpty());
	}

	private static void assertDish(String dish, List<String> nameFlavors, String... items) {
		assertResult(cook(items), dish, nameFlavors, items);
	}

	private static void assertCrafted(String dish, List<String> nameFlavors, String... items) {
		assertResult(craft(items), dish, nameFlavors, items);
	}

	private static void assertResult(DishResult result, String dish, List<String> nameFlavors, String... items) {

		assertEquals("dynamic_cooking:" + dish, result.item(), () -> List.of(items) + " made " + result.item());
		assertEquals(nameFlavors, result.nameFlavors(), () -> List.of(items) + " named by " + result.nameFlavors());
	}

	private static DishResult cook(String... items) {
		return resolver.resolve(inputs(items));
	}

	private static DishResult craft(String... items) {
		return resolver.match(CookingMethod.CRAFTING, inputs(items)).orElseThrow(() -> new AssertionError(List.of(items) + " crafts nothing"));
	}

	private static List<CookingInput> inputs(String... items) {
		List<CookingInput> inputs = new ArrayList<>();

		for (String name : items) {
			String id = "minecraft:" + name;
			IngredientProfile profile = PROFILES.get(id);
			assertTrue(profile != null, "No ingredient profile for " + id);
			inputs.add(new CookingInput(id, profile));
		}

		return inputs;
	}

	private static Matcher matcher(JsonObject json) {
		return new Matcher(
				json.has("items") ? new HashSet<>(strings(json.getAsJsonArray("items"))) : Set.of(),
				json.has("roles") ? new HashSet<>(strings(json.getAsJsonArray("roles"))) : Set.of()
		);
	}

	private static IngredientProfile.BuffSource buff(JsonObject json) {
		return new IngredientProfile.BuffSource(json.get("effect").getAsString(), json.has("potency") ? json.get("potency").getAsInt() : 1);
	}

	private static List<String> strings(JsonArray array) {
		List<String> out = new ArrayList<>();
		array.forEach(element -> out.add(element.getAsString()));
		return out;
	}

	private static List<JsonObject> readAll(Path dir) throws IOException {
		List<JsonObject> out = new ArrayList<>();

		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
				try (Reader reader = Files.newBufferedReader(file)) {
					out.add(JsonParser.parseReader(reader).getAsJsonObject());
				}
			}
		}

		return out;
	}
}
