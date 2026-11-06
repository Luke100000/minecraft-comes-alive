package net.conczin.mca.network;

import io.netty.buffer.Unpooled;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.s2c.AppearanceClothingUpdate;
import net.conczin.mca.network.s2c.AppearanceHairUpdate;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceCatalogUpdateCodecTest {
    @Test
    void clothingPresentAndRemovalRoundTrip() {
        String id = "immersive_library:42";
        Clothing entry = new Clothing(id, "minecraft:farmer", 3, true, Gender.NEUTRAL);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AppearanceClothingUpdate.STREAM_CODEC.encode(buffer, new AppearanceClothingUpdate(id, Optional.of(entry)));
            AppearanceClothingUpdate decoded = AppearanceClothingUpdate.STREAM_CODEC.decode(buffer);
            assertEquals(id, decoded.identifier());
            assertEquals(entry.toJson(), decoded.effectiveClothing().orElseThrow().toJson());
            buffer.clear();
            AppearanceClothingUpdate.STREAM_CODEC.encode(buffer, new AppearanceClothingUpdate(id, Optional.empty()));
            assertTrue(AppearanceClothingUpdate.STREAM_CODEC.decode(buffer).effectiveClothing().isEmpty());
        } finally {
            buffer.release();
        }
    }

    @Test
    void hairStyleAndRemovalRoundTrip() {
        String id = "immersive_library:17";
        Hair hair = new Hair(id, Gender.FEMALE, .75f);
        HairStyle explicit = new HairStyle(id, Gender.NEUTRAL, .25f, true, "mca:base", "", "", "", "");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AppearanceHairUpdate.STREAM_CODEC.encode(buffer, new AppearanceHairUpdate(id, Optional.of(hair), Optional.of(explicit)));
            AppearanceHairUpdate decoded = AppearanceHairUpdate.STREAM_CODEC.decode(buffer);
            assertEquals(id, decoded.identifier());
            assertEquals(Gender.FEMALE, decoded.customHair().orElseThrow().getGender());
            assertTrue(decoded.effectiveStyle().orElseThrow().exclude());
            assertEquals(Gender.NEUTRAL, decoded.effectiveStyle().orElseThrow().getGender());
            buffer.clear();
            AppearanceHairUpdate.STREAM_CODEC.encode(buffer, new AppearanceHairUpdate(id, Optional.empty(), Optional.empty()));
            AppearanceHairUpdate removed = AppearanceHairUpdate.STREAM_CODEC.decode(buffer);
            assertTrue(removed.customHair().isEmpty());
            assertTrue(removed.effectiveStyle().isEmpty());
        } finally {
            buffer.release();
        }
    }
}
