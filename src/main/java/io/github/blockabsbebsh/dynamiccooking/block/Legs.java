package io.github.blockabsbebsh.dynamiccooking.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stilts the pot stands on, picked from what is underneath it.
 */
public enum Legs implements StringRepresentable {
	/** On a full block like a furnace or magma, the pot sits right on top. */
	NONE("none"),
	/** On a campfire, legs reach down to the logs. */
	SHORT("short"),
	/** Over fire, lava or a gap, legs reach down a whole block. */
	LONG("long");

	private final String name;

	Legs(String name) {
		this.name = name;
	}

	public static Legs below(BlockGetter level, BlockPos pos) {
		BlockPos belowPos = pos.below();
		BlockState below = level.getBlockState(belowPos);

		if (below.is(BlockTags.CAMPFIRES)) {
			return SHORT;
		}

		return below.isFaceSturdy(level, belowPos, Direction.UP) ? NONE : LONG;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
