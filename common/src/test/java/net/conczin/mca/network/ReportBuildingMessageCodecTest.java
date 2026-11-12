package net.conczin.mca.network;

import io.netty.buffer.Unpooled;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportBuildingMessageCodecTest {
    @Test
    void expectedTargetIdRoundTripsWithBuildingAction() {
        ReportBuildingMessage message = new ReportBuildingMessage(
                ReportBuildingMessage.Action.SCAN_ROOM, null, 42);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ReportBuildingMessage.STREAM_CODEC.encode(buffer, message);

            assertEquals(message, ReportBuildingMessage.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
