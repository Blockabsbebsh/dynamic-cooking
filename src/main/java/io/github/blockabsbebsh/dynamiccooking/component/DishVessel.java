package io.github.blockabsbebsh.dynamiccooking.component;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import io.github.blockabsbebsh.dynamiccooking.dish.Vessel;

/**
 * A dish served in something other than its usual container, like soup in a bottle, and how many servings it holds.
 * Only a bucket holds more than one: it is topped up a serving at a time at the pot and drunk a serving at a time.
 */
public record DishVessel(Vessel vessel, int servings) implements TooltipProvider {
	public static final Codec<DishVessel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Vessel.CODEC.fieldOf("vessel").forGetter(DishVessel::vessel),
			Codec.intRange(1, Vessel.BUCKET_SERVINGS).optionalFieldOf("servings", 1).forGetter(DishVessel::servings)
	).apply(instance, DishVessel::new));

	public static final StreamCodec<ByteBuf, DishVessel> STREAM_CODEC = StreamCodec.composite(
			Vessel.STREAM_CODEC, DishVessel::vessel,
			ByteBufCodecs.VAR_INT, DishVessel::servings,
			DishVessel::new
	);

	@Override
	public void addToTooltip(TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		if (vessel == Vessel.BUCKET) {
			tooltip.accept(Component.translatable("tooltip.dynamic_cooking.servings", servings, Vessel.BUCKET_SERVINGS).withStyle(ChatFormatting.GRAY));
		}
	}
}
