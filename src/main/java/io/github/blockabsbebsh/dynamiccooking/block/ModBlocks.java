package io.github.blockabsbebsh.dynamiccooking.block;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
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

	/** A cooked cake placed on the ground. Its item is {@link io.github.blockabsbebsh.dynamiccooking.item.ModItems#CAKE}. */
	public static final Block CAKE = registerBlock(
			"cake",
			properties -> new DishCakeBlock(properties
					.strength(0.5f)
					.sound(SoundType.WOOL))
	);

	private ModBlocks() {
	}

	public static void initialize() {
	}

	/** A block whose item is registered with the dishes instead. */
	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, DynamicCooking.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of().setId(key)));
	}

	private static Block register(BlockItemId id, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, id.block(), block);
		Registry.register(BuiltInRegistries.ITEM, id.item(), new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(id.item())));
		return block;
	}
}
