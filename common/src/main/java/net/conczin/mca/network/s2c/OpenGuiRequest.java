package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record OpenGuiRequest(int gui, int villager, UUID interactionId) implements HandleablePayload {
    public static final CustomPacketPayload.Type<OpenGuiRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("open_gui_request"));
    public static final StreamCodec<FriendlyByteBuf, OpenGuiRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OpenGuiRequest::gui,
            ByteBufCodecs.INT, OpenGuiRequest::villager,
            UUIDUtil.STREAM_CODEC, OpenGuiRequest::interactionId,
            OpenGuiRequest::new
    );

    public OpenGuiRequest(int gui, int villager) {
        this(gui, villager, Util.NIL_UUID);
    }

    public OpenGuiRequest(Type gui, Entity villager) {
        this(gui.ordinal(), villager.getId());
    }

    public OpenGuiRequest(Type gui, Entity villager, UUID interactionId) {
        this(gui.ordinal(), villager.getId(), interactionId);
    }

    public OpenGuiRequest(Type gui) {
        this(gui.ordinal(), 0);
    }

    public Type getGui() {
        return Type.values()[gui];
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleGuiRequest(this);
    }

    @Override
    public CustomPacketPayload.Type<OpenGuiRequest> type() {
        return TYPE;
    }

    public enum Type {
        BABY_NAME,
        WHISTLE,
        BLUEPRINT,
        INTERACT,
        VILLAGER_EDITOR,
        LIMITED_VILLAGER_EDITOR,
        BOOK,
        FAMILY_TREE,
        VILLAGER_TRACKER,
        NEEDLE_AND_THREAD,
        COMB,
        CLOSE,
    }
}
