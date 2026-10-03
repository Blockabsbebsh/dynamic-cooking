package io.github.blockabsbebsh.dynamiccooking.block;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.cooking.CookingRules;
import io.github.blockabsbebsh.dynamiccooking.cooking.DishResult;
import io.github.blockabsbebsh.dynamiccooking.cooking.PotColors;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;
import io.github.blockabsbebsh.dynamiccooking.dish.DishFactory;

/**
 * Holds the ingredients in the order they were added and runs the cooking timer.
 * Dishes with a serving item, like stew and a bowl, stay in the pot once cooked until they are served.
 * The liquid color is synced to clients, which tint the liquid with it.
 */
public class CookingPotBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	/** How long cooking takes, in ticks. */
	public static final int COOK_TICKS = 3 * 20;

	private final NonNullList<ItemStack> items = NonNullList.withSize(CookingRules.DEFAULT.maxIngredients(), ItemStack.EMPTY);
	private int cookTicksLeft;
	private int liquidColor = PotColors.WATER;
	/** A cooked dish waiting to be served, or empty. */
	private ItemStack serving = ItemStack.EMPTY;
	private String servedWith = "";
	private int servingsLeft;

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

	public boolean hasServing() {
		return !serving.isEmpty() && servingsLeft > 0;
	}

	/** The dish waiting to be served, for its name. */
	public ItemStack serving() {
		return serving;
	}

	/** The item that serves the waiting dish, like a bowl. */
	public Optional<Item> servedWith() {
		return servedWith.isEmpty() ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(Identifier.parse(servedWith));
	}

	public boolean isServedWith(ItemStack stack) {
		return hasServing() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(servedWith);
	}

	public int servingsLeft() {
		return servingsLeft;
	}

	/** Takes one dish out of the pot. The pot empties after the last one. */
	public ItemStack serve() {
		if (!hasServing()) {
			return ItemStack.EMPTY;
		}

		ItemStack dish = serving.copyWithCount(1);

		if (--servingsLeft <= 0) {
			clearServing();
			contentsChanged();
		} else {
			setChanged();
		}

		return dish;
	}

	private void clearServing() {
		serving = ItemStack.EMPTY;
		servedWith = "";
		servingsLeft = 0;
	}

	public List<ItemStack> contents() {
		return items.stream().filter(stack -> !stack.isEmpty()).toList();
	}

	public void add(ItemStack ingredient) {
		items.set(count(), ingredient);
		contentsChanged();
	}

	public ItemStack removeLast() {
		int last = count() - 1;

		if (last < 0) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = items.get(last);
		items.set(last, ItemStack.EMPTY);
		contentsChanged();
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

		DishResult result;

		try {
			result = CookingService.create(level.registryAccess()).resolve(pot.contents());
		} catch (IllegalArgumentException e) {
			// Data packs changed since the ingredients went in. Give everything back rather than lose it.
			DynamicCooking.LOGGER.warn("Cooking pot at {} could not cook: {}", pos, e.getMessage());
			Containers.dropContents(level, pos, pot.items);
			pot.items.clear();
			pot.setCookingState(false);
			pot.contentsChanged();
			return;
		}

		ItemStack dish = DishFactory.create(result);
		pot.items.clear();
		pot.setCookingState(false);

		if (result.servedWith().isPresent()) {
			// Runny dishes stay in the pot, so the liquid keeps its height and color until the last serving.
			pot.serving = dish;
			pot.servedWith = result.servedWith().get();
			pot.servingsLeft = result.servings();
			pot.setChanged();
		} else {
			pot.contentsChanged();
			Block.popResourceFromFace(level, pos, Direction.UP, dish);
		}

		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
	}

	private void setCookingState(boolean cooking) {
		if (level != null && getBlockState().getValue(CookingPotBlock.COOKING) != cooking) {
			level.setBlock(worldPosition, getBlockState().setValue(CookingPotBlock.COOKING, cooking), Block.UPDATE_ALL);
		}
	}

	/** Updates the contents' height, color and look after ingredients go in or come out. */
	private void contentsChanged() {
		if (level != null && !level.isClientSide()) {
			CookingService cooking = CookingService.create(level.registryAccess());
			liquidColor = cooking.liquidColor(contents());

			BlockState state = getBlockState()
					.setValue(CookingPotBlock.FILL, count())
					.setValue(CookingPotBlock.LIQUID, isRunny(cooking));

			if (state != getBlockState()) {
				level.setBlock(worldPosition, state, Block.UPDATE_ALL);
			}
		}

		setChanged();
	}

	private boolean isRunny(CookingService cooking) {
		if (isEmpty()) {
			return false;
		}

		try {
			return cooking.resolve(contents()).liquid();
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();

		// Sends the new liquid color to players nearby.
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.getChunkSource().blockChanged(worldPosition);
		}
	}

	/** The liquid color, read by the client tint for the pot. */
	@Override
	public Object getRenderData() {
		return liquidColor;
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, items);

			// Breaking the pot spills nothing: whatever was left to serve drops as finished dishes.
			for (int i = 0; i < servingsLeft && !serving.isEmpty(); i++) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), serving.copyWithCount(1));
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		cookTicksLeft = input.getIntOr("cook_ticks_left", 0);
		liquidColor = input.getIntOr("liquid_color", PotColors.WATER);
		serving = input.read("serving", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		servedWith = input.getStringOr("served_with", "");
		servingsLeft = input.getIntOr("servings_left", 0);

		if (level != null && level.isClientSide()) {
			// Redraw the pot so the liquid picks up the new color.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		ContainerHelper.saveAllItems(output, items);
		output.putInt("cook_ticks_left", cookTicksLeft);
		output.putInt("liquid_color", liquidColor);

		if (hasServing()) {
			output.store("serving", ItemStack.CODEC, serving);
			output.putString("served_with", servedWith);
			output.putInt("servings_left", servingsLeft);
		}
		super.saveAdditional(output);
	}
}
