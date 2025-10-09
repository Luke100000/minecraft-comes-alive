package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public record RemoveCustomClothingMessage(Type kind, String identifier) implements HandleablePayload {
    public static final CustomPacketPayload.Type<RemoveCustomClothingMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("remove_custom_clothing"));
    public static final StreamCodec<FriendlyByteBuf, RemoveCustomClothingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, m -> m.kind().ordinal(),
            ByteBufCodecs.STRING_UTF8, RemoveCustomClothingMessage::identifier,
            (i, id) -> new RemoveCustomClothingMessage(Type.values()[i], id)
    );

    public RemoveCustomClothingMessage(Type kind, ResourceLocation identifier) {
        this(kind, String.valueOf(identifier));
    }

    @Override
    public void handle(Player player) {
        if (kind == Type.CLOTHING) {
            CustomClothingManager.getClothing().removeEntry(identifier);
        } else if (kind == Type.HAIR) {
            CustomClothingManager.getHair().removeEntry(identifier);
        }
    }

    @Override
    public CustomPacketPayload.Type<RemoveCustomClothingMessage> type() {
        return TYPE;
    }

    public enum Type {
        CLOTHING,
        HAIR
    }
}
