package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record OpenGuiRequest(int gui, int villager) implements HandleablePayload {
   public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<OpenGuiRequest> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type(
      MCA.locate("open_gui_request")
   );
   public static final StreamCodec<FriendlyByteBuf, OpenGuiRequest> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.INT, OpenGuiRequest::gui, ByteBufCodecs.INT, OpenGuiRequest::villager, OpenGuiRequest::new
   );

   public OpenGuiRequest(OpenGuiRequest.Type gui, Entity villager) {
      this(gui.ordinal(), villager.getId());
   }

   public OpenGuiRequest(OpenGuiRequest.Type gui) {
      this(gui.ordinal(), 0);
   }

   public OpenGuiRequest.Type getGui() {
      return OpenGuiRequest.Type.values()[this.gui];
   }

   @Override
   public void handle(Player player) {
      ClientProxy.getNetworkHandler().handleGuiRequest(this);
   }

   public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<OpenGuiRequest> type() {
      return TYPE;
   }

   public static enum Type {
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
      PLAYER_INTERACT;
   }
}
