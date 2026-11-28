package net.conczin.mca.destiny;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

public record DestinyDestination(String location, Optional<ResourceKey<Level>> dimension) {
    public static final int MAX_LOCATION_LENGTH = 128;
    public static final StreamCodec<FriendlyByteBuf, DestinyDestination> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_LOCATION_LENGTH), DestinyDestination::location,
            ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs::optional), DestinyDestination::dimension,
            DestinyDestination::new
    );

    public DestinyDestination {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Destiny location must not be blank");
        }
        if (location.length() > MAX_LOCATION_LENGTH) {
            throw new IllegalArgumentException("Destiny location exceeds " + MAX_LOCATION_LENGTH + " characters");
        }
        Objects.requireNonNull(dimension, "dimension");
    }
}
