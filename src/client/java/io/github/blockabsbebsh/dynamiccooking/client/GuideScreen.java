package io.github.blockabsbebsh.dynamiccooking.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingMethod;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.cooking.Matcher;
import io.github.blockabsbebsh.dynamiccooking.cooking.Requirement;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;
import io.github.blockabsbebsh.dynamiccooking.item.ModItems;

/**
 * The cooking guide: a few pages of steps with item pictures, then one page per dish built from the loaded data,
 * so data packs that add dishes show up too. Pictures are drawn from item icons, so the guide needs no textures of its own.
 */
public class GuideScreen extends Screen {
	private static final int PAGE_WIDTH = 180;
	private static final int PAGE_HEIGHT = 200;
	private static final int PADDING = 10;
	private static final int TEXT_WIDTH = PAGE_WIDTH - 2 * PADDING;
	/** Requirement rows show this many item icons at once and cycle through the rest. */
	private static final int ICONS_PER_ROW = 8;

	private static final int PAPER = 0xFFF4EAD2;
	private static final int PAPER_DARK = 0xFFE6D6B0;
	private static final int BORDER = 0xFF7A5630;
	private static final int INK = 0xFF3B2A18;
	private static final int INK_LIGHT = 0xFF7A6A55;
	private static final int INK_RED = 0xFF9A2A20;

	private final List<List<Row>> pages = new ArrayList<>();
	private int page;
	private Button back;
	private Button forward;

	public GuideScreen() {
		super(Component.translatable("guide.dynamic_cooking.title"));
	}

