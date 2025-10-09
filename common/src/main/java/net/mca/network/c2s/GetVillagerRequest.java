package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.network.s2c.GetVillagerResponse;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

public class GetVillagerRequest implements Message {
    @Serial
    private static final long serialVersionUID = -4415670234855916259L;

    private final UUID uuid;

    public GetVillagerRequest(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(ServerPlayer player) {
        Entity e = player.serverLevel().getEntity(uuid);
        CompoundTag villagerData = getVillagerData(e);
        if (villagerData != null) {
            NetworkHandler.sendToPlayer(new GetVillagerResponse(villagerData), player);
        }
    }

    private static void storeNode(CompoundTag data, Optional<FamilyTreeNode> entry, String prefix) {
        if (entry.isPresent()) {
            data.putString("tree_" + prefix + "_name", entry.get().getName());
            data.putUUID("tree_" + prefix + "_uuid", entry.get().id());
        } else {
            data.putString("tree_" + prefix + "_name", "");
            data.putUUID("tree_" + prefix + "_uuid", Util.NIL_UUID);
        }
    }

    public static CompoundTag getVillagerData(Entity e) {
        CompoundTag data;

        if (e instanceof ServerPlayer serverPlayer) {
            data = PlayerSaveData.get(serverPlayer).getEntityData();
        } else if (e instanceof LivingEntity) {
            data = new CompoundTag();
            ((Mob)e).addAdditionalSaveData(data);
        } else {
            return null;
        }

        FamilyTree tree = FamilyTree.get((ServerLevel)e.level());
        FamilyTreeNode entry = tree.getOrCreate(e);

        storeNode(data, tree.getOrEmpty(entry.partner()), "spouse");
        storeNode(data, tree.getOrEmpty(entry.father()), "father");
        storeNode(data, tree.getOrEmpty(entry.mother()), "mother");

        return data;
    }
}
