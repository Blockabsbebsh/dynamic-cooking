package io.github.blockabsbebsh.dynamiccooking.block;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import io.github.blockabsbebsh.dynamiccooking.cooking.PotColors;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * Holds the ingredients in the order they were added and runs the cooking timer.
 * Cooked dishes stay in the pot until they are taken out: runny ones with their serving item, like stew with a bowl,
 * the rest with an empty hand. The pot can also hold copies of one raw dish, like a raw chicken skewer, to cook it again.
 * The liquid color and one color per floating chunk are synced to clients, which tint the pot with them.
 */
public class CookingPotBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	/** How long cooking takes, in ticks. */
	public static final int COOK_TICKS = 3 * 20;
	/** Players this close hear that a dish is ready. */
	private static final double ANNOUNCE_RANGE = 16.0;

	private final NonNullList<ItemStack> items = NonNullList.withSize(CookingRules.DEFAULT.maxIngredients(), ItemStack.EMPTY);
	private int cookTicksLeft;
	private int liquidColor = PotColors.WATER;
	/** One color per ingredient slot, for the chunk drawn floating in the pot. */
	private List<Integer> chunkColors = List.of();
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
		return hasServing() && !servedWith.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(servedWith);
	}

	/** Whether the waiting dish is taken out with an empty hand, like a roast. */
	public boolean isServedByHand() {
		return hasServing() && servedWith.isEmpty();
	}

	/**
	 * Why this stack can't go in the pot next to what is already there, as a message key, or empty if it can.
	 * The pot holds either loose ingredients or copies of one raw dish to cook again, never both.
	 */
	public Optional<String> refuses(CookingService cooking, ItemStack stack) {
		if (isEmpty()) {
			return Optional.empty();
		}

		ItemStack first = items.getFirst();

		if (cooking.isRecookable(stack)) {
			return ItemStack.isSameItemSameComponents(first, stack) ? Optional.empty() : Optional.of("recook_alone");
		}

		return cooking.isRecookable(first) ? Optional.of("recook_alone") : Optional.empty();
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

	/** The pot's colors as the client tint reads them: the liquid first, then one per chunk. */
	private int[] tints() {
		int[] tints = new int[1 + capacity()];
		tints[0] = liquidColor;

		for (int i = 0; i < capacity(); i++) {
			tints[i + 1] = i < chunkColors.size() ? chunkColors.get(i) : liquidColor;
		}

		return tints;
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

		CookingService.PotResult result;

		try {
			result = CookingService.create(level.registryAccess()).cookPot(pot.contents());
		} catch (IllegalArgumentException e) {
			// Data packs changed since the ingredients went in. Give everything back rather than lose it.
			DynamicCooking.LOGGER.warn("Cooking pot at {} could not cook: {}", pos, e.getMessage());
			Containers.dropContents(level, pos, pot.items);
			pot.items.clear();
			pot.setCookingState(false);
			pot.contentsChanged();
			return;
		}

		pot.items.clear();
		pot.setCookingState(false);

		// The dish stays in the pot until it is taken out, so the contents keep their height while the colors turn cooked.
		pot.serving = result.dish();
		pot.servedWith = result.servedWith().orElse("");
		pot.servingsLeft = result.servings();
		pot.liquidColor = result.liquidColor();
		pot.chunkColors = result.colors();
		level.setBlock(pos, pot.getBlockState().setValue(CookingPotBlock.READY, true), Block.UPDATE_ALL);
		pot.setChanged();

		announceReady(level, pos, pot);
	}

	/** A bell, a puff of steam and a message for players nearby, so nobody has to watch the pot. */
	private static void announceReady(Level level, BlockPos pos, CookingPotBlockEntity pot) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.2f);
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.8f, 1.0f);

		if (level instanceof ServerLevel serverLevel) {
			double y = pos.getY() + CookingPotBlock.surfaceY(pot.getBlockState());
			serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, y + 0.1, pos.getZ() + 0.5, 8, 0.15, 0.05, 0.15, 0.01);

			Component message = CookingPotBlock.readyMessage(pot);

			for (ServerPlayer player : serverLevel.players()) {
				if (player.blockPosition().closerThan(pos, ANNOUNCE_RANGE)) {
					player.sendOverlayMessage(message);
				}
			}
		}
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
			chunkColors = cooking.colors(contents());

			BlockState state = getBlockState()
					.setValue(CookingPotBlock.FILL, count())
					.setValue(CookingPotBlock.LIQUID, isRunny(cooking))
					.setValue(CookingPotBlock.READY, false);

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
			return cooking.cookPot(contents()).liquid();
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

	/** The liquid and chunk colors, read by the client tint for the pot. */
	@Override
	public Object getRenderData() {
		return tints();
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
		chunkColors = input.read("chunk_colors", Codec.INT.listOf()).orElse(List.of());
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
		output.store("chunk_colors", Codec.INT.listOf(), chunkColors);

		if (hasServing()) {
			output.store("serving", ItemStack.CODEC, serving);
			output.putString("served_with", servedWith);
			output.putInt("servings_left", servingsLeft);
		}
		super.saveAdditional(output);
	}
}
