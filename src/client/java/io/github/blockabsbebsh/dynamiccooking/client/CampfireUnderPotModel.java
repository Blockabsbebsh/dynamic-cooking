package io.github.blockabsbebsh.dynamiccooking.client;

import java.util.function.Predicate;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;

import io.github.blockabsbebsh.dynamiccooking.block.CookingPotBlock;
import io.github.blockabsbebsh.dynamiccooking.block.ModBlocks;

/**
 * A campfire with a cooking pot on it. The pot sinks into the campfire's block, so the flames, which reach a pixel
 * above it, are squashed down to end at the pot's bottom instead of burning through it. The flames are the campfire's
 * only diagonal quads; the logs are left alone.
 */
public class CampfireUnderPotModel extends WrapperBlockStateModel {
	/** The flames start a pixel up, among the logs, and end a pixel above the block. */
	private static final float FLAME_BOTTOM = 1 / 16f;
	private static final float FLAME_TOP = 17 / 16f;
	private static final float POT_BOTTOM = 1 - CookingPotBlock.CAMPFIRE_DROP / 16f;
	private static final float SQUASH = (POT_BOTTOM - FLAME_BOTTOM) / (FLAME_TOP - FLAME_BOTTOM);

	private static final QuadTransform SQUASH_FLAMES = quad -> {
		Vector3fc normal = quad.faceNormal();

		if (Math.abs(normal.x()) > 0.1f && Math.abs(normal.z()) > 0.1f) {
			for (int i = 0; i < 4; i++) {
				quad.pos(i, quad.x(i), FLAME_BOTTOM + (quad.y(i) - FLAME_BOTTOM) * SQUASH, quad.z(i));
			}
		}

		return true;
	};

	public CampfireUnderPotModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	private static boolean underPot(BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockState(pos.above()).is(ModBlocks.COOKING_POT);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		if (!underPot(level, pos)) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}

		emitter.pushTransform(SQUASH_FLAMES);
		super.emitQuads(emitter, level, pos, state, random, cullTest);
		emitter.popTransform();
	}

	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		Object key = super.createGeometryKey(level, pos, state, random);
		return key == null ? null : new Key(key, underPot(level, pos));
	}

	private record Key(Object wrapped, boolean underPot) {
	}
}