	@Override
	protected void init() {
		if (pages.isEmpty()) {
			buildPages();
		}

		int left = (width - PAGE_WIDTH) / 2;
		int buttonsY = top() + PAGE_HEIGHT + 4;
		back = addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1)).bounds(left, buttonsY, 20, 20).build());
		forward = addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1)).bounds(left + PAGE_WIDTH - 20, buttonsY, 20, 20).build());
		updateButtons();
	}

	private int top() {
		return Math.max(4, (height - PAGE_HEIGHT - 24) / 2);
	}

	private void turn(int by) {
		page = Math.clamp(page + by, 0, pages.size() - 1);
		updateButtons();
	}

	private void updateButtons() {
		back.active = page > 0;
		forward.active = page < pages.size() - 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int left = (width - PAGE_WIDTH) / 2;
		int top = top();

		graphics.fill(left - 2, top - 2, left + PAGE_WIDTH + 2, top + PAGE_HEIGHT + 2, BORDER);
		graphics.fill(left, top, left + PAGE_WIDTH, top + PAGE_HEIGHT, PAPER);

		ItemStack hovered = ItemStack.EMPTY;
		int y = top + PADDING;

		for (Row row : pages.get(page)) {
			ItemStack hit = row.draw(this, graphics, left + PADDING, y, mouseX, mouseY);
			hovered = hit.isEmpty() ? hovered : hit;
			y += row.height(this) + 4;
		}

		Component number = Component.translatable("guide.dynamic_cooking.page", page + 1, pages.size());
		graphics.centeredText(font, number, width / 2, top + PAGE_HEIGHT + 10, 0xFFFFFFFF);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
		}
	}

	private void buildPages() {
		Optional<CookingService> cooking = Optional.ofNullable(minecraft.level).map(level -> CookingService.create(level.registryAccess()));
		int maxIngredients = cooking.map(CookingService::maxIngredients).orElse(5);
		ItemStack pot = new ItemStack(ModBlocks.COOKING_POT);

		pages.add(List.of(
				heading("guide.dynamic_cooking.pot.title"),
				new Picture(List.of(pot, "+", stack(Items.CAMPFIRE))),
				text(Component.translatable("guide.dynamic_cooking.pot.text")),
				heading("guide.dynamic_cooking.add.title"),
				new Picture(List.of(stack(Items.CARROT), stack(Items.POTATO), stack(Items.BEEF), "→", pot)),
				text(Component.translatable("guide.dynamic_cooking.add.text", maxIngredients))
		));
		pages.add(List.of(
				heading("guide.dynamic_cooking.water.title"),
				new Picture(List.of(stack(Items.WATER_BUCKET), "→", pot)),
				text(Component.translatable("guide.dynamic_cooking.water.text")),
				heading("guide.dynamic_cooking.cook.title"),
				new Picture(List.of(pot, "→", stack(Items.BELL))),
				text(Component.translatable("guide.dynamic_cooking.cook.text"))
		));
		pages.add(List.of(
				heading("guide.dynamic_cooking.serve.title"),
				new Picture(List.of(pot, "+", stack(Items.BOWL), "→", stack(ModItems.STEW))),
				text(Component.translatable("guide.dynamic_cooking.serve.text")),
				heading("guide.dynamic_cooking.craft.title"),
				new Picture(List.of(stack(Items.BREAD), stack(Items.COOKED_BEEF), stack(Items.BREAD), "→", stack(Items.CRAFTING_TABLE), "→",
						stack(ModItems.SANDWICH))),
				text(Component.translatable("guide.dynamic_cooking.craft.text"))
		));
		pages.add(List.of(
				heading("guide.dynamic_cooking.raw.title"),
				new Picture(List.of(stack(ModItems.SKEWER), "→", stack(Items.FURNACE), stack(Items.SMOKER), stack(Items.CAMPFIRE), pot)),
				text(Component.translatable("guide.dynamic_cooking.raw.text")),
				text(Component.translatable("guide.dynamic_cooking.dishes").withStyle(ChatFormatting.ITALIC))
		));

		cooking.ifPresent(service -> {
			for (DishType type : service.dishTypes()) {
				pages.add(dishPage(service, type));
			}
		});
	}

	private List<Row> dishPage(CookingService cooking, DishType type) {
		List<Row> rows = new ArrayList<>();
		ItemStack dish = itemStack(type.item());
		rows.add(new Heading(dish, dish.getHoverName().copy().withStyle(ChatFormatting.BOLD)));

		MutableComponent method = Component.translatable("guide.dynamic_cooking.method." + type.method().id());

		if (type.method() == CookingMethod.POT && type.servedWith().isPresent()) {
			method.append(" · ").append(Component.translatable("guide.dynamic_cooking.served_with", itemStack(type.servedWith().get()).getHoverName()));
		}

		rows.add(new Text(method, INK_LIGHT));

		for (Requirement requirement : type.requires()) {
			Component label = matcherText(requirement.matcher());

			if (requirement.count() > 1) {
				label = Component.translatable("guide.dynamic_cooking.count", requirement.count(), label);
			}

			rows.add(new Icons(label, items(cooking, requirement.matcher())));
		}

		if (!type.forbids().isEmpty()) {
			List<Component> forbidden = type.forbids().stream().map(GuideScreen::matcherText).toList();
			rows.add(new Text(Component.translatable("guide.dynamic_cooking.forbids", join(forbidden, ", ")), INK_RED));
		}

		return rows;
	}

	/** Every item a matcher accepts: the ones it names, then everything with one of its roles. */
	private static List<ItemStack> items(CookingService cooking, Matcher matcher) {
		Set<String> ids = new LinkedHashSet<>(matcher.items().stream().sorted().toList());

		for (IngredientProfile profile : cooking.profiles()) {
			if (profile.roles().stream().anyMatch(matcher.roles()::contains)) {
				ids.addAll(profile.items());
			}
		}

		return ids.stream().map(GuideScreen::itemStack).filter(stack -> !stack.isEmpty()).toList();
	}

	private static Component matcherText(Matcher matcher) {
		List<Component> options = new ArrayList<>();
		matcher.items().stream().sorted().map(id -> itemStack(id).getHoverName()).forEach(options::add);
		matcher.roles().stream().sorted().map(GuideScreen::roleName).forEach(options::add);

		if (options.size() == 1) {
			return options.getFirst();
		}

		return Component.translatable("guide.dynamic_cooking.or", join(options.subList(0, options.size() - 1), ", "), options.getLast());
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
		String fallback = role.isEmpty() ? role : Character.toUpperCase(role.charAt(0)) + role.substring(1).replace('_', ' ');
		return Component.translatableWithFallback("role.dynamic_cooking." + role, fallback);
	}

	private static ItemStack itemStack(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	private static ItemStack stack(Item item) {
		return new ItemStack(item);
	}

	private static Row heading(String key) {
		return new Text(Component.translatable(key).withStyle(ChatFormatting.BOLD), INK);
	}

	private static Row text(Component text) {
		return new Text(text, INK);
	}

	private List<FormattedCharSequence> lines(Component text) {
		return font.split(text, TEXT_WIDTH);
	}

	/** Draws an item icon and returns it if the mouse is over it. */
	private static ItemStack icon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
		graphics.item(stack, x, y);
		boolean over = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
		return over ? stack : ItemStack.EMPTY;
	}

	private sealed interface Row permits Text, Heading, Picture, Icons {
		int height(GuideScreen screen);

		/** Draws the row and returns the item under the mouse, or an empty stack. */
		ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY);
	}

	private record Text(Component text, int color) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return screen.lines(text).size() * screen.font.lineHeight;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			for (FormattedCharSequence line : screen.lines(text)) {
				graphics.text(screen.font, line, x, y, color, false);
				y += screen.font.lineHeight;
			}

			return ItemStack.EMPTY;
		}
	}

	/** A dish's icon and name at the top of its page. */
	private record Heading(ItemStack icon, Component name) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return 16;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			graphics.text(screen.font, name, x + 20, y + 4, INK, false);
			return icon(graphics, icon, x, y, mouseX, mouseY);
		}
	}

	/** A step drawn as item icons with arrows and plus signs between them, centered on a darker band. */
	private record Picture(List<Object> parts) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return 22;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			graphics.fill(x, y, x + TEXT_WIDTH, y + 22, PAPER_DARK);

			int total = parts.stream().mapToInt(part -> width(screen, part)).sum();
			int partX = x + (TEXT_WIDTH - total) / 2;
			ItemStack hovered = ItemStack.EMPTY;

			for (Object part : parts) {
				if (part instanceof ItemStack stack) {
					ItemStack hit = icon(graphics, stack, partX + 1, y + 3, mouseX, mouseY);
					hovered = hit.isEmpty() ? hovered : hit;
				} else {
					graphics.text(screen.font, part.toString(), partX + 3, y + 7, INK, false);
				}

				partX += width(screen, part);
			}

			return hovered;
		}

		private static int width(GuideScreen screen, Object part) {
			return part instanceof ItemStack ? 18 : screen.font.width(part.toString()) + 6;
		}
	}

	/** One thing a dish needs: its name, then every item that counts, cycling when there are too many to fit. */
	private record Icons(Component label, List<ItemStack> items) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return screen.lines(label).size() * screen.font.lineHeight + (items.isEmpty() ? 0 : 18);
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			for (FormattedCharSequence line : screen.lines(Component.literal("• ").append(label))) {
				graphics.text(screen.font, line, x, y, INK, false);
				y += screen.font.lineHeight;
			}

			int shown = Math.min(ICONS_PER_ROW, items.size());
			int start = items.size() > ICONS_PER_ROW ? (int) (System.currentTimeMillis() / 1000 % items.size()) : 0;
			ItemStack hovered = ItemStack.EMPTY;

			for (int i = 0; i < shown; i++) {
				ItemStack hit = icon(graphics, items.get((start + i) % items.size()), x + 6 + i * 18, y + 1, mouseX, mouseY);
				hovered = hit.isEmpty() ? hovered : hit;
			}

			return hovered;
		}
	}
}
