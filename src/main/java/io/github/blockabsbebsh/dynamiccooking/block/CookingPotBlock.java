package io.github.blockabsbebsh.dynamiccooking.block;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;
import io.github.blockabsbebsh.dynamiccooking.dish.DishServing;
import io.github.blockabsbebsh.dynamiccooking.dish.Vessel;

/**
 * A pot that sits on a heat source. Right-click with ingredients to add them, right-click with an empty hand to cook,
 * sneak and right-click with an empty hand to take the last ingredient back. Cooked dishes stay in the pot: runny ones
 * are taken out with their serving item, like stew with a bowl, the rest with an empty hand. A crafted dish with raw
 * ingredients, like a raw chicken skewer, can be put in on its own and cooked again.
 */
public class CookingPotBlock extends BaseEntityBlock {
	public static final BooleanProperty COOKING = BooleanProperty.create("cooking");
	/** How many ingredients are in the pot, which sets how high the liquid is drawn. */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, CookingRules.DEFAULT.maxIngredients());
	/** Whether the pot would make a runny dish like stew, drawn as liquid; otherwise the contents are a thick mash. */
	public static final BooleanProperty LIQUID = BooleanProperty.create("liquid");
	public static final EnumProperty<Legs> LEGS = EnumProperty.create("legs", Legs.class);
	/** A cooked dish is waiting in the pot. */
	public static final BooleanProperty READY = BooleanProperty.create("ready");

	/** Blocks that can heat the pot when directly underneath it. Ones that can be lit, like campfires and furnaces, must be lit. */
	public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, DynamicCooking.id("heat_sources"));

	/**
	 * How far the pot sinks into a campfire, in pixels. The campfire's flames are squashed on the client to end at the
	 * pot's bottom, so they never show through it.
	 */
	public static final int CAMPFIRE_DROP = 6;

	private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0);
	private static final VoxelShape SHAPE_ON_CAMPFIRE = Block.box(3.0, -CAMPFIRE_DROP, 3.0, 13.0, 8.0 - CAMPFIRE_DROP, 13.0);

	public CookingPotBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(COOKING, false)
				.setValue(FILL, 0)
				.setValue(LIQUID, false)
				.setValue(LEGS, Legs.NONE)
				.setValue(READY, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COOKING, FILL, LIQUID, LEGS, READY);
	}

	/** Height of the contents' surface above the pot's block, in blocks; lower when the pot sits in a campfire. */
	public static double surfaceY(BlockState state) {
		double drop = state.getValue(LEGS) == Legs.SHORT ? CAMPFIRE_DROP : 0;
		return (1.0 + state.getValue(FILL) * 1.2 - drop) / 16.0;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(LEGS, Legs.below(context.getLevel(), context.getClickedPos()));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return direction == Direction.DOWN ? state.setValue(LEGS, Legs.below(level, pos)) : state;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(LEGS) == Legs.SHORT ? SHAPE_ON_CAMPFIRE : SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CookingPotBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.COOKING_POT, CookingPotBlockEntity::serverTick);
	}

	public static boolean hasHeat(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());

		if (!below.is(HEAT_SOURCES)) {
			return false;
		}

		return !below.hasProperty(BlockStateProperties.LIT) || below.getValue(BlockStateProperties.LIT);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		CookingService cooking = CookingService.create(level.registryAccess());

		if (pot.isServedInContainer()) {
			Optional<Vessel> vessel = Vessel.of(stack.getItem());

			if (vessel.isPresent()) {
				return serve(level, pos, player, hand, stack, pot, vessel.get());
			}

			if (DishServing.isDishBucket(stack)) {
				return topUp(level, pos, player, hand, stack, pot);
			}
		}

		if (cooking.melts(stack)) {
			message(level, player, "melts");
			return InteractionResult.SUCCESS;
		}

		boolean servingItem = cooking.isServingItem(stack);
		boolean rawDish = cooking.isRecookable(stack);

		// Anything that isn't an ingredient acts like an empty hand, so the game goes on to useWithoutItem.
		if (!servingItem && !rawDish && !cooking.isIngredient(stack)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (pot.isCooking()) {
			message(level, player, "busy");
			return InteractionResult.SUCCESS;
		}

		if (pot.hasServing()) {
			messageReady(level, player, pot);
			return InteractionResult.SUCCESS;
		}

		// A bowl clicked on a pot with nothing to serve yet is kept, never cooked.
		if (servingItem) {
			message(level, player, "nothing_to_serve");
			return InteractionResult.SUCCESS;
		}

		if (pot.isFull()) {
			message(level, player, "full", pot.capacity());
			return InteractionResult.SUCCESS;
		}

		Optional<String> refused = pot.refuses(cooking, stack);

		if (refused.isPresent()) {
			message(level, player, refused.get());
			return InteractionResult.SUCCESS;
		}

		if (!level.isClientSide()) {
			ItemStack ingredient = stack.copyWithCount(1);
			ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
			pot.add(ingredient);

			// Water and milk buckets give their bucket back, honey bottles their bottle.
			if (remainder != null && !player.hasInfiniteMaterials()) {
				exchange(player, hand, stack, remainder.create());
			} else if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}

			level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.4f);
			message(level, player, "added", ingredient.getHoverName(), pot.count(), pot.capacity(), preview(cooking, pot));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
			return InteractionResult.PASS;
		}

		if (pot.isCooking()) {
			message(level, player, "busy");
			return InteractionResult.SUCCESS;
		}

		if (pot.isServedByHand()) {
			if (!level.isClientSide()) {
				ItemStack dish = pot.serve();
				give(player, dish);
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);

				if (pot.hasServing()) {
					message(level, player, "taken", dish.getHoverName(), pot.servingsLeft());
				}
			}

			return InteractionResult.SUCCESS;
		}

		if (pot.hasServing()) {
			messageReady(level, player, pot);
			return InteractionResult.SUCCESS;
		}

		if (player.isSecondaryUseActive()) {
			if (pot.isEmpty()) {
				message(level, player, "empty");
			} else if (!level.isClientSide()) {
				ItemStack last = pot.removeLast();
				message(level, player, "removed", last.getHoverName(), pot.count(), pot.capacity());
				give(player, last);
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.4f, 1.4f);
			}

			return InteractionResult.SUCCESS;
		}

		if (pot.isEmpty()) {
			message(level, player, "empty");
			return InteractionResult.SUCCESS;
		}

		if (!hasHeat(level, pos)) {
			if (!level.isClientSide()) {
				message(level, player, "no_heat", preview(CookingService.create(level.registryAccess()), pot));
			}

			return InteractionResult.SUCCESS;
		}

		if (!level.isClientSide()) {
			pot.startCooking();
			level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int fill = state.getValue(FILL);
		double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
		double y = pos.getY() + surfaceY(state);
		double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;

		if (state.getValue(READY)) {
			// A finished dish steams lightly until it is taken out, so it is easy to spot without hiding the pot.
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.WHITE_SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
			}

			if (level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot && pot.timeline(level.getGameTime()).heated()) {
				int scorch = pot.scorch();

				if (scorch > 0) {
					// Close to burning: dark smoke and sizzling, more with every step.
					for (int i = 0; i < scorch; i++) {
						level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
					}

					if (random.nextInt(6 - scorch) == 0) {
						level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.25f + 0.1f * scorch, 1.4f + random.nextFloat() * 0.3f, false);
					}
				} else if (pot.timeline(level.getGameTime()).simmerTicks() > 0) {
					// Soup on the heat simmers gently on its way to stew.
					level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.02, 0.0);
				}
			}
		} else if (state.getValue(COOKING)) {
			level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.02, 0.0);

			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.WHITE_SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
			}
		} else if (fill > 0 && random.nextInt(4) == 0 && hasHeat(level, pos)) {
			// A warm pot steams gently while it waits to be cooked.
			level.addParticle(ParticleTypes.WHITE_SMOKE, x, y + 0.1, z, 0.0, 0.02, 0.0);
		}
	}

	/** The name of the dish the pot would make right now, so players can tell if their recipe works before cooking. */
	private static Component preview(CookingService cooking, CookingPotBlockEntity pot) {
		try {
			return cooking.cookPot(pot.contents()).dish().getHoverName();
		} catch (IllegalArgumentException e) {
			return Component.translatable("message.dynamic_cooking.pot.unknown");
		}
	}

	private static void message(Level level, Player player, String key, Object... args) {
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.dynamic_cooking.pot." + key, args));
		}
	}

	/** Fills an empty bowl, bottle or bucket with a serving. A bottle only takes runny dishes. */
	private static InteractionResult serve(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, CookingPotBlockEntity pot, Vessel vessel) {
		if (vessel == Vessel.BOTTLE && !pot.isRunny()) {
			message(level, player, "too_thick");
			return InteractionResult.SUCCESS;
		}

		if (!level.isClientSide()) {
			ItemStack dish = DishServing.fill(pot.serve(), vessel, 1, pot.usualVessel());
			exchange(player, hand, stack, dish);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);

			if (pot.hasServing()) {
				message(level, player, "served", dish.getHoverName(), pot.servingsLeft());
			}
		}

		return InteractionResult.SUCCESS;
	}

	/** Adds a serving to a bucket of the same dish, up to three. */
	private static InteractionResult topUp(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack bucket, CookingPotBlockEntity pot) {
		int servings = DishServing.servings(bucket);

		if (!DishServing.sameDish(bucket, pot.serving())) {
			message(level, player, "different_dish");
		} else if (servings >= Vessel.BUCKET_SERVINGS) {
			message(level, player, "bucket_full", Vessel.BUCKET_SERVINGS);
		} else if (!level.isClientSide()) {
			ItemStack filled = DishServing.fill(pot.serve(), Vessel.BUCKET, servings + 1, pot.usualVessel());
			player.setItemInHand(hand, filled);
			level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
			message(level, player, "topped_up", filled.getHoverName(), servings + 1, Vessel.BUCKET_SERVINGS);
		}

		return InteractionResult.SUCCESS;
	}

	/** Tells the player a dish is waiting and what serves it. */
	private static void messageReady(Level level, Player player, CookingPotBlockEntity pot) {
		if (!level.isClientSide()) {
			player.sendOverlayMessage(readyMessage(pot));
		}
	}

	/** "Beef Stew is ready", with how to take it out. */
	public static Component readyMessage(CookingPotBlockEntity pot) {
		if (pot.isServedByHand()) {
			return Component.translatable("message.dynamic_cooking.pot.ready_hand", pot.serving().getHoverName());
		}

		return Component.translatable(pot.isRunny() ? "message.dynamic_cooking.pot.ready" : "message.dynamic_cooking.pot.ready_thick", pot.serving().getHoverName());
	}

	/** Uses up one of the held stack and hands back what it turned into, in the same hand when the stack ran out. */
	private static void exchange(Player player, InteractionHand hand, ItemStack used, ItemStack result) {
		if (player.hasInfiniteMaterials()) {
			give(player, result);
			return;
		}

		used.shrink(1);

		if (used.isEmpty()) {
			player.setItemInHand(hand, result);
		} else {
			give(player, result);
		}
	}

	private static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
