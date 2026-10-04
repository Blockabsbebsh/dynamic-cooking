package io.github.blockabsbebsh.dynamiccooking.dish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.blockabsbebsh.dynamiccooking.component.DishContents;
import io.github.blockabsbebsh.dynamiccooking.component.ModComponents;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingInput;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingMethod;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingResolver;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishResult;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishType;
import io.github.blockabsbebsh.dynamiccooking.cooking.IngredientProfile;
import io.github.blockabsbebsh.dynamiccooking.cooking.PotColors;
import io.github.blockabsbebsh.dynamiccooking.registry.ModRegistries;

/**
 * Connects the cooking rules to the game: looks up the loaded data, cooks a list of item stacks, and builds the dish stack.
 * The cooking pot, the crafting recipe and anything else that makes dishes goes through here.
 */
public final class CookingService {
	private final Map<String, IngredientProfile> profiles;
	private final CookingResolver resolver;

	private CookingService(Map<String, IngredientProfile> profiles, CookingResolver resolver) {
		this.profiles = profiles;
		this.resolver = resolver;
	}

	/**
	 * Builds a service from the currently loaded data packs. Cheap enough to call per cook.
	 */
	public static CookingService create(HolderLookup.Provider registries) {
		Map<String, IngredientProfile> profiles = new HashMap<>();

		registries.lookupOrThrow(ModRegistries.INGREDIENT).listElements().map(Holder::value).forEach(profile -> {
			for (String item : profile.items()) {
				profiles.put(item, profile);
			}
		});

		List<DishType> dishTypes = registries.lookupOrThrow(ModRegistries.DISH_TYPE).listElements().map(Holder::value).toList();

		return new CookingService(profiles, new CookingResolver(dishTypes, CookingRules.DEFAULT, id -> Optional.ofNullable(profiles.get(id))));
	}

	public Optional<IngredientProfile> profile(Item item) {
		return Optional.ofNullable(profiles.get(BuiltInRegistries.ITEM.getKey(item).toString()));
	}

	/** Whether the pot should accept this item at all. */
	public boolean isIngredient(ItemStack stack) {
		return !stack.isEmpty() && profile(stack.getItem()).isPresent();
	}

	/**
	 * Whether this item serves runny dishes out of the pot, like a bowl. Such items never go into the pot as an
	 * ingredient, so a bowl clicked on the pot too early doesn't end up cooked. Dubious Mush is served with one too.
	 */
	public boolean isServingItem(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

		return id.equals(resolver.rules().fallbackServedWith()) || resolver.dishTypes().stream()
				.anyMatch(type -> type.method() == CookingMethod.POT && type.servedWith().filter(id::equals).isPresent());
	}

	/** Every dish type, in the order the pot and crafting table try them. */
	public List<DishType> dishTypes() {
		return resolver.dishTypes();
	}

	/** Every loaded ingredient profile, each once. */
	public List<IngredientProfile> profiles() {
		return profiles.values().stream().distinct().toList();
	}

	public int maxIngredients() {
		return resolver.rules().maxIngredients();
	}

	/**
	 * Cooks one of each given stack in the pot. Every stack must be an ingredient, see {@link #isIngredient}.
	 */
	public DishResult resolve(List<ItemStack> ingredients) {
		List<CookingInput> inputs = new ArrayList<>();

		for (ItemStack stack : ingredients) {
			inputs.add(input(stack).orElseThrow(() -> new IllegalArgumentException(stack.getItem() + " is not a cooking ingredient")));
		}

		return resolver.resolve(inputs);
	}

	/**
	 * Whether this is a finished dish with raw ingredients the pot can cook, like a skewer made with raw chicken.
	 * The pot takes such dishes on their own and gives them back cooked.
	 */
	public boolean isRecookable(ItemStack stack) {
		return dishInputs(stack).filter(resolver::canRecook).isPresent();
	}

	/** The dish cooked again so its raw ingredients are cooked, or the same dish if nothing in it can be cooked. */
	public ItemStack recook(ItemStack dish) {
		String item = BuiltInRegistries.ITEM.getKey(dish.getItem()).toString();

		return dishInputs(dish)
				.flatMap(inputs -> resolver.recook(item, inputs))
				.map(result -> DishFactory.create(result).copyWithCount(1))
				.orElseGet(() -> dish.copyWithCount(1));
	}

