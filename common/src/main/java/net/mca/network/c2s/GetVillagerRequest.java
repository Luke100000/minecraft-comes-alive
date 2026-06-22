package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.GetVillagerResponse;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;

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
    public void receive(ServerPlayerEntity player) {
        Entity e = player.getServerWorld().getEntity(uuid);
        NbtCompound villagerData = getVillagerData(e);
        if (villagerData != null) {
            NetworkHandler.sendToPlayer(new GetVillagerResponse(villagerData), player);
        }
    }

    private static void storeNode(NbtCompound data, Optional<FamilyTreeNode> entry, String prefix) {
        if (entry.isPresent()) {
            data.putString("tree_" + prefix + "_name", entry.get().getName());
            data.putUuid("tree_" + prefix + "_uuid", entry.get().id());
        } else {
            data.putString("tree_" + prefix + "_name", "");
            data.putUuid("tree_" + prefix + "_uuid", Util.NIL_UUID);
        }
    }

    public static NbtCompound getVillagerData(Entity e) {
        NbtCompound data;

        if (e instanceof ServerPlayerEntity serverPlayer) {
            data = PlayerSaveData.get(serverPlayer).getEntityData();
        } else if (e instanceof LivingEntity) {
            data = new NbtCompound();
            ((MobEntity)e).writeCustomDataToNbt(data);
        } else {
            return null;
        }

        FamilyTree tree = FamilyTree.get((ServerWorld)e.getWorld());
        FamilyTreeNode entry = tree.getOrCreate(e);

        storeNode(data, tree.getOrEmpty(entry.partner()), "spouse");
        storeNode(data, tree.getOrEmpty(entry.father()), "father");
        storeNode(data, tree.getOrEmpty(entry.mother()), "mother");

        return data;
    }
}
