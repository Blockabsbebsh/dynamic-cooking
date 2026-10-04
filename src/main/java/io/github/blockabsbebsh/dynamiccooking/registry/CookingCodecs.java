package io.github.blockabsbebsh.dynamiccooking.registry;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import io.github.blockabsbebsh.dynamiccooking.cooking.CookingMethod;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.cooking.Matcher;
import io.github.blockabsbebsh.dynamiccooking.cooking.Requirement;
import io.github.blockabsbebsh.dynamiccooking.cooking.SideEffect;

/**
 * JSON formats for the cooking data. The records themselves live in the Minecraft-free {@code cooking} package.
 */
public final class CookingCodecs {
	/** An id like {@code minecraft:carrot}; a bare {@code carrot} is read as {@code minecraft:carrot}. */
	public static final Codec<String> ID = Identifier.CODEC.xmap(Identifier::toString, Identifier::parse);

	private static final Codec<List<String>> IDS = ID.listOf();
	private static final Codec<List<String>> NAMES = Codec.STRING.listOf();

	public static final Codec<CookingMethod> METHOD = Codec.STRING.comapFlatMap(CookingCodecs::parseMethod, CookingMethod::id);

	public static final Codec<Matcher> MATCHER = RecordCodecBuilder.<Matcher>create(instance -> instance.group(
			IDS.optionalFieldOf("items", List.of()).forGetter(matcher -> List.copyOf(matcher.items())),
			NAMES.optionalFieldOf("roles", List.of()).forGetter(matcher -> List.copyOf(matcher.roles()))
	).apply(instance, (items, roles) -> new Matcher(Set.copyOf(items), Set.copyOf(roles)))).validate(CookingCodecs::validateMatcher);

