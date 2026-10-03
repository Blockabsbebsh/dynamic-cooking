package io.github.blockabsbebsh.dynamiccooking.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * A pot that sits on a heat source. Right-click with ingredients to add them, right-click with an empty hand to cook,
 * sneak and right-click with an empty hand to take the last ingredient back.
 */
public class CookingPotBlock extends BaseEntityBlock {
	public static final BooleanProperty COOKING = BooleanProperty.create("cooking");

	/** Blocks that can heat the pot when directly underneath it. Campfires must also be lit. */
	public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, DynamicCooking.id("heat_sources"));

	private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0);

	public CookingPotBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(COOKING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COOKING);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
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
			return InteractionResult.PASS;
		}

		// Anything that isn't an ingredient keeps its normal use, like placing a block next to the pot.
		if (!CookingService.create(level.registryAccess()).isIngredient(stack)) {
			return InteractionResult.PASS;
		}

		if (pot.isCooking()) {
			message(level, player, "busy");
			return InteractionResult.SUCCESS;
		}

		if (pot.isFull()) {
			message(level, player, "full", pot.capacity());
			return InteractionResult.SUCCESS;
		}

		if (!level.isClientSide()) {
			ItemStack ingredient = stack.copyWithCount(1);
			ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
			pot.add(ingredient);

			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);

				// Milk buckets give their bucket back, honey bottles their bottle.
				if (remainder != null) {
					give(player, remainder.create());
				}
			}

			level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.4f);
			message(level, player, "added", ingredient.getHoverName(), pot.count(), pot.capacity());
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

		if (player.isSecondaryUseActive()) {
			if (!level.isClientSide()) {
				ItemStack last = pot.removeLast();

				if (!last.isEmpty()) {
					give(player, last);
				}
			}

			return InteractionResult.SUCCESS;
		}

		if (pot.isEmpty()) {
			message(level, player, "empty");
			return InteractionResult.SUCCESS;
		}

		if (!hasHeat(level, pos)) {
			message(level, player, "no_heat");
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
		if (!state.getValue(COOKING)) {
			return;
		}

		double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
		level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.02, 0.0);

		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
		}
	}

	private static void message(Level level, Player player, String key, Object... args) {
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.dynamic_cooking.pot." + key, args));
		}
	}

	private static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
