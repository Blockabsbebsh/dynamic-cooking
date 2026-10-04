package io.github.blockabsbebsh.dynamiccooking.component;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;

public final class ModComponents {
	public static final DataComponentType<DishContents> DISH = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			DynamicCooking.id("dish"),
			DataComponentType.<DishContents>builder().persistent(DishContents.CODEC).networkSynchronized(DishContents.STREAM_CODEC).build()
	);

	public static final DataComponentType<DishVessel> VESSEL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			DynamicCooking.id("vessel"),
			DataComponentType.<DishVessel>builder().persistent(DishVessel.CODEC).networkSynchronized(DishVessel.STREAM_CODEC).build()
	);

	private ModComponents() {
	}

	public static void initialize() {
		ItemComponentTooltipProviderRegistry.addAfter(DataComponents.LORE, DISH);
		ItemComponentTooltipProviderRegistry.addAfter(DISH, VESSEL);
	}
}