	public static final Codec<Requirement> REQUIREMENT = RecordCodecBuilder.create(instance -> instance.group(
			MATCHER.fieldOf("match").forGetter(Requirement::matcher),
			Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Requirement::count),
			Codec.BOOL.optionalFieldOf("flavor", false).forGetter(Requirement::flavor)
	).apply(instance, Requirement::new));

	public static final Codec<IngredientProfile.BuffSource> BUFF_SOURCE = RecordCodecBuilder.create(instance -> instance.group(
			ID.fieldOf("effect").forGetter(IngredientProfile.BuffSource::effect),
			Codec.intRange(1, 64).optionalFieldOf("potency", 1).forGetter(IngredientProfile.BuffSource::potency)
	).apply(instance, IngredientProfile.BuffSource::new));

	/** Durations are written in seconds in JSON and kept in ticks in code. */
	public static final Codec<SideEffect> SIDE_EFFECT = RecordCodecBuilder.create(instance -> instance.group(
			ID.fieldOf("effect").forGetter(SideEffect::effect),
			Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(SideEffect::amplifier),
			Codec.intRange(1, 3600).optionalFieldOf("seconds", 30).forGetter(effect -> effect.durationTicks() / 20),
			Codec.floatRange(0.0f, 1.0f).optionalFieldOf("chance", 1.0f).forGetter(SideEffect::chance)
	).apply(instance, (effect, amplifier, seconds, chance) -> new SideEffect(effect, amplifier, seconds * 20, chance)));

	public static final Codec<IngredientProfile.Raw> RAW = RecordCodecBuilder.create(instance -> instance.group(
			ID.optionalFieldOf("cooks_into").forGetter(IngredientProfile.Raw::cooksInto),
			Codec.intRange(0, 20).optionalFieldOf("nutrition_penalty", 0).forGetter(IngredientProfile.Raw::nutritionPenalty),
			Codec.floatRange(0.0f, 20.0f).optionalFieldOf("saturation_penalty", 0.0f).forGetter(IngredientProfile.Raw::saturationPenalty),
			SIDE_EFFECT.listOf().optionalFieldOf("effects", List.of()).forGetter(IngredientProfile.Raw::effects)
	).apply(instance, IngredientProfile.Raw::new));

	public static final Codec<IngredientProfile> INGREDIENT_PROFILE = RecordCodecBuilder.create(instance -> instance.group(
			IDS.fieldOf("items").forGetter(IngredientProfile::items),
			NAMES.xmap(Set::copyOf, List::copyOf).fieldOf("roles").forGetter(IngredientProfile::roles),
			Codec.STRING.optionalFieldOf("flavor").forGetter(IngredientProfile::flavor),
			Codec.intRange(0, 20).optionalFieldOf("nutrition", 0).forGetter(IngredientProfile::nutrition),
			Codec.floatRange(0.0f, 20.0f).optionalFieldOf("saturation", 0.0f).forGetter(IngredientProfile::saturation),
			BUFF_SOURCE.optionalFieldOf("buff").forGetter(IngredientProfile::buff),
			Codec.STRING.comapFlatMap(CookingCodecs::parseColor, CookingCodecs::formatColor).optionalFieldOf("color").forGetter(IngredientProfile::color),
			RAW.optionalFieldOf("raw").forGetter(IngredientProfile::raw),
			SIDE_EFFECT.listOf().optionalFieldOf("effects", List.of()).forGetter(IngredientProfile::effects)
	).apply(instance, IngredientProfile::new));

	// A record codec takes at most 16 fields, so the rest are read around these.
	private static final MapCodec<DishType> DISH_TYPE_FIELDS = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ID.fieldOf("item").forGetter(DishType::item),
			Codec.INT.fieldOf("priority").forGetter(DishType::priority),
			METHOD.optionalFieldOf("method", CookingMethod.POT).forGetter(DishType::method),
			REQUIREMENT.listOf().fieldOf("requires").forGetter(DishType::requires),
			MATCHER.listOf().optionalFieldOf("forbids", List.of()).forGetter(DishType::forbids),
			NAMES.xmap(Set::copyOf, List::copyOf).optionalFieldOf("flavor_roles", DishType.DEFAULT_FLAVOR_ROLES).forGetter(DishType::flavorRoles),
			Codec.intRange(0, 20).optionalFieldOf("bonus_nutrition", 0).forGetter(DishType::bonusNutrition),
			Codec.floatRange(0.0f, 20.0f).optionalFieldOf("bonus_saturation", 0.0f).forGetter(DishType::bonusSaturation),
			Codec.BOOL.optionalFieldOf("liquid", false).forGetter(DishType::liquid),
			ID.optionalFieldOf("served_with").forGetter(DishType::servedWith),
			Codec.intRange(1, 64).optionalFieldOf("servings", 1).forGetter(DishType::servings),
			MATCHER.listOf().optionalFieldOf("raw_ok", List.of()).forGetter(DishType::rawOk),
			Codec.intRange(1, 64).optionalFieldOf("makes", 1).forGetter(DishType::makes),
			ID.optionalFieldOf("simmers_into").forGetter(DishType::simmersInto),
			Codec.intRange(0, 3600).optionalFieldOf("simmer_seconds", 0).forGetter(DishType::simmerSeconds),
			Codec.intRange(0, 3600).optionalFieldOf("burn_seconds", 0).forGetter(DishType::burnSeconds)
	).apply(instance, DishType::new));

	public static final Codec<DishType> DISH_TYPE = RecordCodecBuilder.create(instance -> instance.group(
			DISH_TYPE_FIELDS.forGetter(type -> type),
			Codec.BOOL.optionalFieldOf("melts", false).forGetter(DishType::melts)
	).apply(instance, DishType::withMelts));

	private CookingCodecs() {
	}

	/** Reads a {@code #RRGGBB} color. */
	private static DataResult<Integer> parseColor(String color) {
		if (!color.matches("#[0-9a-fA-F]{6}")) {
			return DataResult.error(() -> "Expected a color like #E58A1F, got " + color);
		}

		return DataResult.success(Integer.parseInt(color.substring(1), 16));
	}

	private static String formatColor(int color) {
		return String.format("#%06X", color & 0xFFFFFF);
	}

	private static DataResult<CookingMethod> parseMethod(String id) {
		for (CookingMethod method : CookingMethod.values()) {
			if (method.id().equals(id)) {
				return DataResult.success(method);
			}
		}

		return DataResult.error(() -> "Unknown cooking method " + id + ", expected pot or crafting");
	}

	private static DataResult<Matcher> validateMatcher(Matcher matcher) {
		if (matcher.items().isEmpty() && matcher.roles().isEmpty()) {
			return DataResult.error(() -> "A match needs at least one item or role");
		}

		return DataResult.success(matcher);
	}
}
