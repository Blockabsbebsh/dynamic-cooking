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
import io.github.blockabsbebsh.dynamiccooking.cooking.PotColors;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * Holds the ingredients in the order they were added and runs the cooking timer.
 * Cooked dishes stay in the pot until they are taken out: runny ones with their serving item, like soup with a bowl,
 * the rest with an empty hand. The pot can also hold copies of one raw dish, like a raw chicken skewer, to cook it again.
 *
 * <p>A finished dish keeps cooking while it waits on the heat: soup simmers into stew, and anything left long enough
 * starts to scorch, darkening and smoking, then burns into Dubious Mush. Off the heat it waits safely.
 *
 * <p>The liquid color and one color per floating chunk are synced to clients, which tint the pot with them. So are the
 * timers, but only when something changes: clients run them on from the game time, so the status bar moves smoothly.
 */
public class CookingPotBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	/** How long before burning a dish starts to scorch, in ticks, and in how many steps it darkens. */
	public static final int SCORCH_TICKS = 20 * 20;
	public static final int SCORCH_STEPS = 3;
	/** The color of burnt contents. */
	private static final int BURNT = 0x2E2219;

	private final NonNullList<ItemStack> items = NonNullList.withSize(CookingRules.DEFAULT.maxIngredients(), ItemStack.EMPTY);
	private int cookTicksLeft;
	private int cookTicks;
	/** Ticks the waiting dish has spent on the heat, and when it simmers or burns; 0 for never. */
	private int heatTicks;
	private int simmerTicks;
	private int burnTicks;
	/** Whether the pot had heat under it at the last tick, so clients know whether the timers run. */
	private boolean heated;
	/** The game time when the timers above were last sent, which clients count on from. */
	private long syncTime;
	/** On clients, what the ingredients would make, worked out once per change. */
	private ItemStack preview;
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
		heatTicks = 0;
		simmerTicks = 0;
		burnTicks = 0;
	}

	/** The pot's colors as the client tint reads them: the liquid first, then one per chunk, darker as it scorches. */
	private int[] tints() {
		int[] tints = new int[1 + capacity()];
		tints[0] = scorched(liquidColor);

		for (int i = 0; i < capacity(); i++) {
			tints[i + 1] = scorched(i < chunkColors.size() ? chunkColors.get(i) : liquidColor);
		}

		return tints;
	}

	/** A color mixed towards burnt brown, a step at a time. */
	private int scorched(int color) {
		int step = scorch();

		if (step == 0) {
			return color;
		}

		float mix = 0.22f * step;
		int red = mix(color >> 16 & 0xFF, BURNT >> 16 & 0xFF, mix);
		int green = mix(color >> 8 & 0xFF, BURNT >> 8 & 0xFF, mix);
		int blue = mix(color & 0xFF, BURNT & 0xFF, mix);
		return red << 16 | green << 8 | blue;
	}

	private static int mix(int from, int to, float amount) {
		return Math.round(from + (to - from) * amount);
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
		cookTicks = CookingService.create(level.registryAccess()).cookTicks();
		cookTicksLeft = cookTicks;
		setCookingState(true);
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
		boolean heat = CookingPotBlock.hasHeat(level, pos);

		if (heat != pot.heated) {
			pot.heated = heat;
			pot.setChanged();
		}

		if (pot.hasServing()) {
			if (heat) {
				pot.keepCooking(level, pos);
			}

			return;
		}

		if (!pot.isCooking()) {
			return;
		}

		// Taking the heat away stops cooking but keeps the ingredients.
		if (!heat) {
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
		pot.startWaiting(CookingService.create(level.registryAccess()));
		level.setBlock(pos, pot.getBlockState().setValue(CookingPotBlock.READY, true), Block.UPDATE_ALL);
		pot.setChanged();

		announceReady(level, pos, pot);
	}

	/** Starts the waiting dish's timers over, for a dish that just finished or just changed into another. */
	private void startWaiting(CookingService cooking) {
		CookingService.DishTimes times = cooking.times(serving);
		heatTicks = 0;
		simmerTicks = times.simmerTicks();
		burnTicks = times.burnTicks();
	}

	/** One tick of a finished dish on the heat: it simmers, scorches and in the end burns. */
	private void keepCooking(Level level, BlockPos pos) {
		int scorchBefore = scorch();
		heatTicks++;

		if (simmerTicks > 0 && heatTicks >= simmerTicks) {
			simmer(level, pos);
		} else if (burnTicks > 0 && heatTicks >= burnTicks) {
			burn(level, pos);
		} else if (scorch() != scorchBefore) {
			// A darker shade each step, so the pot shows it is about to burn.
			setChanged();
			level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private void simmer(Level level, BlockPos pos) {
		CookingService cooking = CookingService.create(level.registryAccess());
		Optional<ItemStack> simmered = cooking.simmer(serving);

		if (simmered.isEmpty()) {
			// The dish it simmers into doesn't take these ingredients: it just keeps waiting until it burns.
			simmerTicks = 0;
			setChanged();
			return;
		}

		serving = simmered.get();
		startWaiting(cooking);
		// Stew is thick: the pot shows a mash instead of liquid.
		level.setBlock(pos, getBlockState().setValue(CookingPotBlock.LIQUID, false), Block.UPDATE_ALL);
		setChanged();

		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8f, 0.8f);

		if (level instanceof ServerLevel serverLevel) {
			double y = pos.getY() + CookingPotBlock.surfaceY(getBlockState());
			serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, y + 0.1, pos.getZ() + 0.5, 6, 0.15, 0.05, 0.15, 0.01);
		}
	}

	private void burn(Level level, BlockPos pos) {
		serving = CookingService.create(level.registryAccess()).burn(serving);
		servedWith = CookingService.create(level.registryAccess()).fallbackServedWith();
		simmerTicks = 0;
		burnTicks = 0;
		heatTicks = 0;
		liquidColor = BURNT;
		chunkColors = chunkColors.stream().map(color -> BURNT).toList();
		level.setBlock(pos, getBlockState().setValue(CookingPotBlock.LIQUID, false), Block.UPDATE_ALL);
		setChanged();

		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 0.8f);

		if (level instanceof ServerLevel serverLevel) {
			double y = pos.getY() + CookingPotBlock.surfaceY(getBlockState());
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, y + 0.1, pos.getZ() + 0.5, 10, 0.15, 0.05, 0.15, 0.02);
		}
	}

	/** How far a waiting dish has scorched, from 0 (not at all) to {@link #SCORCH_STEPS}, as of the last sync. */
	public int scorch() {
		if (burnTicks <= 0 || heatTicks < burnTicks - SCORCH_TICKS) {
			return 0;
		}

		int into = heatTicks - (burnTicks - SCORCH_TICKS);
		return Math.min(SCORCH_STEPS, 1 + into * SCORCH_STEPS / SCORCH_TICKS);
	}

	/** The status bar's view of the pot, run on from the last sync to the given game time. */
	public Timeline timeline(long gameTime) {
		long since = Math.max(0, gameTime - syncTime);

		if (isCooking()) {
			return new Timeline(Timeline.Stage.COOKING, cookTicks - Math.max(0, cookTicksLeft - since), cookTicks, 0, 0, heated);
		}

		if (hasServing()) {
			long ticks = heated ? heatTicks + since : heatTicks;
			return new Timeline(Timeline.Stage.WAITING, ticks, 0, simmerTicks, burnTicks, heated);
		}

		return new Timeline(Timeline.Stage.IDLE, 0, 0, 0, 0, heated);
	}

	/**
	 * Where the pot is on its way, for the status bar.
	 *
	 * @param ticks       ticks done: cooking so far, or the waiting dish's time on the heat
	 * @param cookTicks   how long cooking takes in all
	 * @param simmerTicks when the waiting dish simmers into another, or 0
	 * @param burnTicks   when the waiting dish burns, or 0
	 * @param heated      whether there is heat under the pot, so the clock runs
	 */
	public record Timeline(Stage stage, long ticks, int cookTicks, int simmerTicks, int burnTicks, boolean heated) {
		public enum Stage { IDLE, COOKING, WAITING }
	}

	/** On clients, what the ingredients in the pot would make, for the status bar. Empty if nothing works out. */
	public ItemStack preview() {
		if (preview == null) {
			preview = ItemStack.EMPTY;

			if (level != null && !isEmpty()) {
				try {
					preview = CookingService.create(level.registryAccess()).cookPot(contents()).dish();
				} catch (IllegalArgumentException e) {
					// Not ingredients any more, after a data pack change: nothing to show.
				}
			}
		}

		return preview;
	}

	/** A bell and a puff of steam, so nobody has to watch the pot. */
	private static void announceReady(Level level, BlockPos pos, CookingPotBlockEntity pot) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.2f);
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.8f, 1.0f);

		if (level instanceof ServerLevel serverLevel) {
			double y = pos.getY() + CookingPotBlock.surfaceY(pot.getBlockState());
			serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, y + 0.1, pos.getZ() + 0.5, 8, 0.15, 0.05, 0.15, 0.01);
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

		// Sends the new colors and timers to players nearby.
		if (level instanceof ServerLevel serverLevel) {
			syncTime = level.getGameTime();
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
		cookTicks = input.getIntOr("cook_ticks", cookTicksLeft);
		heatTicks = input.getIntOr("heat_ticks", 0);
		simmerTicks = input.getIntOr("simmer_ticks", 0);
		burnTicks = input.getIntOr("burn_ticks", 0);
		heated = input.getBooleanOr("heated", false);
		syncTime = input.getLongOr("sync_time", 0L);
		preview = null;

		if (level != null && level.isClientSide()) {
			// Redraw the pot so the liquid picks up the new color.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		ContainerHelper.saveAllItems(output, items);
		output.putInt("cook_ticks_left", cookTicksLeft);
		output.putInt("cook_ticks", cookTicks);
		output.putInt("heat_ticks", heatTicks);
		output.putInt("simmer_ticks", simmerTicks);
		output.putInt("burn_ticks", burnTicks);
		output.putBoolean("heated", heated);
		output.putLong("sync_time", level != null ? level.getGameTime() : syncTime);
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
