package io.github.blockabsbebsh.dynamiccooking.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
import io.github.blockabsbebsh.dynamiccooking.dish.Vessel;
import io.github.blockabsbebsh.dynamiccooking.item.ModItems;

/**
 * The cooking guide, drawn on two pages of the game's own written book so it follows resource packs.
 *
 * <p>Every page holds one topic: a title page and contents, one page per cooking step with a picture and a short
 * caption, then one page per dish built from the loaded data, so data packs that add dishes show up too. The contents
 * link to the steps and to every dish, and a page number links back to the contents.
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
	private static final int TEXT_HEIGHT = 140;
	private static final int TEXT_WIDTH = 112;
	/** Distance from a page's outer edge to its text, which sits away from the stitching. */
	private static final int TEXT_LEFT = 18;
	private static final int TEXT_RIGHT_PAGE = 16;
	private static final int NUMBER_Y = 159;
	private static final int LINE = 10;

	private static final int INK = 0xFF000000;
	private static final int INK_LIGHT = 0xFF6B6157;
	private static final int INK_RED = 0xFF9A2A20;
	private static final int RULE = 0xFFB8A888;
	private static final int HIGHLIGHT = 0x40FFFFFF;

	private final List<List<Row>> pages = new ArrayList<>();
	/** The page each dish starts on, by item id. */
	private final Map<String, Integer> dishPages = new HashMap<>();
	private final List<LinkButton> links = new ArrayList<>();
	/** Lines of text to show under the mouse this frame, for things that aren't items. */
	private List<Component> textTooltip;
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
		back = addRenderableWidget(new PageArrow(left() + PAGE_WIDTH - 96 - PageArrow.WIDTH, top() + 156, false, () -> turnTo(page - 2)));
		forward = addRenderableWidget(new PageArrow(left() + PAGE_WIDTH + 96, top() + 156, true, () -> turnTo(page + 2)));

		// Links are laid out once, where their rows will draw them, and only shown while their page is open.
		links.clear();

		for (int index = 0; index < pages.size(); index++) {
			int x = textX(index);
			int y = top() + TEXT_TOP;

			for (Row row : pages.get(index)) {
				for (Link link : row.links(this, x, y)) {
					links.add(addRenderableWidget(new LinkButton(index, link, () -> turnTo(link.target()))));
				}

				y += row.height(this);
			}

			if (index > 1) {
				String number = String.valueOf(index + 1);
				int numberX = x + (TEXT_WIDTH - font.width(number)) / 2;
				Link home = new Link(numberX - 2, top() + NUMBER_Y - 2, font.width(number) + 4, LINE + 2, 1,
						Component.translatable("guide.dynamic_cooking.back"));
				links.add(addRenderableWidget(new LinkButton(index, home, () -> turnTo(1))));
			}
		}

		updateButtons();
	}

	private int left() {
		return (width - 2 * PAGE_WIDTH) / 2;
	}

	private int top() {
		return Math.max(2, (height - PAGE_HEIGHT) / 2);
	}

	/** Where the text of a page starts: pages alternate left and right. */
	private int textX(int index) {
		return index % 2 == 0 ? left() + TEXT_LEFT : left() + PAGE_WIDTH + TEXT_RIGHT_PAGE;
	}

	private void turnTo(int target) {
		page = Math.clamp(target, 0, pages.size() - 1) & ~1;
		updateButtons();
	}

	private void updateButtons() {
		back.visible = page > 0;
		forward.visible = page + 2 < pages.size();

		for (LinkButton link : links) {
			link.visible = (link.page & ~1) == page;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int left = left();
		int top = top();

		// The left page is the book page flipped, by drawing it with its texture coordinates running backwards.
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK, left, top, PAGE_U + PAGE_WIDTH, PAGE_V, PAGE_WIDTH, PAGE_HEIGHT, -PAGE_WIDTH, PAGE_HEIGHT, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK, left + PAGE_WIDTH, top, PAGE_U, PAGE_V, PAGE_WIDTH, PAGE_HEIGHT, 256, 256);

		ItemStack hovered = ItemStack.EMPTY;
		textTooltip = null;

		for (int index = page; index < page + 2 && index < pages.size(); index++) {
			int x = textX(index);
			int y = top + TEXT_TOP;

			for (Row row : pages.get(index)) {
				ItemStack hit = row.draw(this, graphics, x, y, mouseX, mouseY);
				hovered = hit.isEmpty() ? hovered : hit;
				y += row.height(this);
			}

			String number = String.valueOf(index + 1);
			graphics.text(font, number, x + (TEXT_WIDTH - font.width(number)) / 2, top + NUMBER_Y, INK_LIGHT, false);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (textTooltip != null) {
			graphics.setTooltipForNextFrame(font, textTooltip, Optional.empty(), mouseX, mouseY);
		} else if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
		}
	}

	private void buildPages() {
		Optional<CookingService> cooking = Optional.ofNullable(minecraft.level).map(level -> CookingService.create(level.registryAccess()));
		int maxIngredients = cooking.map(CookingService::maxIngredients).orElse(5);
		List<DishType> dishTypes = cooking.map(CookingService::dishTypes).orElse(List.of());
		ItemStack pot = stack(ModBlocks.COOKING_POT.asItem());

		List<List<Row>> steps = new ArrayList<>();
		steps.add(step("pot", new Picture(new Pile(pot, stack(Items.CAMPFIRE)), Part.OR, small(Items.FURNACE), small(Items.MAGMA_BLOCK)),
				Component.translatable("guide.dynamic_cooking.pot.text"), null));
		steps.add(step("add", new Picture(small(Items.CARROT), small(Items.POTATO), small(Items.BEEF), Part.ARROW, big(pot)),
				Component.translatable("guide.dynamic_cooking.add.text", maxIngredients), Component.translatable("guide.dynamic_cooking.add.tip")));
		steps.add(step("water", new Picture(big(stack(Items.WATER_BUCKET)), Part.ARROW, big(pot)),
				Component.translatable("guide.dynamic_cooking.water.text"), Component.translatable("guide.dynamic_cooking.water.tip")));
		steps.add(step("cook", new Picture(big(pot), Part.ARROW, big(stack(Items.BELL))),
				Component.translatable("guide.dynamic_cooking.cook.text"), null));
		steps.add(step("serve", new Picture(small(Items.BOWL), small(Items.GLASS_BOTTLE), small(Items.BUCKET), Part.ARROW, big(stack(ModItems.SOUP))),
				Component.translatable("guide.dynamic_cooking.serve.text"), Component.translatable("guide.dynamic_cooking.serve.tip")));
		steps.add(step("simmer", new Picture(big(stack(ModItems.SOUP)), Part.ARROW, big(stack(ModItems.STEW))),
				Component.translatable("guide.dynamic_cooking.simmer.text"), Component.translatable("guide.dynamic_cooking.simmer.tip")));
		steps.add(step("burn", new Picture(big(pot), Part.ARROW, big(stack(ModItems.DUBIOUS_MUSH))),
				Component.translatable("guide.dynamic_cooking.burn.text"), Component.translatable("guide.dynamic_cooking.burn.tip")));
		int crafted = steps.size();
		steps.add(step("craft", new Picture(new Grid(Map.of(0, stack(Items.BREAD), 4, stack(Items.COOKED_BEEF), 8, stack(Items.BREAD))), Part.ARROW, big(stack(ModItems.SANDWICH))),
				Component.translatable("guide.dynamic_cooking.craft.text"), null));
		steps.add(step("raw", new Picture(small(ModItems.SKEWER), Part.ARROW, small(Items.FURNACE), small(Items.SMOKER), small(Items.CAMPFIRE)),
				Component.translatable("guide.dynamic_cooking.raw.text"), Component.translatable("guide.dynamic_cooking.raw.tip")));
		steps.add(step("mix", new Picture(small(Items.SUGAR), Part.PLUS, small(Items.STICK), Part.ARROW, big(stack(ModItems.DUBIOUS_MUSH))),
				Component.translatable("guide.dynamic_cooking.mix.text"), Component.translatable("guide.dynamic_cooking.mix.tip")));
		steps.add(step("effects", new Picture(small(Items.GOLDEN_APPLE), Part.PLUS, small(Items.GOLDEN_CARROT), Part.ARROW, big(stack(ModItems.PIE))),
				Component.translatable("guide.dynamic_cooking.effects.text"), Component.translatable("guide.dynamic_cooking.effects.tip")));

		// The title page and contents come first, then the steps, then the dishes.
		int firstStep = 2;
		int craftedPage = firstStep;

		for (int i = 0; i < crafted; i++) {
			craftedPage += split(steps.get(i)).size();
		}

		List<String> dishIds = dishTypes.stream().map(DishType::item).toList();
		pages.add(List.of(new Cover(pot, stack(Items.CAMPFIRE))));
		pages.add(List.of(
				new Title(Component.translatable("guide.dynamic_cooking.contents")),
				new Entry(pot, Component.translatable("guide.dynamic_cooking.contents.basics"), firstStep),
				new Entry(stack(Items.CRAFTING_TABLE), Component.translatable("guide.dynamic_cooking.contents.crafted"), craftedPage),
				new Heading(Component.translatable("guide.dynamic_cooking.contents.dishes")),
				new DishGrid(dishIds)));
		steps.forEach(rows -> pages.addAll(split(rows)));

		cooking.ifPresent(service -> {
			Map<String, ItemStack> examples = DishRelations.examples(service, GuideScreen::itemStack);

			for (DishType type : dishTypes) {
				DishRelations relations = DishRelations.of(service, type, examples, matcher -> items(service, matcher), GuideScreen::matcherText)
						.withSimmering(type, dishTypes);
				dishPages.put(type.item(), pages.size());
				pages.addAll(split(dishPage(service, type, relations)));
			}
		});
	}

	/** Lays rows out on one page, or several when a data pack dish has more rows than fit on one. */
	private List<List<Row>> split(List<Row> rows) {
		List<List<Row>> out = new ArrayList<>();
		List<Row> current = new ArrayList<>();
		int used = 0;

		for (Row row : rows) {
			if (!current.isEmpty() && used + row.height(this) > TEXT_HEIGHT) {
				out.add(current);
				current = new ArrayList<>();
				used = 0;
			}

			current.add(row);
			used += row.height(this);
		}

		out.add(current);
		return out;
	}

	private static List<Row> step(String key, Picture picture, Component text, Component tip) {
		List<Row> rows = new ArrayList<>(List.of(new Title(Component.translatable("guide.dynamic_cooking." + key + ".title")), picture, new Caption(text, INK)));

		if (tip != null) {
			rows.add(new Gap(4));
			rows.add(new Caption(tip, INK_LIGHT));
		}

		return rows;
	}

	private List<Row> dishPage(CookingService cooking, DishType type, DishRelations relations) {
		List<Row> rows = new ArrayList<>();
		ItemStack dish = itemStack(type.item());
		List<Component> about = new ArrayList<>(List.of(Component.translatable("guide.dynamic_cooking.method." + type.method().id())));

		if (type.method() != CookingMethod.CRAFTING && type.servedWith().isPresent()) {
			about.add(Vessel.of(type.servedWith().get()).isPresent()
					? Component.translatable(type.liquid() ? "guide.dynamic_cooking.served_runny" : "guide.dynamic_cooking.served_thick")
					: Component.translatable("guide.dynamic_cooking.served_with", itemStack(type.servedWith().get()).getHoverName()));
		}

		if (type.makes() > 1) {
			about.add(Component.translatable("guide.dynamic_cooking.makes", type.makes()));
		}

		if (type.melts()) {
			about.add(Component.translatable("guide.dynamic_cooking.melts"));
		}

		rows.add(new DishHeader(dish, dish.getHoverName(), about));

		for (Requirement requirement : type.requires()) {
			Component label = matcherText(requirement.matcher());

			if (requirement.count() > 1) {
				label = Component.translatable("guide.dynamic_cooking.count", requirement.count(), label);
			}

			rows.add(new Need(items(cooking, requirement.matcher()), label, INK));
		}

		// Extras: flavors the dish takes on top of what it needs, minus anything it refuses.
		Set<String> required = new HashSet<>();
		type.requires().forEach(requirement -> required.addAll(requirement.matcher().roles()));
		Set<String> forbidden = new HashSet<>();
		type.forbids().forEach(matcher -> forbidden.addAll(matcher.roles()));
		List<String> extras = type.flavorRoles().stream().filter(role -> !required.contains(role) && !forbidden.contains(role)).sorted().toList();

		if (!extras.isEmpty()) {
			Matcher matcher = new Matcher(Set.of(), Set.copyOf(extras));
			rows.add(new Need(items(cooking, matcher), Component.translatable("guide.dynamic_cooking.extras", matcherText(matcher)), INK_LIGHT));
		}

		if (!relations.related().isEmpty()) {
			rows.add(new Related(relations.related()));
		}

		// Only what spoils the dish: a refused ingredient that turns it into another dish is shown as related instead.
		Set<String> spoiling = new TreeSet<>(forbidden);

		if (relations.probed()) {
			spoiling.retainAll(relations.spoils());
		}

		if (!spoiling.isEmpty()) {
			Matcher matcher = new Matcher(Set.of(), spoiling);
			rows.add(new Need(items(cooking, matcher), Component.translatable("guide.dynamic_cooking.forbids", matcherText(matcher)), INK_RED));
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

	static Component joinOr(List<Component> options) {
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

	static Component roleName(String role) {
		String fallback = role.isEmpty() ? role : Character.toUpperCase(role.charAt(0)) + role.substring(1).replace('_', ' ');
		return Component.translatableWithFallback("role.dynamic_cooking." + role, fallback);
	}

	static ItemStack itemStack(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	private static ItemStack stack(Item item) {
		return new ItemStack(item);
	}

	private static Part small(Item item) {
		return new Icon(stack(item), 1);
	}

	private static Part big(ItemStack stack) {
		return new Icon(stack, 2);
	}

	private List<FormattedCharSequence> lines(Component text, int width) {
		return font.split(text, width);
	}

	/** Draws an item icon, scaled up for pictures, and returns it if the mouse is over it. */
	private static ItemStack drawItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int scale, int mouseX, int mouseY) {
		if (scale == 1) {
			graphics.item(stack, x, y);
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(scale);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();
		}

		int size = 16 * scale;
		boolean over = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
		return over ? stack : ItemStack.EMPTY;
	}

	/** A slot with one item in it; lists of items take turns, a second each. */
	private static ItemStack drawSlot(GuiGraphicsExtractor graphics, List<ItemStack> items, int x, int y, int mouseX, int mouseY) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18);

		if (items.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack shown = items.get((int) (System.currentTimeMillis() / 1000 % items.size()));
		return drawItem(graphics, shown, x + 1, y + 1, 1, mouseX, mouseY);
	}

	private void centered(GuiGraphicsExtractor graphics, FormattedCharSequence line, int x, int y, int color) {
		graphics.text(font, line, x + (TEXT_WIDTH - font.width(line)) / 2, y, color, false);
	}

	/** A small ornament under a title: a line either side of a dot. */
	private static void rule(GuiGraphicsExtractor graphics, int x, int y) {
		int middle = x + TEXT_WIDTH / 2;
		graphics.fill(middle - 22, y, middle - 3, y + 1, RULE);
		graphics.fill(middle + 3, y, middle + 22, y + 1, RULE);
		graphics.fill(middle - 1, y - 1, middle + 1, y + 2, RULE);
	}

	private sealed interface Row permits Cover, Title, Heading, Picture, Caption, Gap, Entry, DishGrid, DishHeader, Need, Related {
		int height(GuideScreen screen);

		/** Draws the row and returns the item under the mouse, or an empty stack. */
		ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY);

		/** The clickable parts of the row when drawn at x, y. */
		default List<Link> links(GuideScreen screen, int x, int y) {
			return List.of();
		}
	}

	/** The first page: the guide's name over a pot on a campfire, drawn large. */
	private record Cover(ItemStack pot, ItemStack fire) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return TEXT_HEIGHT;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			Component title = Component.translatable("guide.dynamic_cooking.title").withStyle(ChatFormatting.BOLD);
			screen.centered(graphics, title.getVisualOrderText(), x, y + 6, INK);
			rule(graphics, x, y + 19);

			int pictureX = x + TEXT_WIDTH / 2 - 24;
			ItemStack hovered = drawItem(graphics, fire, pictureX, y + 54, 3, mouseX, mouseY);
			ItemStack hit = drawItem(graphics, pot, pictureX, y + 24, 3, mouseX, mouseY);
			hovered = hit.isEmpty() ? hovered : hit;

			int lineY = y + 110;

			for (FormattedCharSequence line : screen.lines(Component.translatable("guide.dynamic_cooking.tagline"), TEXT_WIDTH - 8)) {
				screen.centered(graphics, line, x, lineY, INK_LIGHT);
				lineY += LINE;
			}

			return hovered;
		}
	}

	/** A page title, centred, with an ornament under it. */
	private record Title(Component text) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return 20;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			screen.centered(graphics, text.copy().withStyle(ChatFormatting.BOLD).getVisualOrderText(), x, y, INK);
			rule(graphics, x, y + 12);
			return ItemStack.EMPTY;
		}
	}

	/** A bold heading inside a page. */
	private record Heading(Component text) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return 14;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			graphics.text(screen.font, text.copy().withStyle(ChatFormatting.BOLD), x, y + 3, INK, false);
			return ItemStack.EMPTY;
		}
	}

	/** Centred lines of text. */
	private record Caption(Component text, int color) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return screen.lines(text, TEXT_WIDTH).size() * LINE;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			for (FormattedCharSequence line : screen.lines(text, TEXT_WIDTH)) {
				screen.centered(graphics, line, x, y, color);
				y += LINE;
			}

			return ItemStack.EMPTY;
		}
	}

	private record Gap(int size) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return size;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			return ItemStack.EMPTY;
		}
	}

	/** One piece of a picture. */
	private sealed interface Part permits Icon, Pile, Grid, Sign {
		Part ARROW = new Sign("→");
		Part PLUS = new Sign("+");
		Part OR = new Sign("or");

		int width(GuideScreen screen);

		/** Draws the part, vertically centred on middle, and returns the item under the mouse. */
		ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int middle, int mouseX, int mouseY);
	}

	private record Icon(ItemStack stack, int scale) implements Part {
		@Override
		public int width(GuideScreen screen) {
			return 16 * scale;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int middle, int mouseX, int mouseY) {
			return drawItem(graphics, stack, x, middle - 8 * scale, scale, mouseX, mouseY);
		}
	}

	/** One item sitting on another, both large: the pot on its fire. */
	private record Pile(ItemStack top, ItemStack bottom) implements Part {
		@Override
		public int width(GuideScreen screen) {
			return 32;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int middle, int mouseX, int mouseY) {
			ItemStack below = drawItem(graphics, bottom, x, middle - 3, 2, mouseX, mouseY);
			ItemStack above = drawItem(graphics, top, x, middle - 27, 2, mouseX, mouseY);
			return above.isEmpty() ? below : above;
		}
	}

	/** A crafting grid with items in some of its nine slots, numbered left to right, top to bottom. */
	private record Grid(Map<Integer, ItemStack> slots) implements Part {
		@Override
		public int width(GuideScreen screen) {
			return 54;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int middle, int mouseX, int mouseY) {
			ItemStack hovered = ItemStack.EMPTY;

			for (int i = 0; i < 9; i++) {
				ItemStack item = slots.get(i);
				ItemStack hit = drawSlot(graphics, item == null ? List.of() : List.of(item), x + i % 3 * 18, middle - 27 + i / 3 * 18, mouseX, mouseY);
				hovered = hit.isEmpty() ? hovered : hit;
			}

			return hovered;
		}
	}

	/** An arrow, plus sign or word between items. The arrow is drawn rather than typed, to match the pixel art. */
	private record Sign(String text) implements Part {
		private boolean isArrow() {
			return text.equals("→");
		}

		@Override
		public int width(GuideScreen screen) {
			return isArrow() ? 10 : screen.font.width(text);
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int middle, int mouseX, int mouseY) {
			if (isArrow()) {
				graphics.fill(x, middle - 1, x + 7, middle, INK_LIGHT);

				for (int i = 0; i < 4; i++) {
					graphics.fill(x + 6 + i, middle - 4 + i, x + 7 + i, middle + 3 - i, INK_LIGHT);
				}
			} else {
				graphics.text(screen.font, text, x, middle - 4, INK_LIGHT, false);
			}

			return ItemStack.EMPTY;
		}
	}

	/** A step's illustration: items and signs in a row, centred in a band under the title. */
	private record Picture(List<Part> parts) implements Row {
		private static final int GAP = 4;

		Picture(Part... parts) {
			this(List.of(parts));
		}

		@Override
		public int height(GuideScreen screen) {
			return 58;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			int total = parts.stream().mapToInt(part -> part.width(screen)).sum() + GAP * (parts.size() - 1);
			int partX = x + (TEXT_WIDTH - total) / 2;
			ItemStack hovered = ItemStack.EMPTY;

			for (Part part : parts) {
				ItemStack hit = part.draw(screen, graphics, partX, y + 28, mouseX, mouseY);
				hovered = hit.isEmpty() ? hovered : hit;
				partX += part.width(screen) + GAP;
			}

			return hovered;
		}
	}

	/** A contents line: icon, name, dotted leader and page number. The whole line is a link. */
	private record Entry(ItemStack icon, Component name, int target) implements Row {
		@Override
		public int height(GuideScreen screen) {
			return 19;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			graphics.text(screen.font, name, x + 20, y + 4, INK, false);
			String number = String.valueOf(target + 1);
			int numberX = x + TEXT_WIDTH - screen.font.width(number);
			graphics.text(screen.font, number, numberX, y + 4, INK_LIGHT, false);

			for (int dotX = x + 22 + screen.font.width(name); dotX < numberX - 3; dotX += 3) {
				graphics.fill(dotX, y + 11, dotX + 1, y + 12, RULE);
			}

			return drawItem(graphics, icon, x, y, 1, mouseX, mouseY);
		}

		@Override
		public List<Link> links(GuideScreen screen, int x, int y) {
			return List.of(new Link(x - 1, y - 1, TEXT_WIDTH + 2, 18, target, name));
		}
	}

	/** Every dish as a slot to click, six to a row. */
	private record DishGrid(List<String> dishes) implements Row {
		private static final int COLUMNS = 6;

		@Override
		public int height(GuideScreen screen) {
			return (dishes.size() + COLUMNS - 1) / COLUMNS * 18 + 6;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			ItemStack hovered = ItemStack.EMPTY;

			for (int i = 0; i < dishes.size(); i++) {
				ItemStack hit = drawSlot(graphics, List.of(itemStack(dishes.get(i))), slotX(x, i), slotY(y, i), mouseX, mouseY);
				hovered = hit.isEmpty() ? hovered : hit;
			}

			return hovered;
		}

		@Override
		public List<Link> links(GuideScreen screen, int x, int y) {
			List<Link> out = new ArrayList<>();

			for (int i = 0; i < dishes.size(); i++) {
				String dish = dishes.get(i);
				out.add(new Link(slotX(x, i) + 1, slotY(y, i) + 1, 16, 16, screen.dishPages.getOrDefault(dish, 1), itemStack(dish).getHoverName()));
			}

			return out;
		}

		private static int slotX(int x, int i) {
			return x + (TEXT_WIDTH - COLUMNS * 18) / 2 + i % COLUMNS * 18;
		}

		private static int slotY(int y, int i) {
			return y + i / COLUMNS * 18;
		}
	}

	/** The top of a dish page: the dish drawn large, its name in bold and how it is made, then a rule. */
	private record DishHeader(ItemStack dish, Component name, List<Component> about) implements Row {
		private static final int TEXT_X = 37;

		private List<FormattedCharSequence> titleLines(GuideScreen screen) {
			return screen.lines(name.copy().withStyle(ChatFormatting.BOLD), TEXT_WIDTH - TEXT_X);
		}

		private List<FormattedCharSequence> lines(GuideScreen screen) {
			List<FormattedCharSequence> out = new ArrayList<>(titleLines(screen));
			about.forEach(line -> out.addAll(screen.lines(line, TEXT_WIDTH - TEXT_X)));
			return out;
		}

		@Override
		public int height(GuideScreen screen) {
			return Math.max(32, lines(screen).size() * LINE) + 8;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			List<FormattedCharSequence> lines = lines(screen);
			int titleLines = titleLines(screen).size();
			int lineY = y + Math.max(0, (32 - lines.size() * LINE) / 2) + 1;

			for (int i = 0; i < lines.size(); i++) {
				graphics.text(screen.font, lines.get(i), x + TEXT_X, lineY, i < titleLines ? INK : INK_LIGHT, false);
				lineY += LINE;
			}

			int ruleY = y + height(screen) - 5;
			graphics.fill(x, ruleY, x + TEXT_WIDTH, ruleY + 1, RULE);
			return drawItem(graphics, dish, x, y, 2, mouseX, mouseY);
		}
	}

	/** One line of a recipe: a slot showing what counts, in turn, and what it is. Long labels stop after three lines. */
	private record Need(List<ItemStack> items, Component label, int color) implements Row {
		private static final int TEXT_X = 23;
		private static final int MAX_LINES = 3;

		private List<FormattedCharSequence> lines(GuideScreen screen) {
			List<FormattedCharSequence> lines = screen.lines(label, TEXT_WIDTH - TEXT_X);

			if (lines.size() <= MAX_LINES) {
				return lines;
			}

			// Cut the last shown line short and end it with an ellipsis; the slot still shows every item.
			String last = plain(lines.get(MAX_LINES - 1));
			int room = TEXT_WIDTH - TEXT_X - screen.font.width("…");

			while (!last.isEmpty() && screen.font.width(last) > room) {
				last = last.substring(0, last.length() - 1);
			}

			List<FormattedCharSequence> out = new ArrayList<>(lines.subList(0, MAX_LINES - 1));
			out.add(Component.literal(last.stripTrailing() + "…").getVisualOrderText());
			return out;
		}

		private static String plain(FormattedCharSequence sequence) {
			StringBuilder out = new StringBuilder();
			sequence.accept((index, style, codePoint) -> {
				out.appendCodePoint(codePoint);
				return true;
			});
			return out.toString();
		}

		@Override
		public int height(GuideScreen screen) {
			return Math.max(19, lines(screen).size() * LINE + 1);
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			List<FormattedCharSequence> lines = lines(screen);
			// Centred beside the slot when one line, from the slot's top when more.
			int lineY = y + (height(screen) - lines.size() * LINE) / 2 + 1;

			for (FormattedCharSequence line : lines) {
				graphics.text(screen.font, line, x + TEXT_X, lineY, color, false);
				lineY += LINE;
			}

			return drawSlot(graphics, items, x, y, mouseX, mouseY);
		}
	}

	/** A clickable area on a page that turns to another page. */
	private record Link(int x, int y, int width, int height, int target, Component name) {
	}

	/**
	 * The dishes a change to this one makes, as small slots to click. Hovering one says how, like "Fruit/Veg or
	 * Mushroom instead of Meat/Fish" under Soup.
	 */
	private record Related(Map<String, List<Component>> dishes) implements Row {
		private static final int MAX = 4;

		private int slotX(GuideScreen screen, int x, int i) {
			return x + screen.font.width(Component.translatable("guide.dynamic_cooking.related")) + 4 + i * 18;
		}

		private List<String> shown() {
			return dishes.keySet().stream().limit(MAX).toList();
		}

		@Override
		public int height(GuideScreen screen) {
			return 19;
		}

		@Override
		public ItemStack draw(GuideScreen screen, GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
			graphics.text(screen.font, Component.translatable("guide.dynamic_cooking.related"), x, y + 5, INK_LIGHT, false);
			List<String> shown = shown();

			for (int i = 0; i < shown.size(); i++) {
				String dish = shown.get(i);
				ItemStack icon = itemStack(dish);

				if (!drawSlot(graphics, List.of(icon), slotX(screen, x, i), y, mouseX, mouseY).isEmpty()) {
					List<Component> lines = new ArrayList<>(List.of(icon.getHoverName()));
					dishes.get(dish).forEach(way -> lines.add(way.copy().withStyle(ChatFormatting.GRAY)));
					screen.textTooltip = lines;
				}
			}

			return ItemStack.EMPTY;
		}

		@Override
		public List<Link> links(GuideScreen screen, int x, int y) {
			List<Link> out = new ArrayList<>();
			List<String> shown = shown();

			for (int i = 0; i < shown.size(); i++) {
				String dish = shown.get(i);
				out.add(new Link(slotX(screen, x, i) + 1, y + 1, 16, 16, screen.dishPages.getOrDefault(dish, 1), itemStack(dish).getHoverName()));
			}

			return out;
		}
	}

	/** An invisible button over a link, lit up under the mouse. */
	private static final class LinkButton extends AbstractButton {
		final int page;
		private final Runnable open;

		LinkButton(int page, Link link, Runnable open) {
			super(link.x(), link.y(), link.width(), link.height(), link.name());
			this.page = page;
			this.open = open;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			if (isHoveredOrFocused()) {
				graphics.fill(getX(), getY(), getX() + width, getY() + height, HIGHLIGHT);
			}
		}

		@Override
		public void onPress(InputWithModifiers input) {
			open.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
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
