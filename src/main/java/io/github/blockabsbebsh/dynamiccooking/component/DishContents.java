package io.github.blockabsbebsh.dynamiccooking.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import io.github.blockabsbebsh.dynamiccooking.registry.CookingCodecs;

/**
 * What a cooked dish was made from. Everything else on the stack (name, food, texture) is derived from this when cooking.
 *
 * @param ingredients item ids, in the order they went into the pot
 * @param flavors     flavor keys, strongest first
 */
public record DishContents(List<String> ingredients, List<String> flavors) implements TooltipProvider {
	public static final Codec<DishContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			CookingCodecs.ID.listOf().fieldOf("ingredients").forGetter(DishContents::ingredients),
			Codec.STRING.listOf().optionalFieldOf("flavors", List.of()).forGetter(DishContents::flavors)
	).apply(instance, DishContents::new));

	public static final StreamCodec<ByteBuf, DishContents> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DishContents::ingredients,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DishContents::flavors,
			DishContents::new
	);

	public DishContents {
		ingredients = List.copyOf(ingredients);
		flavors = List.copyOf(flavors);
	}

	@Override
	public void addToTooltip(TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		if (ingredients.isEmpty()) {
			return;
		}

		// Repeats are grouped, so two carrots read "Carrot x2".
		Map<String, Integer> counts = new LinkedHashMap<>();
		ingredients.forEach(id -> counts.merge(id, 1, Integer::sum));

		MutableComponent list = Component.empty();
		boolean first = true;

		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (!first) {
				list.append(", ");
			}

			Component name = ingredientName(entry.getKey());
			list.append(entry.getValue() > 1 ? Component.translatable("tooltip.dynamic_cooking.ingredient_count", name, entry.getValue()) : name);
			first = false;
		}

		tooltip.accept(Component.translatable("tooltip.dynamic_cooking.made_with", list).withStyle(ChatFormatting.GRAY));
	}

	private static Component ingredientName(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
				.map(item -> (Component) Component.translatable(item.getDescriptionId()))
				.orElseGet(() -> Component.literal(id));
	}
}
