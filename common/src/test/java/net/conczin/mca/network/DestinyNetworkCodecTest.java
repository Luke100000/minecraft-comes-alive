package net.conczin.mca.network;

import io.netty.buffer.Unpooled;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.network.c2s.DestinyMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinyNetworkCodecTest {
    @Test
    void destinationCodecRoundTripsDimensionBoundDestination() {
        DestinyDestination destination = new DestinyDestination(
                "minecraft:village_plains",
                Optional.of(Level.OVERWORLD)
        );

        assertEquals(destination, roundTrip(DestinyDestination.STREAM_CODEC, destination));
    }

    @Test
    void destinationCodecRoundTripsDimensionlessDestination() {
        DestinyDestination destination = new DestinyDestination("somewhere", Optional.empty());

        assertEquals(destination, roundTrip(DestinyDestination.STREAM_CODEC, destination));
    }

    @Test
    void messageFactoriesDistinguishSelectionFromClose() {
        DestinyDestination destination = new DestinyDestination(
                "minecraft:village_plains",
                Optional.of(Level.OVERWORLD)
        );

        assertEquals(Optional.of(destination), DestinyMessage.select(destination).destination());
        assertTrue(DestinyMessage.close().destination().isEmpty());
    }

    @Test
    void messageCodecRoundTripsSelectionAndClose() {
        DestinyDestination destination = new DestinyDestination(
                "minecraft:village_plains",
                Optional.of(Level.OVERWORLD)
        );

        DestinyMessage selection = DestinyMessage.select(destination);
        DestinyMessage close = DestinyMessage.close();

        assertEquals(selection, roundTrip(DestinyMessage.STREAM_CODEC, selection));
        assertEquals(close, roundTrip(DestinyMessage.STREAM_CODEC, close));
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
