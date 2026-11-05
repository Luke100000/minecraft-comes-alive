package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public record SaveVillageMessage(
        int id,
        float taxes,
        float populationThreshold,
        float marriageThreshold
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<SaveVillageMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("save_village"));
    public static final StreamCodec<FriendlyByteBuf, SaveVillageMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SaveVillageMessage::id,
            ByteBufCodecs.FLOAT, SaveVillageMessage::taxes,
            ByteBufCodecs.FLOAT, SaveVillageMessage::populationThreshold,
            ByteBufCodecs.FLOAT, SaveVillageMessage::marriageThreshold,
            SaveVillageMessage::new
    );

    public SaveVillageMessage(Village village) {
        this(village.getId(), village.getTaxes(), village.getPopulationThreshold(), village.getMarriageThreshold());
    }

    @Override
    public void handleServer(ServerPlayer player) {
        Village.findNearest(player).ifPresent(village ->
                applyTo(village, Tasks.getRank(village, player)));
    }

    public void applyTo(Village village, Rank rank) {
        if (village == null || rank == null || village.getId() != id) {
            return;
        }
        if (rank.isAtLeast(Rank.MERCHANT) && Float.isFinite(taxes)) {
            village.setTaxes(Mth.clamp(taxes, 0.0f, 1.0f));
        }
        if (rank.isAtLeast(Rank.NOBLE) && Float.isFinite(populationThreshold)) {
            village.setPopulationThreshold(Mth.clamp(populationThreshold, 0.0f, 1.0f));
        }
        if (rank.isAtLeast(Rank.MAYOR) && Float.isFinite(marriageThreshold)) {
            village.setMarriageThreshold(Mth.clamp(marriageThreshold, 0.0f, 1.0f));
        }
    }

    @Override
    public Type<SaveVillageMessage> type() {
        return TYPE;
    }
}
