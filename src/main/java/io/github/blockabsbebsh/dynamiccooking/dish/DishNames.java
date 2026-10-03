package io.github.blockabsbebsh.dynamiccooking.dish;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import io.github.blockabsbebsh.dynamiccooking.cooking.DishResult;

/**
 * Builds dish names like "Carrot Cake", "Beef and Potato Stew" or "Night Vision Carrot Cake" from translation keys,
 * so other languages can reorder the words.
 *
 * <ul>
 * <li>{@code dish.<namespace>.<dish>}: the pattern, e.g. {@code "%s Cake"}</li>
 * <li>{@code flavor.<flavor>}: a flavor name, e.g. {@code "Carrot"}; unknown flavors fall back to their key in title case</li>
 * <li>{@code dish.dynamic_cooking.flavor_pair}: two flavors, {@code "%s and %s"}</li>
 * <li>{@code buff.<namespace>.<effect>} and {@code dish.dynamic_cooking.buffed}: the buff prefix</li>
 * <li>{@code dish.dynamic_cooking.raw}: the prefix for a dish with raw ingredients, {@code "Raw %s"}</li>
 * </ul>
 */
public final class DishNames {
	private DishNames() {
	}

	public static Optional<Component> name(DishResult result) {
		if (result.nameFlavors().isEmpty()) {
			return Optional.empty();
		}

		Component flavor = flavorName(result.nameFlavors().getFirst());

		if (result.nameFlavors().size() > 1) {
			flavor = Component.translatable("dish.dynamic_cooking.flavor_pair", flavor, flavorName(result.nameFlavors().get(1)));
		}

		Identifier dish = Identifier.parse(result.item());
		Component name = Component.translatable("dish." + dish.getNamespace() + "." + dish.getPath(), flavor);

		if (result.buff().isPresent()) {
			name = Component.translatable("dish.dynamic_cooking.buffed", buffName(result.buff().get().effect()), name);
		}

		if (result.raw()) {
			name = Component.translatable("dish.dynamic_cooking.raw", name);
		}

		return Optional.of(name);
	}

	/** The buff's name as a dish prefix, like "Hasty" for speed. */
	static Component buffName(String effectId) {
		Identifier effect = Identifier.parse(effectId);
		return Component.translatableWithFallback("buff." + effect.getNamespace() + "." + effect.getPath(), titleCase(effect.getPath()));
	}

	private static Component flavorName(String flavor) {
		return Component.translatableWithFallback("flavor." + flavor.replace(':', '.'), titleCase(flavor));
	}

	static String titleCase(String key) {
		String path = key.substring(key.indexOf(':') + 1);
		StringBuilder out = new StringBuilder();

		for (String word : path.split("_")) {
			if (word.isEmpty()) {
				continue;
			}

			if (!out.isEmpty()) {
				out.append(' ');
			}

			out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}

		return out.toString();
	}
}
