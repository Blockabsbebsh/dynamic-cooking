package io.github.blockabsbebsh.dynamiccooking.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.blockabsbebsh.dynamiccooking.block.CookingPotBlock;
import io.github.blockabsbebsh.dynamiccooking.block.CookingPotBlockEntity;
import io.github.blockabsbebsh.dynamiccooking.block.CookingPotBlockEntity.Timeline;

/**
 * Draws what the block model can't: a finished solid dish lying in the pot, like a cake or a roast, and, while the
 * player looks at the pot, a status panel floating above it. The panel shows what is in the pot and a bar for the
 * cooking time, or for a finished dish how long until it simmers into another and until it burns.
 */
public class CookingPotRenderer implements BlockEntityRenderer<CookingPotBlockEntity, CookingPotRenderer.State> {
	/** Size of one panel pixel, in blocks: half a name tag's, so the panel stays small over the pot. */
	private static final float PIXEL = 0.0125f;
	/** Where cooked food floats in a finished soup or stew, in pot pixels, clear of each other and the rim. */
	private static final float[][] FLOAT_SPOTS = {{6.0f, 6.2f}, {9.8f, 6.6f}, {7.4f, 9.8f}, {10.2f, 10.0f}};
	private static final int BAR_HALF_WIDTH = 30;
	private static final int ICON = 8;

	private static final int PANEL = 0x99000000;
	private static final int TRACK = 0xFF4A4A4A;
	private static final int COOKING = 0xFFE0A030;
	private static final int GOOD = 0xFF5FB55F;
	private static final int GOOD_PASSED = 0xFF2F5A2F;
	private static final int SCORCHING = 0xFFD0503A;
	private static final int SCORCHING_PASSED = 0xFF682820;
	private static final int MARKER = 0xFFFFE070;
	private static final int POINTER = 0xFFFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFB0B0B0;
	private static final int TEXT_WARN = 0xFFFF7060;

	private final ItemModelResolver items;
	private final Font font;

	public CookingPotRenderer(BlockEntityRendererProvider.Context context) {
		this.items = context.itemModelResolver();
		this.font = context.font();
	}

	public static class State extends BlockEntityRenderState {
		/** A finished solid dish to draw lying in the pot, or empty. */
		final ItemStackRenderState dish = new ItemStackRenderState();
		/** Cooked food floating in a finished soup or stew. */
		final List<ItemStackRenderState> floating = new ArrayList<>();
		float surfaceY;
		/** The status panel, when the player is looking at the pot. */
		boolean panel;
		Component title = Component.empty();
		Component status = Component.empty();
		int statusColor = TEXT_DIM;
		final List<ItemStackRenderState> icons = new ArrayList<>();
		/** The bar as pieces from left to right, and the markers above and below it, in panel pixels. */
		final List<Piece> bar = new ArrayList<>();
		final List<Piece> marks = new ArrayList<>();
	}

	/** A coloured span of the bar, from x0 to x1, or a mark at x0 when x1 is x0 + 1. */
	record Piece(float x0, float x1, int color) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CookingPotBlockEntity pot, State state, float partialTick, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(pot, state, partialTick, cameraPos, crumbling);
		state.surfaceY = (float) CookingPotBlock.surfaceY(pot.getBlockState());
		state.dish.clear();

		// Dishes taken out by hand are solid ones, like a cake or a roast: draw the real dish in the pot.
		if (pot.isServedByHand()) {
			items.updateForTopItem(state.dish, pot.serving(), ItemDisplayContext.FIXED, pot.getLevel(), null, (int) pot.getBlockPos().asLong());
		}

		// A soup or stew shows its cooked ingredients floating in it, so it looks cooked too.
		state.floating.clear();

		for (ItemStack food : pot.floating()) {
			ItemStackRenderState floating = new ItemStackRenderState();
			items.updateForTopItem(floating, food, ItemDisplayContext.FIXED, pot.getLevel(), null, 0);
			state.floating.add(floating);
		}

		state.panel = isLookedAt(pot) && (pot.hasServing() || !pot.isEmpty());
		state.icons.clear();
		state.bar.clear();
		state.marks.clear();

