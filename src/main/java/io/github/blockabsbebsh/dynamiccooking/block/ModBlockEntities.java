package io.github.blockabsbebsh.dynamiccooking.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;

public final class ModBlockEntities {
	public static final BlockEntityType<CookingPotBlockEntity> COOKING_POT = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			DynamicCooking.id("cooking_pot"),
			FabricBlockEntityTypeBuilder.create(CookingPotBlockEntity::new, ModBlocks.COOKING_POT).build()
	);

	public static final BlockEntityType<DishCakeBlockEntity> CAKE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			DynamicCooking.id("cake"),
			FabricBlockEntityTypeBuilder.create(DishCakeBlockEntity::new, ModBlocks.CAKE).build()
	);

	private ModBlockEntities() {
	}

	public static void initialize() {
	}
}
