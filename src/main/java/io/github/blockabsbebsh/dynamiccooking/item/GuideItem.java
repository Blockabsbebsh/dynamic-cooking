package io.github.blockabsbebsh.dynamiccooking.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The cooking guide: a book with short steps and pictures, plus a page per dish built from the loaded data.
 * The screen lives on the client, which plugs it in through {@link #opener}.
 */
public class GuideItem extends Item {
	/** Opens the guide screen. Set by the client; does nothing on a dedicated server. */
	public static Runnable opener = () -> {
	};

	public GuideItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			opener.run();
		}

		return InteractionResult.SUCCESS;
	}
}