	/** Whether an ingredient only holds the dish, like a water bucket or a bowl, rather than being food in it. */
	public boolean isContainer(Item item) {
		return profile(item).filter(found -> found.roles().stream().anyMatch(resolver.rules().containerRoles()::contains)).isPresent();
	}

	/** The item that serves the fallback dish out of the pot, like a bowl. */
	public String fallbackServedWith() {
		return resolver.rules().fallbackServedWith();
	}

	/** How long cooking takes, in ticks. */
	public int cookTicks() {
		return resolver.rules().cookSeconds() * 20;
	}

	/**
	 * What a finished dish does while it waits on the heat, in ticks from when it was ready.
	 *
	 * @param simmerTicks when it simmers into another dish, like soup into stew, or 0 if it doesn't
	 * @param burnTicks   when it burns into the fallback dish, or 0 if it can't, like the fallback dish itself
	 */
	public record DishTimes(int simmerTicks, int burnTicks) {
	}

	public DishTimes times(ItemStack dish) {
		String id = BuiltInRegistries.ITEM.getKey(dish.getItem()).toString();

		if (id.equals(resolver.rules().fallbackItem())) {
			return new DishTimes(0, 0);
		}

		Optional<DishType> type = dishType(id);
		int simmer = type.filter(found -> found.simmersInto().isPresent()).map(found -> found.simmerSeconds() * 20).orElse(0);
		int burn = type.map(DishType::burnSeconds).filter(seconds -> seconds > 0).orElse(resolver.rules().burnSeconds()) * 20;
		return new DishTimes(simmer, burn);
	}

	/** The dish a finished one turns into after simmering, like soup into stew, with the same ingredients. */
	public Optional<ItemStack> simmer(ItemStack dish) {
		String id = BuiltInRegistries.ITEM.getKey(dish.getItem()).toString();

		return dishType(id).flatMap(DishType::simmersInto)
				.flatMap(into -> dishInputs(dish).flatMap(inputs -> resolver.simmer(into, inputs)))
				.map(result -> DishFactory.create(result).copyWithCount(1));
	}

	/** What a dish left too long on the heat becomes: the fallback dish, with its ingredients' side effects. */
	public ItemStack burn(ItemStack dish) {
		return DishFactory.create(resolver.burn(dishInputs(dish).orElse(List.of()))).copyWithCount(1);
	}

	private Optional<DishType> dishType(String item) {
		return resolver.dishTypes().stream().filter(type -> type.item().equals(item)).findFirst();
	}

	/** The ingredients a dish was made from, if they are all still known ingredients. */
	private Optional<List<CookingInput>> dishInputs(ItemStack dish) {
		DishContents contents = dish.get(ModComponents.DISH);

		if (contents == null || contents.ingredients().isEmpty()) {
			return Optional.empty();
		}

		List<CookingInput> inputs = new ArrayList<>();

		for (String id : contents.ingredients()) {
			IngredientProfile profile = profiles.get(id);

			if (profile == null) {
				return Optional.empty();
			}

			inputs.add(new CookingInput(id, profile));
		}

		return Optional.of(inputs);
	}

