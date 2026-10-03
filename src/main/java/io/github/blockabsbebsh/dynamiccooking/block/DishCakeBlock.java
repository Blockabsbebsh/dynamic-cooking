package io.github.blockabsbebsh.dynamiccooking.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cooked cake placed on the ground. Like a vanilla cake it is eaten a slice at a time by right-clicking it with an
 * empty hand, and each slice feeds the player and gives the cake's buff. The cake item it was placed from is kept in
 * {@link DishCakeBlockEntity}, so every slice has that cake's food value, buff and ingredient colors.
 */
public class DishCakeBlock extends BaseEntityBlock {
	public static final IntegerProperty BITES = BlockStateProperties.BITES;
	public static final int MAX_BITES = 6;
	/** Slices in a whole cake, the same as vanilla. */
	public static final int SLICES = MAX_BITES + 1;

	private static final VoxelShape[] SHAPES = new VoxelShape[SLICES];

	static {
		for (int bites = 0; bites < SLICES; bites++) {
			SHAPES[bites] = Block.box(1.0 + bites * 2, 0.0, 1.0, 15.0, 8.0, 15.0);
		}
	}

	public DishCakeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BITES, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BITES);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(BITES)];
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isSolid();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DishCakeBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (level.getBlockEntity(pos) instanceof DishCakeBlockEntity cake) {
			cake.setCake(stack);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.canEat(false)) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DishCakeBlockEntity cake) {
			player.awardStat(Stats.EAT_CAKE_SLICE);
			cake.feed(player);
			level.gameEvent(player, GameEvent.EAT, pos);

			int bites = state.getValue(BITES);

			if (bites < MAX_BITES) {
				level.setBlock(pos, state.setValue(BITES, bites + 1), Block.UPDATE_ALL);
			} else {
				level.removeBlock(pos, false);
				level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
			}
		}

		return InteractionResult.SUCCESS;
	}
}
