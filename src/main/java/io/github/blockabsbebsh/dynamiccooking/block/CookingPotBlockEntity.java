package io.github.blockabsbebsh.dynamiccooking.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * Holds the ingredients in the order they were added and runs the cooking timer.
 */
public class CookingPotBlockEntity extends BlockEntity {
	/** How long cooking takes, in ticks. */
	public static final int COOK_TICKS = 3 * 20;

	private final NonNullList<ItemStack> items = NonNullList.withSize(CookingRules.DEFAULT.maxIngredients(), ItemStack.EMPTY);
	private int cookTicksLeft;

	public CookingPotBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COOKING_POT, pos, state);
	}

	public int capacity() {
		return items.size();
	}

	public int count() {
		return (int) items.stream().filter(stack -> !stack.isEmpty()).count();
	}

	public boolean isEmpty() {
		return count() == 0;
	}

	public boolean isFull() {
		return count() >= capacity();
	}

	public boolean isCooking() {
		return cookTicksLeft > 0;
	}

	public List<ItemStack> contents() {
		return items.stream().filter(stack -> !stack.isEmpty()).toList();
	}

	public void add(ItemStack ingredient) {
		items.set(count(), ingredient);
		setChanged();
	}

	public ItemStack removeLast() {
		int last = count() - 1;

		if (last < 0) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = items.get(last);
		items.set(last, ItemStack.EMPTY);
		setChanged();
		return stack;
	}

	public void startCooking() {
		cookTicksLeft = COOK_TICKS;
		setCookingState(true);
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
		if (!pot.isCooking()) {
			return;
		}

		// Taking the heat away stops cooking but keeps the ingredients.
		if (!CookingPotBlock.hasHeat(level, pos)) {
			pot.cookTicksLeft = 0;
			pot.setCookingState(false);
			pot.setChanged();
			return;
		}

		if (--pot.cookTicksLeft > 0) {
			return;
		}

		ItemStack dish;

		try {
			dish = CookingService.create(level.registryAccess()).cook(pot.contents());
		} catch (IllegalArgumentException e) {
			// Data packs changed since the ingredients went in. Give everything back rather than lose it.
			DynamicCooking.LOGGER.warn("Cooking pot at {} could not cook: {}", pos, e.getMessage());
			Containers.dropContents(level, pos, pot.items);
			pot.items.clear();
			pot.setCookingState(false);
			pot.setChanged();
			return;
		}

		pot.items.clear();
		pot.setCookingState(false);
		pot.setChanged();
		Block.popResourceFromFace(level, pos, Direction.UP, dish);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
	}

	private void setCookingState(boolean cooking) {
		if (level != null && getBlockState().getValue(CookingPotBlock.COOKING) != cooking) {
			level.setBlock(worldPosition, getBlockState().setValue(CookingPotBlock.COOKING, cooking), Block.UPDATE_ALL);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		cookTicksLeft = input.getIntOr("cook_ticks_left", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		ContainerHelper.saveAllItems(output, items);
		output.putInt("cook_ticks_left", cookTicksLeft);
		super.saveAdditional(output);
	}
}
