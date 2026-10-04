package io.github.blockabsbebsh.dynamiccooking.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.blockabsbebsh.dynamiccooking.DynamicCooking;
import io.github.blockabsbebsh.dynamiccooking.block.CookingPotBlock;
import io.github.blockabsbebsh.dynamiccooking.block.Legs;
import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;

/**
 * Tests run on a real server by {@code ./gradlew runGametest}. Starting it at all proves every data pack file in the mod
 * loads: one that doesn't stops the server, as it would stop a world from opening.
 */
public class DynamicCookingGameTests {
	@GameTest
	public void recipeUnlocksLoad(GameTestHelper helper) {
		for (String recipe : new String[] {"cookbook", "cooking_pot"}) {
			boolean loaded = helper.getLevel().getServer().getAdvancements().get(DynamicCooking.id("recipes/misc/" + recipe)) != null;
			helper.assertTrue(loaded, "The " + recipe + " recipe unlock didn't load");
		}

		helper.succeed();
	}

	@GameTest
	public void potSinksIntoACampfire(GameTestHelper helper) {
		BlockPos pot = new BlockPos(0, 2, 0);
		helper.setBlock(pot, ModBlocks.COOKING_POT);
		// Placed under the pot, so the pot sees its neighbour change.
		helper.setBlock(pot.below(), Blocks.CAMPFIRE);
		helper.assertTrue(helper.getBlockState(pot).getValue(CookingPotBlock.LEGS) == Legs.SHORT, "The pot didn't sink into the campfire");
		helper.succeed();
	}
}
