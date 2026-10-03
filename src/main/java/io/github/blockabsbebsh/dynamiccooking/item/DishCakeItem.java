package io.github.blockabsbebsh.dynamiccooking.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A cooked cake. Like a vanilla cake it is placed on the ground and eaten there a slice at a time, never eaten from the
 * hand, even though its stack carries the food value and buff that every slice gives.
 */
public class DishCakeItem extends BlockItem {
	public DishCakeItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}
}
