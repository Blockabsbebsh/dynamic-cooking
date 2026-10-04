package io.github.blockabsbebsh.dynamiccooking.cooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
					json.has("buff") ? Optional.of(buff(json.getAsJsonObject("buff"))) : Optional.empty(),
					json.has("color") ? Optional.of(Integer.parseInt(json.get("color").getAsString().substring(1), 16)) : Optional.empty(),
					json.has("raw") ? Optional.of(raw(json.getAsJsonObject("raw"))) : Optional.empty(),
					effects(json)
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

			List<Matcher> forbids = matchers(json, "forbids");

			types.add(new DishType(
					json.get("item").getAsString(),
					json.get("priority").getAsInt(),
					json.has("method") ? CookingMethod.byId(json.get("method").getAsString()) : CookingMethod.POT,
					requires,
					forbids,
					json.has("flavor_roles") ? new HashSet<>(strings(json.getAsJsonArray("flavor_roles"))) : DishType.DEFAULT_FLAVOR_ROLES,
					json.has("bonus_nutrition") ? json.get("bonus_nutrition").getAsInt() : 0,
					json.has("bonus_saturation") ? json.get("bonus_saturation").getAsFloat() : 0.0f,
					json.has("liquid") && json.get("liquid").getAsBoolean(),
					json.has("served_with") ? Optional.of(json.get("served_with").getAsString()) : Optional.empty(),
					json.has("servings") ? json.get("servings").getAsInt() : 1,
					matchers(json, "raw_ok"),
					json.has("makes") ? json.get("makes").getAsInt() : 1,
					json.has("simmers_into") ? Optional.of(json.get("simmers_into").getAsString()) : Optional.empty(),
					json.has("simmer_seconds") ? json.get("simmer_seconds").getAsInt() : 0,
					json.has("burn_seconds") ? json.get("burn_seconds").getAsInt() : 0
			));
		}

		resolver = new CookingResolver(types, CookingRules.DEFAULT, id -> Optional.ofNullable(PROFILES.get(id)));
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
		assertDish("soup", List.of("beef", "potato"), "water_bucket", "beef", "potato");
		assertCrafted("salad", List.of("apple", "sweet_berry"), "bowl", "apple", "sweet_berries");
		assertDish("soup", List.of("pumpkin"), "water_bucket", "pumpkin");
		assertDish("soup", List.of("mushroom"), "water_bucket", "red_mushroom", "brown_mushroom");
		assertCrafted("sandwich", List.of("porkchop"), "bread", "cooked_porkchop");
		assertCrafted("kelp_roll", List.of("cod"), "dried_kelp", "cod");
		assertCrafted("skewer", List.of("chicken", "carrot"), "stick", "chicken", "carrot");
		assertDish("omelette", List.of("mushroom"), "egg", "brown_mushroom");
		assertDish("roast", List.of("rabbit", "potato"), "rabbit", "baked_potato");
		assertDish("roast", List.of("cod", "potato"), "cod", "potato");
		assertDish("roast", List.of("carrot", "potato"), "carrot", "potato");
		assertDish("roast", List.of("mushroom", "beetroot"), "brown_mushroom", "beetroot");
	}

	@Test
	void waterMakesSoupsNotRoasts() {
		assertDish("soup", List.of("cod", "carrot"), "water_bucket", "cod", "carrot");
		assertDish("soup", List.of("carrot", "potato"), "water_bucket", "carrot", "potato");
		assertDish("soup", List.of("beef"), "water_bucket", "beef");
	}

	@Test
	void recipesThatShouldWork() {
		assertDish("omelette", List.of("egg"), "egg", "egg");
		assertDish("omelette", List.of(), "egg");
		assertCrafted("kelp_roll", List.of("mushroom"), "dried_kelp", "shelf_mushroom");
		assertCrafted("kelp_roll", List.of("carrot"), "dried_kelp", "carrot");
		assertCrafted("sandwich", List.of("egg"), "bread", "egg");
		assertCrafted("skewer", List.of("mushroom"), "stick", "brown_mushroom");
		assertCrafted("salad", List.of("carrot"), "bowl", "carrot");
		assertDish("cookies", List.of("chocolate"), "wheat", "cocoa_beans");
		assertDish("cake", List.of(), "wheat", "sugar", "egg");
		assertDish("cake", List.of(), "wheat", "sugar", "egg", "milk_bucket");
		assertDish("pie", List.of("pumpkin"), "pumpkin", "sugar", "egg");
		assertDish("pie", List.of("mutton"), "wheat", "sugar", "egg", "cooked_mutton");
		assertDish("roast", List.of("carrot"), "carrot");
	}

	@Test
	void oneKindOfRawIngredientJustCooks() {
		assertEquals(Optional.of("minecraft:baked_potato"), resolver.cookedAlone(inputs("potato", "potato")));
		assertEquals(Optional.of("minecraft:cooked_beef"), resolver.cookedAlone(inputs("beef")));
		assertTrue(resolver.cookedAlone(inputs("beef", "potato")).isEmpty());
		assertTrue(resolver.cookedAlone(inputs("carrot")).isEmpty());
	}

	@Test
	void rawSandwichesCookInThePot() {
		DishResult beef = resolver.recook("dynamic_cooking:sandwich", inputs("bread", "beef")).orElseThrow();
		DishResult potato = resolver.recook("dynamic_cooking:sandwich", inputs("bread", "potato")).orElseThrow();

		assertFalse(beef.raw());
		assertEquals(List.of("minecraft:bread", "minecraft:cooked_beef"), beef.ingredients());
		assertEquals(List.of("minecraft:bread", "minecraft:baked_potato"), potato.ingredients());
	}

	@Test
	void soupSimmersIntoStewWithTheSameIngredients() {
		DishResult soup = cook("water_bucket", "carrot", "potato");
		DishResult stew = resolver.simmer("dynamic_cooking:stew", inputs("water_bucket", "carrot", "potato")).orElseThrow();

		assertEquals("dynamic_cooking:stew", stew.item());
		assertEquals(soup.nameFlavors(), stew.nameFlavors());
		assertEquals(soup.nutrition(), stew.nutrition());
		assertEquals(Optional.of("minecraft:bowl"), stew.servedWith());
		assertFalse(stew.liquid());
	}

	@Test
	void stewIsOnlyEverSimmered() {
		for (String food : List.of("beef", "carrot", "brown_mushroom")) {
			assertFalse(cook("water_bucket", food).item().equals("dynamic_cooking:stew"), food);
		}
	}

	@Test
	void burntDishesBecomeMushButKeepTheirSideEffects() {
		DishResult burnt = resolver.burn(inputs("water_bucket", "rotten_flesh", "carrot"));

		assertTrue(burnt.dubious());
		assertEquals("minecraft:hunger", burnt.sideEffects().getFirst().effect());
	}

	@Test
	void everyIngredientUsedAddsFood() {
		assertTrue(cook("water_bucket", "beef", "potato", "carrot").nutrition() > cook("water_bucket", "beef", "potato").nutrition());
		assertTrue(cook("egg", "egg", "egg").nutrition() > cook("egg", "egg").nutrition());
		// A dish beats eating its ingredients one by one.
		assertTrue(craft("bread", "cooked_beef").nutrition() > 5 + 8);
	}

	@Test
	void kelpRollsComeFourAtATimeAndShareTheirFood() {
		DishResult roll = craft("dried_kelp", "cooked_salmon");
		DishResult sandwich = craft("bread", "cooked_salmon");

		assertEquals(4, roll.count());
		assertEquals(1, sandwich.count());
		assertTrue(roll.nutrition() * roll.count() > 1 + 6);
		assertTrue(roll.nutrition() < sandwich.nutrition());
	}

	@Test
	void stewsAndSoupsAreServedWithABowl() {
		assertEquals(Optional.of("minecraft:bowl"), cook("water_bucket", "beef", "potato").servedWith());
		assertEquals(Optional.of("minecraft:bowl"), cook("water_bucket", "pumpkin").servedWith());
		assertTrue(cook("carrot", "potato").servedWith().isEmpty());
		assertEquals(Optional.of("minecraft:bowl"), cook("sugar", "sugar").servedWith());
		assertTrue(cook("wheat", "sugar", "egg", "carrot").servedWith().isEmpty());
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
	void onlyRunnyDishesShowLiquidInThePot() {
		assertTrue(cook("water_bucket", "beef", "potato").liquid());
		assertTrue(cook("water_bucket", "pumpkin").liquid());
		assertTrue(!cook("wheat", "sugar", "egg", "carrot").liquid());
		assertTrue(!cook("sugar", "sugar").liquid());
	}

	@Test
	void everyFlavorTintsThePot() {
		for (IngredientProfile profile : PROFILES.values()) {
			if (profile.flavor().isPresent()) {
				assertTrue(profile.color().isPresent(), profile.items() + " has a flavor but no pot color");
			}
		}
	}

	@Test
	void craftedDishesNeedACraftingTable() {
		assertTrue(cook("bread", "cooked_porkchop").dubious());
		assertTrue(cook("dried_kelp", "cod").dubious());
		assertTrue(resolver.match(CookingMethod.CRAFTING, inputs("wheat", "sugar", "egg", "carrot")).isEmpty());
	}

	@Test
	void sticksAndBreadDoNotMix() {
		assertCrafted("skewer", List.of("melon"), "stick", "melon_slice");
		assertCrafted("sandwich", List.of("melon"), "bread", "melon_slice");
		assertTrue(resolver.match(CookingMethod.CRAFTING, inputs("stick", "melon_slice", "bread")).isEmpty());
		assertTrue(resolver.match(CookingMethod.CRAFTING, inputs("dried_kelp", "cod", "stick")).isEmpty());
	}

	@Test
	void seedsAndSeasoningsGoInAnything() {
		assertCrafted("salad", List.of("apple", "melon"), "bowl", "apple", "melon_slice", "pumpkin_seeds");
		assertDish("cake", List.of("carrot"), "wheat", "sugar", "egg", "carrot", "wheat_seeds");
		assertCrafted("skewer", List.of("beef"), "stick", "cooked_beef", "golden_dandelion");
	}

	@Test
	void shelfMushroomsAreMushrooms() {
		assertDish("soup", List.of("mushroom"), "water_bucket", "shelf_mushroom");
	}

	@Test
	void everyRawIngredientCooksIntoSomethingThePotKnows() {
		for (IngredientProfile profile : PROFILES.values()) {
			profile.raw().flatMap(IngredientProfile.Raw::cooksInto).ifPresent(cooked -> {
				IngredientProfile into = PROFILES.get(cooked);
				assertTrue(into != null, profile.items() + " cooks into " + cooked + ", which has no profile");
				assertEquals(profile.flavor(), into.flavor(), cooked + " should keep the raw flavor");
				assertTrue(into.raw().isEmpty(), cooked + " should not be raw");
			});
		}
	}

	@Test
	void thePotCooksRawIngredients() {
		DishResult stew = cook("water_bucket", "beef", "potato");

		assertEquals(List.of("minecraft:water_bucket", "minecraft:cooked_beef", "minecraft:baked_potato"), stew.ingredients());
		assertFalse(stew.raw());
		assertEquals(List.of(false, false), stew.rawFlavors());
		assertEquals(cook("water_bucket", "cooked_beef", "baked_potato").nutrition(), stew.nutrition());
	}

	@Test
	void rawMeatInACraftedDishCostsFoodAndMaySicken() {
		DishResult raw = craft("stick", "chicken", "carrot");
		DishResult cooked = craft("stick", "cooked_chicken", "carrot");

		assertTrue(raw.raw());
		assertEquals(List.of(true, false), raw.rawFlavors());
		assertTrue(raw.nutrition() < cooked.nutrition());
		assertEquals("minecraft:hunger", raw.sideEffects().getFirst().effect());
		assertFalse(cooked.raw());
		assertTrue(cooked.sideEffects().isEmpty());
	}

	@Test
	void rawFishIsFineInAKelpRoll() {
		DishResult roll = craft("dried_kelp", "salmon");

		assertFalse(roll.raw());
		assertEquals(List.of(true), roll.rawFlavors());
		assertEquals(craft("dried_kelp", "cooked_salmon").nutrition(), roll.nutrition());
		assertTrue(craft("bread", "salmon").raw());
	}

	@Test
	void cookingARawDishAgainCooksItsIngredients() {
		DishResult raw = craft("stick", "chicken", "carrot");
		DishResult recooked = resolver.recook(raw.item(), inputs("stick", "chicken", "carrot")).orElseThrow();

		assertEquals("dynamic_cooking:skewer", recooked.item());
		assertEquals(List.of("minecraft:stick", "minecraft:cooked_chicken", "minecraft:carrot"), recooked.ingredients());
		assertFalse(recooked.raw());
		assertEquals(craft("stick", "cooked_chicken", "carrot").nutrition(), recooked.nutrition());
		assertTrue(resolver.recook("dynamic_cooking:skewer", inputs("stick", "cooked_chicken", "carrot")).isEmpty());
	}

	@Test
	void spoiledFoodSickensEvenWhenCooked() {
		assertEquals("minecraft:hunger", cook("water_bucket", "rotten_flesh", "carrot").sideEffects().getFirst().effect());
	}

	@Test
	void anEnchantedGoldenAppleKeepsAllItsEffects() {
		List<SideEffect> effects = cook("wheat", "sugar", "egg", "enchanted_golden_apple").sideEffects();

		assertEquals(List.of("minecraft:regeneration", "minecraft:absorption", "minecraft:resistance", "minecraft:fire_resistance"),
				effects.stream().map(SideEffect::effect).toList());
		assertEquals(3, effects.get(1).amplifier());
		assertEquals(300 * 20, effects.get(2).durationTicks());
	}

	@Test
	void sameEffectsAddUpAndBatchesShareThem() {
		SideEffect two = cook("wheat", "sugar", "egg", "golden_apple", "golden_apple").sideEffects().get(1);
		assertEquals("minecraft:absorption", two.effect());
		assertEquals(240 * 20, two.durationTicks());

		SideEffect roll = craft("dried_kelp", "golden_apple").sideEffects().get(1);
		assertEquals(30 * 20, roll.durationTicks());
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

	private static List<Matcher> matchers(JsonObject json, String key) {
		List<Matcher> out = new ArrayList<>();

		if (json.has(key)) {
			json.getAsJsonArray(key).forEach(element -> out.add(matcher(element.getAsJsonObject())));
		}

		return out;
	}

	private static IngredientProfile.Raw raw(JsonObject json) {
		return new IngredientProfile.Raw(
				json.has("cooks_into") ? Optional.of(json.get("cooks_into").getAsString()) : Optional.empty(),
				json.has("nutrition_penalty") ? json.get("nutrition_penalty").getAsInt() : 0,
				json.has("saturation_penalty") ? json.get("saturation_penalty").getAsFloat() : 0.0f,
				effects(json)
		);
	}

	private static List<SideEffect> effects(JsonObject json) {
		List<SideEffect> out = new ArrayList<>();

		if (json.has("effects")) {
			for (JsonElement element : json.getAsJsonArray("effects")) {
				JsonObject effect = element.getAsJsonObject();
				out.add(new SideEffect(
						effect.get("effect").getAsString(),
						effect.has("amplifier") ? effect.get("amplifier").getAsInt() : 0,
						(effect.has("seconds") ? effect.get("seconds").getAsInt() : 30) * 20,
						effect.has("chance") ? effect.get("chance").getAsFloat() : 1.0f
				));
			}
		}

		return out;
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
