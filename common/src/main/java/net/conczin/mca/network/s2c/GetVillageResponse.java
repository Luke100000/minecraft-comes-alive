package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.resources.data.tasks.Task;
import net.conczin.mca.resources.data.tasks.TaskRegistry;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.function.IntFunction;

public record GetVillageResponse(
        CompoundTag data,
        Rank rank,
        int reputation,
        boolean isVillage,
        Set<String> ids,
        Map<Rank, List<Task>> tasks,
        Map<String, BuildingType> buildingTypes
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetVillageResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_village_response"));

    private static final IntFunction<Rank> RANK_BY_ID = ByIdMap.continuous(
            Rank::ordinal, Rank.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Rank> RANK_CODEC =
            ByteBufCodecs.idMapper(RANK_BY_ID, Rank::ordinal);

    private static final StreamCodec<FriendlyByteBuf, Set<String>> IDS_CODEC = ByteBufCodecs.collection(
            HashSet::new, ByteBufCodecs.STRING_UTF8
    );

    private static final StreamCodec<FriendlyByteBuf, Map<Rank, List<Task>>> TASKS_CODEC = ByteBufCodecs.map(
            HashMap::new,
            RANK_CODEC,
            TaskRegistry.CODEC.apply(ByteBufCodecs.list())
    );

    private static final StreamCodec<FriendlyByteBuf, Map<String, BuildingType>> BUILDING_TYPES_CODEC = ByteBufCodecs.map(
            HashMap::new, ByteBufCodecs.STRING_UTF8, BuildingType.STREAM_CODEC
    );

    public static final StreamCodec<FriendlyByteBuf, GetVillageResponse> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {
                ByteBufCodecs.COMPOUND_TAG.encode(buf, value.data());
                RANK_CODEC.encode(buf, value.rank());
                ByteBufCodecs.VAR_INT.encode(buf, value.reputation());
                ByteBufCodecs.BOOL.encode(buf, value.isVillage());
                IDS_CODEC.encode(buf, value.ids());
                TASKS_CODEC.encode(buf, value.tasks());
                BUILDING_TYPES_CODEC.encode(buf, value.buildingTypes());
            }, buf -> new GetVillageResponse(
                    ByteBufCodecs.COMPOUND_TAG.decode(buf),
                    RANK_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    IDS_CODEC.decode(buf),
                    TASKS_CODEC.decode(buf),
                    BUILDING_TYPES_CODEC.decode(buf)
            ));

    public GetVillageResponse(Village data, Rank rank, int reputation, boolean isVillage, Set<String> ids) {
        this(data.saveForClient(), rank, reputation, isVillage, ids, Tasks.getInstance().tasks, BuildingTypes.getInstance().getServerBuildingTypes());
    }

    public CompoundTag getData() {
        return data;
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleVillageDataResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<GetVillageResponse> type() {
        return TYPE;
    }
}
