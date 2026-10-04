package io.github.blockabsbebsh.dynamiccooking.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
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
 * so data packs that add dishes show up too. It opens as two pages of the game's own written book, the left one
 * mirrored so the stitching meets in the middle, so it follows resource packs. Pictures are drawn from item icons.
 */
public class GuideScreen extends Screen {
	private static final Identifier BOOK = Identifier.parse("minecraft:textures/gui/book.png");
	private static final Identifier SLOT = Identifier.parse("minecraft:container/slot");
	/** Where one page sits in the book texture. */
	private static final int PAGE_U = 20;
	private static final int PAGE_V = 1;
	private static final int PAGE_WIDTH = 146;
	private static final int PAGE_HEIGHT = 180;
	/** The writing area of a page: inside the paper, clear of the stitching and the page arrows. */
	private static final int TEXT_TOP = 14;
	private static final int TEXT_HEIGHT = 138;
	private static final int TEXT_WIDTH = 112;
	/** Distance from a page's outer edge to its text, which sits away from the stitching. */
	private static final int TEXT_LEFT = 18;
	private static final int TEXT_RIGHT_PAGE = 16;
	/** Requirement rows show this many item icons at once and cycle through the rest. */
	private static final int ICONS_PER_ROW = 5;

	private static final int PICTURE_BAND = 0xFFE9DFC4;
	private static final int INK = 0xFF000000;
	private static final int INK_LIGHT = 0xFF5A5A5A;
	private static final int INK_RED = 0xFF9A2A20;

	private final List<List<Row>> pages = new ArrayList<>();
	/** The left page of the open spread; always even. */
	private int page;
	private PageArrow back;
	private PageArrow forward;

	public GuideScreen() {
		super(Component.translatable("guide.dynamic_cooking.title"));
	}

	@Override
	protected void init() {
		if (pages.isEmpty()) {
			buildPages();
		}

		// The arrows sit where the written book has them, mirrored on the left page.
		back = addRenderableWidget(new PageArrow(left() + PAGE_WIDTH - 96 - PageArrow.WIDTH, top() + 156, false, () -> turn(-2)));
		forward = addRenderableWidget(new PageArrow(left() + PAGE_WIDTH + 96, top() + 156, true, () -> turn(2)));
		updateButtons();
	}

	private int left() {
		return (width - 2 * PAGE_WIDTH) / 2;
	}

	private int top() {
		return Math.max(2, (height - PAGE_HEIGHT) / 2);
	}

	private void turn(int by) {
		page = Math.clamp(page + by, 0, (pages.size() - 1) & ~1);
		updateButtons();
	}

	private void updateButtons() {
		back.visible = page > 0;
		forward.visible = page + 2 < pages.size();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int left = left();
		int top = top();

		// The left page is the book page flipped, by drawing it with its texture coordinates running backwards.
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK, left, top, PAGE_U + PAGE_WIDTH, PAGE_V, PAGE_WIDTH, PAGE_HEIGHT, -PAGE_WIDTH, PAGE_HEIGHT, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK, left + PAGE_WIDTH, top, PAGE_U, PAGE_V, PAGE_WIDTH, PAGE_HEIGHT, 256, 256);

		ItemStack hovered = ItemStack.EMPTY;

