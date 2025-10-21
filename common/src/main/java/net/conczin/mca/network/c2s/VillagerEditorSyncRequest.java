package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairList;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public record VillagerEditorSyncRequest(String command, UUID uuid, CompoundTag data) implements HandleablePayload {
    public static final CustomPacketPayload.Type<VillagerEditorSyncRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("villager_editor_sync_request"));
    public static final StreamCodec<FriendlyByteBuf, VillagerEditorSyncRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VillagerEditorSyncRequest::command,
            UUIDUtil.STREAM_CODEC, VillagerEditorSyncRequest::uuid,
            ByteBufCodecs.COMPOUND_TAG, VillagerEditorSyncRequest::data,
            VillagerEditorSyncRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity entity = player.serverLevel().getEntity(uuid);
        switch (command) {
            case "hair":
                setHair(player, entity);
                break;
            case "clothing":
                setClothing(player, entity);
                break;
            case "gender":
                setHair(player, entity);
                setClothing(player, entity);
                break;
            case "sync":
                saveEntity(player, entity, data());
                break;
            case "profession":
                if (entity instanceof VillagerEntityMCA villager) {
                    VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(data.getString("profession")));
                    villager.setProfession(profession);
                }
                break;
        }
    }

    private void setHair(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        if (villagerData != null) {
            // fetch hair
            String hair;
            if (data.contains("offset")) {
                hair = HairList.getInstance().getPool(getGender(villagerData)).pickNext(villagerData.getString("Hair"), data.getInt("offset"));
            } else {
                hair = HairList.getInstance().getPool(getGender(villagerData)).pickOne();
            }

            // set
            villagerData.putString("Hair", hair);
            saveEntity(player, entity, villagerData);
        }
    }

    private void setClothing(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        if (villagerData != null) {
            String clothes = "mca:missing";
            if (entity instanceof Player) {
                if (data.contains("offset")) {
                    clothes = ClothingList.getInstance().getPool(getGender(villagerData), VillagerProfession.NONE).pickNext(villagerData.getString("Clothes"), data.getInt("offset"));
                } else {
                    clothes = ClothingList.getInstance().getPool(getGender(villagerData), VillagerProfession.NONE).pickOne();
                }
            } else if (entity instanceof VillagerLike<?> villager) {
                if (data.contains("offset")) {
                    clothes = ClothingList.getInstance().getPool(villager).pickNext(villager.getClothes(), data.getInt("offset"));
                } else {
                    clothes = ClothingList.getInstance().getPool(villager).pickOne();
                }
            }
            villagerData.putString("Clothes", clothes);
            saveEntity(player, entity, villagerData);
        }
    }

    private void saveEntity(ServerPlayer player, Entity entity, CompoundTag villagerData) {
        if (entity instanceof ServerPlayer serverPlayer) {
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            data.setEntityData(villagerData);
            data.setEntityDataSet(true);
            syncFamilyTree(player, entity, villagerData);

            //also update players
            serverPlayer.serverLevel().players().forEach(p -> Network.sendToPlayer(new PlayerDataMessage(player.getUUID(), villagerData), p));
        } else if (entity instanceof VillagerLike<?> villagerLike) {
            villagerLike.syncFromEditor(villagerData);
            entity.refreshDimensions();
            syncFamilyTree(player, entity, villagerData);

            if (entity instanceof VillagerEntityMCA villager) {
                villager.getResidency().getHomeVillage().ifPresent(b -> b.updateResident(villager));
            }
        }
    }

    private Gender getGender(CompoundTag villagerData) {
        return Gender.byId(villagerData.getInt("gender"));
    }

    private Optional<FamilyTreeNode> getFamilyNode(ServerPlayer player, FamilyTree tree, String name, Gender gender) {
        try {
            UUID uuid = UUID.fromString(name);
            Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
            if (node.isPresent()) {
                player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_known", name, node.get().getName()), true);
                return node;
            } else {
                player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_unknown", name).withStyle(ChatFormatting.RED), true);
                return Optional.empty();
            }
        } catch (IllegalArgumentException exception) {
            List<FamilyTreeNode> nodes = tree.getAllWithName(name).toList();
            if (nodes.isEmpty()) {
                //create a new entry
                player.displayClientMessage(Component.translatable("gui.villager_editor.name_created", name).withStyle(ChatFormatting.YELLOW), true);
                return Optional.of(tree.getOrCreate(UUID.randomUUID(), name, gender));
            } else {
                if (nodes.size() > 1) {
                    player.displayClientMessage(Component.translatable("gui.villager_editor.name_not_unique", name).withStyle(ChatFormatting.RED), true);

                    String uuids = nodes.stream().map(FamilyTreeNode::id).map(UUID::toString).collect(Collectors.joining(", "));
                    player.displayClientMessage(Component.translatable("gui.villager_editor.list_of_ids", uuids), false);
                } else {
                    player.displayClientMessage(Component.translatable("gui.villager_editor.name_unique", name), true);
                }

                return Optional.ofNullable(nodes.getFirst());
            }
        }
    }

    private void syncFamilyTree(ServerPlayer player, Entity entity, CompoundTag villagerData) {
        FamilyTree tree = FamilyTree.get((ServerLevel) entity.level());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(getGender(data));

        String s = villagerData.getString("CustomName");
        try {
            entry.setName(Objects.requireNonNull(Component.Serializer.fromJson(s, entity.registryAccess())).getString());
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to parse custom name for villager: {}", s, e);
        }

        if (villagerData.contains("FamilyTreeNewFatherName")) {
            String name = villagerData.getString("FamilyTreeNewFatherName");
            if (MCA.isBlankString(name)) {
                entry.removeFather();
            } else {
                getFamilyNode(player, tree, name, Gender.MALE).ifPresent(entry::setFather);
            }
        }

        if (villagerData.contains("FamilyTreeNewMotherName")) {
            String name = villagerData.getString("FamilyTreeNewMotherName");
            if (MCA.isBlankString(name)) {
                entry.removeMother();
            } else {
                getFamilyNode(player, tree, name, Gender.FEMALE).ifPresent(entry::setMother);
            }
        }

        if (villagerData.contains("FamilyTreeNewSpouseName")) {
            String name = villagerData.getString("FamilyTreeNewSpouseName");
            if (MCA.isBlankString(name)) {
                Optional.of(entry.partner()).flatMap(tree::getOrEmpty).ifPresent(node -> node.updatePartner(null, null));
                entry.updatePartner(null, null);
            } else {
                getFamilyNode(player, tree, name, entry.gender().opposite()).ifPresent(node -> {
                    entry.updatePartner(node);
                    node.updatePartner(entry);
                });
            }
        }
    }

    @Override
    public Type<VillagerEditorSyncRequest> type() {
        return TYPE;
    }
}
