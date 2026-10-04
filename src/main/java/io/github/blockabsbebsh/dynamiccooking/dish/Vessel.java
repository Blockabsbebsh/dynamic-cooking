package io.github.blockabsbebsh.dynamiccooking.dish;

import java.util.Arrays;
import java.util.Optional;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What a dish from the pot can be taken out with. Any of them serves any dish from the pot that isn't taken by hand,
 * except that a bottle only takes runny ones. A bucket holds three servings, like a cauldron fills three bottles.
 */
public enum Vessel implements StringRepresentable {
	BOWL("bowl", Items.BOWL, 1, false),
	BOTTLE("bottle", Items.GLASS_BOTTLE, 2, true),
	BUCKET("bucket", Items.BUCKET, 3, true);

	public static final int BUCKET_SERVINGS = 3;
	public static final Codec<Vessel> CODEC = StringRepresentable.fromEnum(Vessel::values);
	public static final StreamCodec<ByteBuf, Vessel> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Vessel::ordinal);

	private final String name;
	private final Item item;
	private final int modelCode;
	private final boolean drunk;

	Vessel(String name, Item item, int modelCode, boolean drunk) {
		this.name = name;
		this.item = item;
		this.modelCode = modelCode;
		this.drunk = drunk;
	}

	/** The empty container. */
	public Item item() {
		return item;
	}

	/** The first item model data float of a dish in this container, as tools/textures/generate.py writes the models. */
	public float modelCode() {
		return modelCode;
	}

	/** Whether a dish in this is drunk rather than eaten. */
	public boolean drunk() {
		return drunk;
	}

	public int capacity() {
		return this == BUCKET ? BUCKET_SERVINGS : 1;
	}

	public static Optional<Vessel> of(Item item) {
		return Arrays.stream(values()).filter(vessel -> vessel.item == item).findFirst();
	}

	public static Optional<Vessel> of(String itemId) {
		return Arrays.stream(values()).filter(vessel -> BuiltInRegistries.ITEM.getKey(vessel.item).toString().equals(itemId)).findFirst();
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
