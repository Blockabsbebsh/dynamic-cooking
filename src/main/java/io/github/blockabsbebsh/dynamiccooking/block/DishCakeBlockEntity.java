package io.github.blockabsbebsh.dynamiccooking.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;

import io.github.blockabsbebsh.dynamiccooking.item.ModItems;

/**
 * Keeps the cake item a {@link DishCakeBlock} was placed from, with its name, food value, buff and ingredient colors.
 * The colors are synced to clients, which tint the fruit on top and the filling between the layers with them.
 */
public class DishCakeBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	/** How many times the cake dish's own food value a whole placed cake gives, shared out over its slices. */
	public static final float WHOLE_CAKE_FOOD = 2.0f;
	/** A slice never feeds less than a vanilla cake slice. */
	public static final int MIN_SLICE_NUTRITION = 2;
	public static final float MIN_SLICE_SATURATION = 0.4f;
	/** Fruit color for a cake that was never cooked, matching the sweet berry look such a cake item has. */
	public static final int DEFAULT_COLOR = 0xA82430;

	private ItemStack cake = ItemStack.EMPTY;

	public DishCakeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CAKE, pos, state);
	}

	/** The cake item this block was placed from. */
	public ItemStack cake() {
		return cake.isEmpty() ? new ItemStack(ModItems.CAKE) : cake;
	}

	public void setCake(ItemStack stack) {
		cake = stack.copyWithCount(1);
		setChanged();
	}

	/** Feeds the player one slice and gives them the cake's buff and side effects. */
	public void feed(Player player) {
		ItemStack stack = cake();
		FoodProperties whole = stack.get(DataComponents.FOOD);

		if (whole != null) {
			player.getFoodData().eat(slice(whole));
		}

		Consumable consumable = stack.get(DataComponents.CONSUMABLE);

		if (consumable != null) {
			consumable.onConsumeEffects().forEach(effect -> effect.apply(player.level(), stack, player));
		}
	}

	/** One slice's share of the whole placed cake, which feeds {@link #WHOLE_CAKE_FOOD} times what the dish is worth. */
	public static FoodProperties slice(FoodProperties whole) {
		int nutrition = Math.max(MIN_SLICE_NUTRITION, Math.round(whole.nutrition() * WHOLE_CAKE_FOOD / DishCakeBlock.SLICES));
		float saturation = Math.max(MIN_SLICE_SATURATION, whole.saturation() * WHOLE_CAKE_FOOD / DishCakeBlock.SLICES);
		return new FoodProperties(nutrition, saturation, false);
	}

	/** Tint 0 colors the fruit on top, tint 1 the filling: the second flavor when there is one, otherwise the first. */
	private int[] tints() {
		CustomModelData data = cake().get(DataComponents.CUSTOM_MODEL_DATA);
		List<Integer> colors = data == null ? List.of() : data.colors();
		int first = colors.isEmpty() ? DEFAULT_COLOR : colors.get(0);
		int second = colors.size() > 1 ? colors.get(1) : first;
		return new int[] {first, second};
	}

	@Override
	public void setChanged() {
		super.setChanged();

		// Sends the cake's colors to players nearby.
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.getChunkSource().blockChanged(worldPosition);
		}
	}

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
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		cake = input.read("cake", ItemStack.CODEC).orElse(ItemStack.EMPTY);

		if (level != null && level.isClientSide()) {
			// Redraw the cake so it picks up the new colors.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		if (!cake.isEmpty()) {
			output.store("cake", ItemStack.CODEC, cake);
		}
		super.saveAdditional(output);
	}
}
