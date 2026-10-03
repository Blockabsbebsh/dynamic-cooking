package io.github.blockabsbebsh.dynamiccooking.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import io.github.blockabsbebsh.dynamiccooking.dish.Cookbook;
import io.github.blockabsbebsh.dynamiccooking.dish.CookingService;

/**
 * A book listing every dish and what it needs. The pages are rewritten from the loaded data each time it is opened.
 */
public class CookbookItem extends Item {
	public CookbookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!level.isClientSide()) {
			stack.set(DataComponents.WRITTEN_BOOK_CONTENT, Cookbook.write(CookingService.create(level.registryAccess())));
			player.openItemGui(stack, hand);
		}

		return InteractionResult.SUCCESS;
	}
}