	/**
	 * Cooks what is in the pot: loose ingredients, copies of one raw dish to cook again, or copies of one raw ingredient,
	 * which simply come out cooked, like raw potatoes as baked potatoes.
	 * The result's {@link PotResult#servings()} is how many dishes come out.
	 */
	public PotResult cookPot(List<ItemStack> contents) {
		if (!contents.isEmpty() && isRecookable(contents.getFirst())) {
			ItemStack dish = recook(contents.getFirst());
			List<ItemStack> cooked = contents.stream().map(stack -> dish).toList();
			return new PotResult(dish, contents.size(), Optional.empty(), false, liquidColor(cooked), colors(cooked));
		}

		Optional<Item> cookedAlone = inputs(contents).flatMap(resolver::cookedAlone).flatMap(id -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)));

		if (cookedAlone.isPresent()) {
			List<ItemStack> cooked = contents.stream().map(stack -> new ItemStack(cookedAlone.get())).toList();
			return new PotResult(cooked.getFirst(), cooked.size(), Optional.empty(), false, liquidColor(cooked), colors(cooked));
		}

		DishResult result = resolve(contents);
		// Colors come from the cooked ingredients, so raw meat chunks turn brown when the dish is done.
		List<ItemStack> cooked = result.ingredients().stream()
				.map(id -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY))
				.toList();
		// The pot hands out a batch of several, like kelp rolls, one at a time.
		ItemStack dish = DishFactory.create(result);
		return new PotResult(dish.copyWithCount(1), result.servings() * dish.getCount(), result.servedWith(), result.liquid(), liquidColor(cooked), colors(cooked));
	}

	/** One cooking input per stack, or empty if any stack isn't an ingredient. */
	private Optional<List<CookingInput>> inputs(List<ItemStack> stacks) {
		List<CookingInput> inputs = new ArrayList<>();

		for (ItemStack stack : stacks) {
			Optional<CookingInput> input = input(stack);

			if (input.isEmpty()) {
				return Optional.empty();
			}

			inputs.add(input.get());
		}

		return Optional.of(inputs);
	}

	/**
	 * What came out of the pot.
	 *
	 * @param dish        one dish
	 * @param servings    how many of it the pot holds
	 * @param servedWith  item id that takes it out, or empty for an empty hand
	 * @param liquid      whether it is runny
	 * @param liquidColor the color of the cooked contents
	 * @param colors      one color per ingredient, after cooking, for the chunks drawn in the pot
	 */
	public record PotResult(ItemStack dish, int servings, Optional<String> servedWith, boolean liquid, int liquidColor, List<Integer> colors) {
	}

	/** The color of the liquid in a pot holding these stacks. Stacks that aren't ingredients or dishes are skipped. */
	public int liquidColor(List<ItemStack> contents) {
		List<IngredientProfile> mixed = new ArrayList<>();

		for (ItemStack stack : contents) {
			input(stack).ifPresentOrElse(
					input -> mixed.add(input.profile()),
					() -> dishInputs(stack).ifPresent(inputs -> inputs.forEach(input -> mixed.add(input.profile())))
			);
		}

		return PotColors.mix(mixed);
	}

	/**
	 * One color per stack for the chunks floating in the pot. Ingredients without a flavor, like water or sugar, take the
	 * liquid's color so their chunk blends in; a dish takes the color of its main flavor.
	 */
	public List<Integer> colors(List<ItemStack> contents) {
		int liquid = liquidColor(contents);
		List<Integer> colors = new ArrayList<>();

		for (ItemStack stack : contents) {
			Optional<IngredientProfile> profile = input(stack).map(CookingInput::profile);

			if (profile.isPresent()) {
				colors.add(chunkColor(profile.get()).orElse(liquid));
			} else {
				Optional<Integer> mainFlavor = dishInputs(stack)
						.flatMap(inputs -> inputs.stream().map(input -> chunkColor(input.profile())).flatMap(Optional::stream).findFirst());
				colors.add(mainFlavor.orElse(liquid));
			}
		}

		return colors;
	}

	private static Optional<Integer> chunkColor(IngredientProfile profile) {
		return profile.flavor().isPresent() ? profile.color() : Optional.empty();
	}

	/**
	 * Finds the dish one of each given stack makes with the given method. Unlike the pot, there is no fallback:
	 * any stack that isn't an ingredient, or a mix no dish type fits, gives nothing.
	 */
	public Optional<DishResult> match(CookingMethod method, List<ItemStack> ingredients) {
		return inputs(ingredients).flatMap(inputs -> resolver.match(method, inputs));
	}

	public Optional<ItemStack> craft(List<ItemStack> ingredients) {
		return match(CookingMethod.CRAFTING, ingredients).map(DishFactory::create);
	}

	private Optional<CookingInput> input(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}

		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return Optional.ofNullable(profiles.get(id)).map(profile -> new CookingInput(id, profile));
	}
}
