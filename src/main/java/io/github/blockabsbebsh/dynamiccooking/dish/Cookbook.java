package io.github.blockabsbebsh.dynamiccooking.dish;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

import io.github.blockabsbebsh.dynamiccooking.cooking.CookingMethod;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.cooking.Matcher;
import io.github.blockabsbebsh.dynamiccooking.cooking.Requirement;

/**
 * Writes the cookbook's pages from the loaded dish types and ingredients, so it always matches the data packs in use.
 *
 * <p>Roles are named by {@code role.dynamic_cooking.<role>} translations; unknown roles fall back to their key in title case.
 */
public final class Cookbook {
	/** Rough number of text lines that fit on a book page, used to pack the ingredient and buff lists. */
	private static final int LINES_PER_PAGE = 13;
	/** Rough number of characters per line of a book page. */
	private static final int CHARS_PER_LINE = 19;

	private Cookbook() {
	}

	public static WrittenBookContent write(CookingService cooking) {
		List<Component> pages = new ArrayList<>();
		pages.add(Component.translatable("cookbook.dynamic_cooking.intro.pot", cooking.maxIngredients()));
		pages.add(Component.translatable("cookbook.dynamic_cooking.intro.more"));

		for (DishType type : cooking.dishTypes()) {
			pages.add(dishPage(type));
		}

		pages.addAll(ingredientPages(cooking.profiles()));
		pages.addAll(buffPages(cooking.profiles()));

		// Left unresolved so the game resolves the pages and syncs the book before opening it.
		return new WrittenBookContent(
				Filterable.passThrough("Cookbook"),
				"Dynamic Cooking",
				0,
				pages.stream().map(Filterable::passThrough).toList(),
				false
		);
	}

	private static Component dishPage(DishType type) {
		MutableComponent page = Component.empty();
		page.append(itemName(type.item()).copy().withStyle(ChatFormatting.BOLD)).append("\n");
		page.append(Component.translatable("cookbook.dynamic_cooking.method." + type.method().id()).withStyle(ChatFormatting.DARK_GRAY));

		if (type.method() == CookingMethod.POT && type.servedWith().isPresent()) {
			page.append("\n").append(Component.translatable("cookbook.dynamic_cooking.served_with", itemName(type.servedWith().get()))
					.withStyle(ChatFormatting.DARK_GRAY));
		}

		page.append("\n\n").append(Component.translatable("cookbook.dynamic_cooking.needs"));

		for (Requirement requirement : type.requires()) {
			Component line = matcherText(requirement.matcher());

			if (requirement.count() > 1) {
				line = Component.translatable("cookbook.dynamic_cooking.count", requirement.count(), line);
			}

			page.append("\n- ").append(line);
		}

		if (!type.forbids().isEmpty()) {
			List<Component> forbidden = type.forbids().stream().map(Cookbook::matcherText).toList();
			page.append("\n\n").append(Component.translatable("cookbook.dynamic_cooking.forbids", join(forbidden, ", "))
					.withStyle(ChatFormatting.DARK_RED));
		}

		return page;
	}

	/** Which items fill each role, packed onto as few pages as fit. */
	private static List<Component> ingredientPages(List<IngredientProfile> profiles) {
		Map<String, List<String>> byRole = new TreeMap<>();

		for (IngredientProfile profile : profiles) {
			for (String role : profile.roles()) {
				byRole.computeIfAbsent(role, key -> new ArrayList<>()).addAll(profile.items());
			}
		}

		List<Entry> entries = new ArrayList<>();

		byRole.forEach((role, items) -> {
			List<Component> names = items.stream().sorted().map(Cookbook::itemName).toList();
			Component text = Component.empty().append(roleName(role).copy().withStyle(ChatFormatting.BOLD)).append(": ").append(join(names, ", "));
			// Item names average around ten characters plus a comma.
			entries.add(new Entry(text, 1 + (items.size() * 12) / CHARS_PER_LINE + 1));
		});

		return pack(Component.translatable("cookbook.dynamic_cooking.ingredients"), entries);
	}

	/** Which ingredients add a buff. */
	private static List<Component> buffPages(List<IngredientProfile> profiles) {
		Map<String, Component> lines = new TreeMap<>();

		for (IngredientProfile profile : profiles) {
			profile.buff().ifPresent(buff -> lines.put(profile.items().getFirst(),
					Component.empty().append(itemName(profile.items().getFirst())).append(": ").append(DishNames.buffName(buff.effect()))));
		}

		List<Entry> entries = lines.values().stream().map(text -> new Entry(text, 2)).toList();
		return entries.isEmpty() ? List.of() : pack(Component.translatable("cookbook.dynamic_cooking.buffs"), entries);
	}

	private static List<Component> pack(Component heading, List<Entry> entries) {
		List<Component> pages = new ArrayList<>();
		MutableComponent page = null;
		int lines = 0;

		for (Entry entry : entries) {
			if (page == null || lines + entry.lines() > LINES_PER_PAGE) {
				page = Component.empty().append(heading.copy().withStyle(ChatFormatting.UNDERLINE));
				pages.add(page);
				lines = 2;
			}

			page.append("\n").append(entry.text());
			lines += entry.lines();
		}

		return pages;
	}

	private static Component matcherText(Matcher matcher) {
		List<Component> options = new ArrayList<>();
		matcher.items().stream().sorted().map(Cookbook::itemName).forEach(options::add);
		matcher.roles().stream().sorted().map(Cookbook::roleName).forEach(options::add);
		return joinOr(options);
	}

	private static Component joinOr(List<Component> options) {
		if (options.size() == 1) {
			return options.getFirst();
		}

		return Component.translatable("cookbook.dynamic_cooking.or", join(options.subList(0, options.size() - 1), ", "), options.getLast());
	}

	private static Component join(List<Component> parts, String separator) {
		MutableComponent out = Component.empty();

		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(separator);
			}

			out.append(parts.get(i));
		}

		return out;
	}

	private static Component roleName(String role) {
		return Component.translatableWithFallback("role.dynamic_cooking." + role, DishNames.titleCase(role));
	}

	private static Component itemName(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
				.map(item -> (Component) Component.translatable(item.getDescriptionId()))
				.orElseGet(() -> Component.literal(id));
	}

	private record Entry(Component text, int lines) {
	}
}
