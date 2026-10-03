package io.github.blockabsbebsh.dynamiccooking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import io.github.blockabsbebsh.dynamiccooking.component.ModComponents;
import io.github.blockabsbebsh.dynamiccooking.item.ModCreativeTab;
import io.github.blockabsbebsh.dynamiccooking.item.ModItems;
import io.github.blockabsbebsh.dynamiccooking.registry.ModRegistries;

public class DynamicCooking implements ModInitializer {
	public static final String MOD_ID = "dynamic_cooking";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModRegistries.initialize();
		ModComponents.initialize();
		ModItems.initialize();
		ModCreativeTab.initialize();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
