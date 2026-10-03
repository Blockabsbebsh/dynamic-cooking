package io.github.blockabsbebsh.dynamiccooking.registry;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.cooking.Matcher;
import io.github.blockabsbebsh.dynamiccooking.cooking.Requirement;

/**
 * JSON formats for the cooking data. The records themselves live in the Minecraft-free {@code cooking} package.
 */
public final class CookingCodecs {
	/** An id like {@code minecraft:carrot}; a bare {@code carrot} is read as {@code minecraft:carrot}. */
	public static final Codec<String> ID = Identifier.CODEC.xmap(Identifier::toString, Identifier::parse);

	private static final Codec<List<String>> IDS = ID.listOf();
	private static final Codec<List<String>> NAMES = Codec.STRING.listOf();

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

	public static final Codec<IngredientProfile> INGREDIENT_PROFILE = RecordCodecBuilder.create(instance -> instance.group(
			IDS.fieldOf("items").forGetter(IngredientProfile::items),
			NAMES.xmap(Set::copyOf, List::copyOf).fieldOf("roles").forGetter(IngredientProfile::roles),
			Codec.STRING.optionalFieldOf("flavor").forGetter(IngredientProfile::flavor),
			Codec.intRange(0, 20).optionalFieldOf("nutrition", 0).forGetter(IngredientProfile::nutrition),
			Codec.floatRange(0.0f, 20.0f).optionalFieldOf("saturation", 0.0f).forGetter(IngredientProfile::saturation),
			BUFF_SOURCE.optionalFieldOf("buff").forGetter(IngredientProfile::buff)
	).apply(instance, IngredientProfile::new));

	public static final Codec<DishType> DISH_TYPE = RecordCodecBuilder.create(instance -> instance.group(
			ID.fieldOf("item").forGetter(DishType::item),
			Codec.INT.fieldOf("priority").forGetter(DishType::priority),
			REQUIREMENT.listOf().fieldOf("requires").forGetter(DishType::requires),
			MATCHER.listOf().optionalFieldOf("forbids", List.of()).forGetter(DishType::forbids),
			NAMES.xmap(Set::copyOf, List::copyOf).optionalFieldOf("flavor_roles", DishType.DEFAULT_FLAVOR_ROLES).forGetter(DishType::flavorRoles),
			Codec.intRange(0, 20).optionalFieldOf("bonus_nutrition", 0).forGetter(DishType::bonusNutrition),
			Codec.floatRange(0.0f, 20.0f).optionalFieldOf("bonus_saturation", 0.0f).forGetter(DishType::bonusSaturation)
	).apply(instance, DishType::new));

	private CookingCodecs() {
	}

	private static DataResult<Matcher> validateMatcher(Matcher matcher) {
		if (matcher.items().isEmpty() && matcher.roles().isEmpty()) {
			return DataResult.error(() -> "A match needs at least one item or role");
		}

		return DataResult.success(matcher);
	}
}
