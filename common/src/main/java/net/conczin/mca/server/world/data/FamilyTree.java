package net.conczin.mca.server.world.data;

import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class FamilyTree extends SavedData {
    private static final String DATA_ID = "MCA-FamilyTree";

    private final Map<UUID, FamilyTreeNode> entries;

    public static FamilyTree get(ServerLevel world) {
        return WorldUtils.loadData(world.getServer().overworld(), FamilyTree::new, FamilyTree::new, DATA_ID);
    }

    FamilyTree(ServerLevel world) {
        entries = new HashMap<>();
    }

    FamilyTree(CompoundTag nbt) {
        entries = NbtHelper.toMap(nbt, UUID::fromString, (id, element) -> new FamilyTreeNode(this, id, (CompoundTag)element));

        // Fixing the shift in relationships introduces by the promised update
        // TODO the moment this code here causes any porting errors, remove it. By that time no old saves should exist anyways.
        UUID uuid = UUID.fromString("12341234-1234-1234-1234-123412341234");
        if (!entries.containsKey(uuid)) {
            entries.put(uuid, createEntry(uuid, "debug", Gender.NEUTRAL, false));
            setDirty();

            entries.values().forEach(e -> {
                FamilyTreeNode partner = entries.get(e.partner());
                boolean partnerIsPlayer = partner != null && partner.isPlayer();
                if (e.getRelationshipState() == RelationshipState.ENGAGED && partnerIsPlayer == e.isPlayer()) {
                    //this is a villager-villager or player-player relationship. They do not have engagement
                    e.setRelationshipState(RelationshipState.MARRIED_TO_VILLAGER);
                }
                if (e.getRelationshipState() == RelationshipState.MARRIED_TO_VILLAGER && partnerIsPlayer) {
                    //The partner is not a villager
                    e.setRelationshipState(RelationshipState.MARRIED_TO_PLAYER);
                }
                if (e.getRelationshipState() == RelationshipState.MARRIED_TO_PLAYER && !partnerIsPlayer) {
                    //The partner is not a player
                    e.setRelationshipState(RelationshipState.WIDOW);
                }
            });
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        return NbtHelper.fromMap(nbt, entries, UUID::toString, FamilyTreeNode::save);
    }

    public Optional<FamilyTreeNode> getOrEmpty(@Nullable UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entries.get(id));
    }

    public Stream<FamilyTreeNode> getAllWithName(String name) {
        return entries.values().stream().filter(n -> n.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)));
    }

    @NotNull
    public FamilyTreeNode getOrCreate(Entity entity) {
        return entries.computeIfAbsent(entity.getUUID(), uuid -> createEntry(
                entity.getUUID(),
                entity.getName().getString(),
                EntityRelationship.of(entity).map(EntityRelationship::getGender).orElse(Gender.MALE),
                entity instanceof Player));
    }

    public void remove(UUID id) {
        entries.remove(id);
        setDirty();
    }

    @NotNull
    public FamilyTreeNode getOrCreate(UUID id, String name, Gender gender) {
        return getOrCreate(id, name, gender, false);
    }

    @NotNull
    public FamilyTreeNode getOrCreate(UUID id, String name, Gender gender, boolean isPlayer) {
        return entries.computeIfAbsent(id, uuid -> createEntry(uuid, name, gender, isPlayer));
    }

    private FamilyTreeNode createEntry(UUID uuid, String name, Gender gender, boolean isPlayer) {
        setDirty();
        return new FamilyTreeNode(this,
                uuid,
                name,
                isPlayer,
                gender,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
    }
}
