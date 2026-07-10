package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetVillagerResponse;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.Optional;
import java.util.UUID;

public record GetVillagerRequest(UUID id) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetVillagerRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_villager_request"));
    public static final StreamCodec<FriendlyByteBuf, GetVillagerRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GetVillagerRequest::id,
            GetVillagerRequest::new
    );

    private static void storeNode(CompoundTag data, Optional<FamilyTreeNode> entry, String prefix) {
        if (entry.isPresent()) {
            data.putString("FamilyTree" + prefix + "Name", entry.get().getName());
        } else {
            data.putString("FamilyTree" + prefix + "Name", "");
        }
    }

    public static CompoundTag getVillagerData(Entity e) {
        CompoundTag data;

        if (e instanceof ServerPlayer serverPlayer) {
            data = PlayerSaveData.get(serverPlayer).getEntityData();
        } else if (e instanceof LivingEntity) {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, e.registryAccess());
            e.saveAsPassenger(output);
            data = output.buildResult();
        } else {
            return null;
        }

        FamilyTree tree = FamilyTree.get((ServerLevel) e.level());
        FamilyTreeNode entry = tree.getOrCreate(e);
        data.putString("FamilyTreeName", entry.getName());

        storeNode(data, tree.getOrEmpty(entry.partner()), "Spouse");
        storeNode(data, tree.getOrEmpty(entry.father()), "Father");
        storeNode(data, tree.getOrEmpty(entry.mother()), "Mother");

        return data;
    }

    @Override
    public void handleServer(ServerPlayer player) {
        Entity e = player.level().getEntity(id);
        CompoundTag villagerData = getVillagerData(e);
        if (villagerData != null) {
            Network.sendToPlayer(new GetVillagerResponse(villagerData), player);
        }
    }

    @Override
    public Type<GetVillagerRequest> type() {
        return TYPE;
    }
}