		if (state.panel) {
			long gameTime = pot.getLevel() == null ? 0 : pot.getLevel().getGameTime();
			extractPanel(pot, pot.timeline(gameTime), partialTick, state);
		}
	}

	private static boolean isLookedAt(CookingPotBlockEntity pot) {
		HitResult hit = Minecraft.getInstance().hitResult;
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && block.getBlockPos().equals(pot.getBlockPos());
	}

	private void extractPanel(CookingPotBlockEntity pot, Timeline timeline, float partialTick, State state) {
		List<ItemStack> shown = pot.hasServing() ? List.of(pot.serving()) : pot.contents();

		for (ItemStack stack : shown) {
			ItemStackRenderState icon = new ItemStackRenderState();
			items.updateForTopItem(icon, stack, ItemDisplayContext.GUI, pot.getLevel(), null, 0);
			state.icons.add(icon);
		}

		float ticks = timeline.ticks() + (timeline.heated() && timeline.stage() != Timeline.Stage.IDLE ? partialTick : 0);

		switch (timeline.stage()) {
			case IDLE -> {
				ItemStack preview = pot.preview();
				state.title = preview.isEmpty() ? Component.translatable("status.dynamic_cooking.pot.unknown") : preview.getHoverName();
				state.status = Component.translatable(timeline.heated() ? "status.dynamic_cooking.pot.start" : "status.dynamic_cooking.pot.no_heat");
				state.statusColor = TEXT_DIM;
			}
			case COOKING -> {
				ItemStack preview = pot.preview();
				state.title = Component.translatable("status.dynamic_cooking.pot.cooking", preview.isEmpty() ? Component.translatable("status.dynamic_cooking.pot.unknown") : preview.getHoverName());
				float done = Math.min(1, ticks / Math.max(1, timeline.cookTicks()));
				split(state.bar, -BAR_HALF_WIDTH, BAR_HALF_WIDTH, x(done), COOKING, TRACK);
				state.status = Component.translatable("status.dynamic_cooking.pot.ready_in", seconds(timeline.cookTicks() - ticks));
				state.statusColor = TEXT_DIM;
			}
			case WAITING -> extractWaiting(pot, timeline, ticks, state);
		}
	}

	/**
	 * A finished dish's bar runs from ready to burnt: green while it is fine, red over the last stretch where it scorches,
	 * darker where the time has passed, with a mark where it simmers into another dish.
	 */
	private void extractWaiting(CookingPotBlockEntity pot, Timeline timeline, float ticks, State state) {
		state.title = pot.serving().getHoverName();

		if (timeline.burnTicks() <= 0) {
			state.status = Component.empty();
			return;
		}

		float total = timeline.burnTicks();
		float scorchFrom = Math.max(0, total - CookingPotBlockEntity.SCORCH_TICKS) / total;
		float now = Math.min(1, ticks / total);
		float pointer = x(now);

		split(state.bar, -BAR_HALF_WIDTH, x(scorchFrom), pointer, GOOD_PASSED, GOOD);
		split(state.bar, x(scorchFrom), BAR_HALF_WIDTH, pointer, SCORCHING_PASSED, SCORCHING);
		state.marks.add(new Piece(pointer - 0.5f, pointer + 0.5f, POINTER));

		boolean simmers = timeline.simmerTicks() > 0 && ticks < timeline.simmerTicks();

		if (simmers) {
			float at = x(timeline.simmerTicks() / total);
			state.marks.add(new Piece(at - 0.5f, at + 0.5f, MARKER));
		}

		if (!timeline.heated()) {
			state.status = Component.translatable("status.dynamic_cooking.pot.off_heat");
			state.statusColor = TEXT_DIM;
		} else if (simmers) {
			state.status = Component.translatable("status.dynamic_cooking.pot.simmers_in", seconds(timeline.simmerTicks() - ticks));
			state.statusColor = MARKER;
		} else if (now >= scorchFrom) {
			state.status = Component.translatable("status.dynamic_cooking.pot.burns_in", seconds(total - ticks)).withStyle(ChatFormatting.BOLD);
			state.statusColor = TEXT_WARN;
		} else {
			state.status = Component.translatable("status.dynamic_cooking.pot.burns_in", seconds(total - ticks));
			state.statusColor = TEXT_DIM;
		}
	}

	/** The span from x0 to x1, coloured one way left of the split and the other way right of it. */
	private static void split(List<Piece> bar, float x0, float x1, float at, int left, int right) {
		float middle = Math.clamp(at, x0, x1);

		if (middle > x0) {
			bar.add(new Piece(x0, middle, left));
		}

		if (x1 > middle) {
			bar.add(new Piece(middle, x1, right));
		}
	}

	/** Where a fraction of the way along falls on the bar. */
	private static float x(float fraction) {
		return -BAR_HALF_WIDTH + 2 * BAR_HALF_WIDTH * Math.clamp(fraction, 0, 1);
	}

	private static int seconds(float ticks) {
		return Math.max(0, (int) Math.ceil(ticks / 20));
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.dish.isEmpty()) {
			// Lying flat on the contents, like food on a campfire.
			pose.pushPose();
			pose.translate(0.5f, state.surfaceY + 0.02f, 0.5f);
			pose.rotateDegrees(Axis.XP, 90);
			pose.scale(0.42f, 0.42f, 0.42f);
			state.dish.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}

		for (int i = 0; i < state.floating.size(); i++) {
			float[] spot = FLOAT_SPOTS[i];
			pose.pushPose();
			pose.translate(spot[0] / 16f, state.surfaceY + 0.015f, spot[1] / 16f);
			pose.rotateDegrees(Axis.YP, 70 * i + 20);
			pose.rotateDegrees(Axis.XP, 90);
			pose.scale(0.2f, 0.2f, 0.2f);
			state.floating.get(i).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}

		if (state.panel) {
			submitPanel(state, pose, collector, camera);
		}
	}

	/** The panel floats a little above the pot and always faces the camera, like a name tag. */
	private void submitPanel(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = LightCoordsUtil.FULL_BRIGHT;
		boolean hasBar = !state.bar.isEmpty();

		pose.pushPose();
		pose.translate(0.5f, state.surfaceY + 0.7f, 0.5f);
		pose.mulPose(new Matrix4f().rotation(camera.orientation));
		pose.scale(PIXEL, -PIXEL, PIXEL);

		// Rows from the top: title, icons, bar, status.
		float titleY = -24;
		float iconsY = -14;
		float barY = -3;
		float statusY = hasBar ? 5 : -3;
		float bottom = state.status.getString().isEmpty() ? (hasBar ? 3 : -5) : statusY + 10;
		float halfWidth = Math.max(BAR_HALF_WIDTH, Math.max(font.width(state.title), font.width(state.status)) / 2f) + 4;
		collector.submitTextBackground(pose, -halfWidth, titleY - 3, halfWidth, bottom, PANEL, Font.DisplayMode.NORMAL, light);

		text(collector, pose, state.title, titleY, TEXT, light);
		text(collector, pose, state.status, statusY, state.statusColor, light);

		for (Piece piece : state.bar) {
			collector.submitTextBackground(pose, piece.x0(), barY, piece.x1(), barY + 4, piece.color(), Font.DisplayMode.POLYGON_OFFSET, light);
		}

		// Marks stick out above and below the bar so they stay visible on either colour.
		for (Piece mark : state.marks) {
			collector.submitTextBackground(pose, mark.x0(), barY - 2, mark.x1(), barY, mark.color(), Font.DisplayMode.POLYGON_OFFSET, light);
			collector.submitTextBackground(pose, mark.x0(), barY + 4, mark.x1(), barY + 6, mark.color(), Font.DisplayMode.POLYGON_OFFSET, light);
		}

		// Icons sit in a row, centred, a touch in front of the panel.
		float iconsX = -(state.icons.size() * (ICON + 1) - 1) / 2f;

		for (int i = 0; i < state.icons.size(); i++) {
			pose.pushPose();
			pose.translate(iconsX + i * (ICON + 1) + ICON / 2f, iconsY + ICON / 2f, 0.5f);
			pose.scale(ICON, -ICON, ICON);
			state.icons.get(i).submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}

		pose.popPose();
	}

	private void text(SubmitNodeCollector collector, PoseStack pose, Component text, float y, int color, int light) {
		if (text.getString().isEmpty()) {
			return;
		}

		collector.submitText(pose, -font.width(text) / 2f, y, text.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
	}
}
