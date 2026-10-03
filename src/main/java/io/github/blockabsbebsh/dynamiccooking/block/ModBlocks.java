package io.github.blockabsbebsh.dynamiccooking.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;

public final class ModBlocks {
	public static final BlockItemId COOKING_POT_ID = BlockItemId.create(DynamicCooking.id("cooking_pot"), DynamicCooking.id("cooking_pot"));

	public static final Block COOKING_POT = register(
			COOKING_POT_ID,
			new CookingPotBlock(BlockBehaviour.Properties.of()
					.strength(2.0f)
					.sound(SoundType.METAL)
					.noOcclusion()
					.setId(COOKING_POT_ID.block()))
	);

	private ModBlocks() {
	}

	public static void initialize() {
	}

	private static Block register(BlockItemId id, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, id.block(), block);
		Registry.register(BuiltInRegistries.ITEM, id.item(), new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(id.item())));
		return block;
	}
}
