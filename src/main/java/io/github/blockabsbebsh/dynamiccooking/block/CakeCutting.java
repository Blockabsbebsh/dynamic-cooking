package io.github.blockabsbebsh.dynamiccooking.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import io.github.blockabsbebsh.dynamiccooking.component.ModComponents;
import io.github.blockabsbebsh.dynamiccooking.item.ModItems;

/**
 * Sneak and right-click a placed cake with a sword to cut it into slices to carry around. Works on cooked cakes and the
 * vanilla cake. The cake drops one slice per slice it has left, and each slice feeds exactly what eating that slice from
 * the placed cake would, so cutting a cake never gains or loses food.
 */
public final class CakeCutting {
	private CakeCutting() {
	}

	public static void initialize() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);

			if (player.isSpectator() || !player.isSecondaryUseActive() || !held.is(ItemTags.SWORDS)) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);

			if (!state.is(Blocks.CAKE) && !state.is(ModBlocks.CAKE)) {
				return InteractionResult.PASS;
			}

			if (!level.isClientSide()) {
				cut(level, pos, state);
			}

			return InteractionResult.SUCCESS;
		});
	}

	private static void cut(Level level, BlockPos pos, BlockState state) {
		int left = DishCakeBlock.SLICES - state.getValue(BlockStateProperties.BITES);
		ItemStack slice = level.getBlockEntity(pos) instanceof DishCakeBlockEntity cake ? sliceOf(cake.cake()) : new ItemStack(ModItems.CAKE_SLICE);

		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 1.2f);
		Block.popResource(level, pos, slice.copyWithCount(left));
	}

	/** One slice of a cooked cake, with the cake's name, contents, buff and colors and one slice's share of its food. */
	public static ItemStack sliceOf(ItemStack cake) {
		ItemStack slice = new ItemStack(ModItems.CAKE_SLICE);
		copy(cake, slice, ModComponents.DISH);
		copy(cake, slice, DataComponents.CUSTOM_MODEL_DATA);
		repeatFirstColor(slice);
		copy(cake, slice, DataComponents.CONSUMABLE);

		FoodProperties whole = cake.get(DataComponents.FOOD);

		if (whole != null) {
			slice.set(DataComponents.FOOD, DishCakeBlockEntity.slice(whole));
		}

		if (cake.has(DataComponents.ITEM_NAME)) {
			slice.set(DataComponents.ITEM_NAME, Component.translatable("item.dynamic_cooking.cake_slice.of", cake.get(DataComponents.ITEM_NAME)));
		}

		return slice;
	}

	/** The slice item tints its filling with color 1, so a one-flavor slice gets its only color twice. */
	private static void repeatFirstColor(ItemStack slice) {
		CustomModelData data = slice.get(DataComponents.CUSTOM_MODEL_DATA);

		if (data != null && data.colors().size() == 1) {
			int color = data.colors().get(0);
			slice.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.floats(), data.flags(), data.strings(), List.of(color, color)));
		}
	}

	private static <T> void copy(ItemStack from, ItemStack to, DataComponentType<T> type) {
		T value = from.get(type);

		if (value != null) {
			to.set(type, value);
		}
	}
}