		for (int side = 0; side < 2 && page + side < pages.size(); side++) {
			int x = side == 0 ? left + TEXT_LEFT : left + PAGE_WIDTH + TEXT_RIGHT_PAGE;
			int y = top + TEXT_TOP;

			for (Row row : pages.get(page + side)) {
				ItemStack hit = row.draw(this, graphics, x, y, mouseX, mouseY);
				hovered = hit.isEmpty() ? hovered : hit;
				y += row.height(this) + 4;
			}

			String number = String.valueOf(page + side + 1);
			graphics.text(font, number, x + (TEXT_WIDTH - font.width(number)) / 2, top + 159, INK_LIGHT, false);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
		}
	}

	private void buildPages() {
		Optional<CookingService> cooking = Optional.ofNullable(minecraft.level).map(level -> CookingService.create(level.registryAccess()));
		int maxIngredients = cooking.map(CookingService::maxIngredients).orElse(5);
		ItemStack pot = new ItemStack(ModBlocks.COOKING_POT);
		List<Component> crafted = cooking.map(service -> service.dishTypes().stream()
				.filter(type -> type.method() == CookingMethod.CRAFTING)
				.map(type -> itemStack(type.item()).getHoverName())
				.toList()).orElse(List.of());

		List<List<Row>> steps = new ArrayList<>();
		steps.add(step("pot", new Picture(List.of(pot, "+", stack(Items.CAMPFIRE), stack(Items.FURNACE), stack(Items.MAGMA_BLOCK))),
				Component.translatable("guide.dynamic_cooking.pot.text")));
		steps.add(step("add", new Picture(List.of(stack(Items.CARROT), stack(Items.POTATO), stack(Items.BEEF), "→", pot)),
				Component.translatable("guide.dynamic_cooking.add.text", maxIngredients)));
		steps.add(step("water", new Picture(List.of(stack(Items.WATER_BUCKET), "→", pot)),
				Component.translatable("guide.dynamic_cooking.water.text")));
		steps.add(step("cook", new Picture(List.of(pot, "→", stack(Items.BELL))),
				Component.translatable("guide.dynamic_cooking.cook.text")));
		steps.add(step("serve", new Picture(List.of(pot, "+", stack(Items.BOWL), "→", stack(ModItems.STEW))),
				Component.translatable("guide.dynamic_cooking.serve.text")));

		if (!crafted.isEmpty()) {
			steps.add(step("craft", new Picture(List.of(stack(Items.BREAD), stack(Items.COOKED_BEEF), stack(Items.BREAD), "→",
					stack(Items.CRAFTING_TABLE), "→", stack(ModItems.SANDWICH))),
					Component.translatable("guide.dynamic_cooking.craft.text", join(crafted, ", "))));
		}

		steps.add(step("raw", new Picture(List.of(stack(ModItems.SKEWER), "→", stack(Items.FURNACE), stack(Items.SMOKER), stack(Items.CAMPFIRE), pot)),
				Component.translatable("guide.dynamic_cooking.raw.text")));
		steps.add(step("mix", new Picture(List.of(stack(Items.ENCHANTED_GOLDEN_APPLE), "→", stack(ModItems.PIE))),
				Component.translatable("guide.dynamic_cooking.mix.text")));
		steps.add(List.of(text(Component.translatable("guide.dynamic_cooking.dishes").withStyle(ChatFormatting.ITALIC))));
		pack(steps);

		cooking.ifPresent(service -> {
			for (DishType type : service.dishTypes()) {
				pack(List.of(dishPage(service, type)));
			}
		});
	}

	private static List<Row> step(String key, Picture picture, Component text) {
		return List.of(heading("guide.dynamic_cooking." + key + ".title"), picture, text(text));
	}

	/**
	 * Lays blocks of rows out on pages, starting a new page whenever the next block doesn't fit. A block taller than a page
	 * is split between rows.
	 */
	private void pack(List<List<Row>> blocks) {
		int available = TEXT_HEIGHT;
		List<Row> page = new ArrayList<>();
		int used = 0;

		for (List<Row> block : blocks) {
			int height = block.stream().mapToInt(row -> row.height(this) + 4).sum();
			int gap = Spacer.INSTANCE.height(this) + 4;

			if (!page.isEmpty() && used + gap + height > available) {
				pages.add(page);
				page = new ArrayList<>();
				used = 0;
			} else if (!page.isEmpty()) {
				page.add(Spacer.INSTANCE);
				used += gap;
			}

			for (Row row : block) {
				int rowHeight = row.height(this) + 4;

				if (!page.isEmpty() && used + rowHeight > available) {
					pages.add(page);
					page = new ArrayList<>();
					used = 0;
				}

				page.add(row);
				used += rowHeight;
			}
		}

		if (!page.isEmpty()) {
			pages.add(page);
		}
	}

	private List<Row> dishPage(CookingService cooking, DishType type) {
		List<Row> rows = new ArrayList<>();
		ItemStack dish = itemStack(type.item());
		rows.add(new Heading(dish, dish.getHoverName().copy().withStyle(ChatFormatting.BOLD)));

		MutableComponent method = Component.translatable("guide.dynamic_cooking.method." + type.method().id());

		if (type.method() == CookingMethod.POT && type.servedWith().isPresent()) {
			method.append(" · ").append(Component.translatable("guide.dynamic_cooking.served_with", itemStack(type.servedWith().get()).getHoverName()));
		}

		if (type.makes() > 1) {
			method.append(" · ").append(Component.translatable("guide.dynamic_cooking.makes", type.makes()));
		}

		rows.add(new Text(method, INK_LIGHT));
		rows.add(heading("guide.dynamic_cooking.needs"));

		for (Requirement requirement : type.requires()) {
			Component label = matcherText(requirement.matcher());

			if (requirement.count() > 1) {
				label = Component.translatable("guide.dynamic_cooking.count", requirement.count(), label);
			}

			rows.add(new Icons(label, items(cooking, requirement.matcher())));
		}

		// Extras: flavors the dish takes on top of what it needs, minus anything it refuses.
		Set<String> required = new HashSet<>();
		type.requires().forEach(requirement -> required.addAll(requirement.matcher().roles()));
		Set<String> forbidden = new HashSet<>();
		type.forbids().forEach(matcher -> forbidden.addAll(matcher.roles()));
		List<String> extras = type.flavorRoles().stream().filter(role -> !required.contains(role) && !forbidden.contains(role)).sorted().toList();

		if (!extras.isEmpty()) {
			Matcher matcher = new Matcher(Set.of(), Set.copyOf(extras));
			rows.add(heading("guide.dynamic_cooking.extras"));
			rows.add(new Icons(matcherText(matcher), items(cooking, matcher)));
		}

		if (!type.forbids().isEmpty()) {
			List<Component> refused = type.forbids().stream().map(GuideScreen::matcherText).toList();
			rows.add(new Text(Component.translatable("guide.dynamic_cooking.forbids", join(refused, ", ")), INK_RED));
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
		return joinOr(options);
	}

	private static Component joinOr(List<Component> options) {
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
	private static ItemStack drawItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
		graphics.item(stack, x, y);
		boolean over = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
		return over ? stack : ItemStack.EMPTY;
	}

	private sealed interface Row permits Text, Heading, Picture, Icons, Spacer {
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

	/** A little extra room between steps that share a page. */
	private record Spacer() implements Row {
		static final Spacer INSTANCE = new Spacer();

		@Override
		public int height(GuideScreen screen) {
			return 4;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			return ItemStack.EMPTY;
		}
	}

	/** A dish's icon and name at the top of its page. */
	private record Heading(ItemStack icon, Component name) implements Row {
		/** The name wraps beside the icon's slot. */
		private static final int NAME_WIDTH = TEXT_WIDTH - 22;

		@Override
		public int height(GuideScreen screen) {
			return Math.max(18, screen.font.split(name, NAME_WIDTH).size() * screen.font.lineHeight + 1);
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18);
			List<FormattedCharSequence> lines = screen.font.split(name, NAME_WIDTH);
			// Centred on the slot when it fits beside it, otherwise from the top down.
			int lineY = y + 1 + Math.max(0, (18 - lines.size() * screen.font.lineHeight) / 2);

			for (FormattedCharSequence line : lines) {
				graphics.text(screen.font, line, x + 22, lineY, INK, false);
				lineY += screen.font.lineHeight;
			}

			return drawItem(graphics, icon, x + 1, y + 1, mouseX, mouseY);
		}
	}

	/** A step drawn as item icons with arrows and plus signs between them, centered on a darker band of paper. */
	private record Picture(List<Object> parts) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return 22;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			graphics.fill(x, y, x + TEXT_WIDTH, y + 22, PICTURE_BAND);

			int total = parts.stream().mapToInt(part -> width(screen, part)).sum();
			int partX = x + (TEXT_WIDTH - total) / 2;
			ItemStack hovered = ItemStack.EMPTY;

			for (Object part : parts) {
				if (part instanceof ItemStack stack) {
					ItemStack hit = drawItem(graphics, stack, partX + 1, y + 3, mouseX, mouseY);
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
			return screen.lines(label).size() * screen.font.lineHeight + (items.isEmpty() ? 0 : 20);
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
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x + 4 + i * 18, y + 1, 18, 18);
				ItemStack hit = drawItem(graphics, items.get((start + i) % items.size()), x + 5 + i * 18, y + 2, mouseX, mouseY);
				hovered = hit.isEmpty() ? hovered : hit;
			}

			return hovered;
		}
	}

	/** The written book's page arrow, highlighted under the mouse. */
	private static final class PageArrow extends AbstractButton {
		static final int WIDTH = 23;
		static final int HEIGHT = 13;

		private final boolean forward;
		private final Runnable turn;

		PageArrow(int x, int y, boolean forward, Runnable turn) {
			super(x, y, WIDTH, HEIGHT, Component.translatable(forward ? "guide.dynamic_cooking.next" : "guide.dynamic_cooking.previous"));
			this.forward = forward;
			this.turn = turn;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			String sprite = "minecraft:widget/page_" + (forward ? "forward" : "backward") + (isHoveredOrFocused() ? "_highlighted" : "");
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(sprite), getX(), getY(), WIDTH, HEIGHT);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			turn.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
