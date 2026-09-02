package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetVillagerResponse;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.resources.SkinVisualIds;
import net.conczin.mca.resources.data.skin.LayeredHair;
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record VillagerEditorSyncRequest(String command, UUID uuid, CompoundTag data) implements HandleablePayload {
    private static final String[] MCA_VISUAL_KEYS = {
            "Gender",
            "Clothes",
            "ClothingLocked",
            "Skin",
            "Hair",
            "HairStyle",
            "HairBase",
            "HairBangs",
            "HairBack",
            "HairFront",
            "HairExtra",
            "SkinColor",
            "HairColor",
            "EyeColor",
            "EyeColorLeft",
            "AgeState",
            "PlayerModel"
    };
    public static final CustomPacketPayload.Type<VillagerEditorSyncRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("villager_editor_sync_request"));
    public static final StreamCodec<FriendlyByteBuf, VillagerEditorSyncRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VillagerEditorSyncRequest::command,
            UUIDUtil.STREAM_CODEC, VillagerEditorSyncRequest::uuid,
            ByteBufCodecs.COMPOUND_TAG, VillagerEditorSyncRequest::data,
            VillagerEditorSyncRequest::new
    );

    public VillagerEditorSyncRequest {
        data = data.copy();
    }

    public static boolean isAllowedTopLevelKey(String key) {
        return key.equals("Age") ||
               key.equals("CustomName") ||
               key.equals("CustomNameVisible") ||
               key.equals("FamilyTreeNewFatherName") ||
               key.equals("FamilyTreeNewMotherName") ||
               key.equals("FamilyTreeNewSpouseName") ||
               key.equals("VillagerDataFinalized");
    }

    public static boolean isAllowedMcaKey(String key) {
        if (key.startsWith("Gene")) {
            return true;
        }
        if (key.equals("Personality") || key.equals("Traits")) {
            return true;
        }
        for (String visualKey : MCA_VISUAL_KEYS) {
            if (visualKey.equals(key)) {
                return true;
            }
        }
        if (key.equals("HairDye") || key.equals("SkinDye") || key.equals("EyeDye") || key.equals("EyeLeftDye")) {
            return true;
        }
        if (key.equals("InfectionProgress") || key.equals("Mood") || key.equals("Memories")) {
            return true;
        }
        return false;
    }

    public static CompoundTag createEditorPatch(CompoundTag sourceNbt) {
        CompoundTag patch = new CompoundTag();
        for (String key : sourceNbt.getAllKeys()) {
            if (isAllowedTopLevelKey(key) || isAllowedMcaKey(key)) {
                patch.put(key, Objects.requireNonNull(sourceNbt.get(key)).copy());
            }
        }
        return patch;
    }

    public static CompoundTag mergeAllowedEditorPatch(CompoundTag serverData, CompoundTag patch) {
        CompoundTag merged = serverData.copy();
        for (String key : patch.getAllKeys()) {
            if (isAllowedTopLevelKey(key) || isAllowedMcaKey(key)) {
                merged.put(key, Objects.requireNonNull(patch.get(key)).copy());
            }
        }
        return merged;
    }

    @Override
    public void handleServer(ServerPlayer player) {
        Entity entity = player.serverLevel().getEntity(uuid);
        switch (command) {
            case "skin", "hair", "layered_hair", "hair_base", "hair_bangs", "hair_back", "hair_front", "hair_extra", "clothing", "gender", "sync" ->
                    saveEntity(player, entity, data.copy());
            case "profession" -> {
                if (entity instanceof VillagerEntityMCA villager) {
                    VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(data.getString("profession")));
                    if (profession != null) {
                        CompoundTag merged = applyVillagerPatch(player, entity, data.copy());
                        villager.setProfession(profession);
                        if (merged != null) {
                            CompoundTag fresh = GetVillagerRequest.getVillagerData(entity);
                            Network.sendToPlayer(new GetVillagerResponse(fresh != null ? fresh : merged), player);
                        }
                    }
                }
            }
        }
    }

    private void saveEntity(ServerPlayer player, Entity entity, CompoundTag patch) {
        if (entity == null) {
            return;
        }

        if (entity instanceof ServerPlayer serverPlayer) {
            CompoundTag serverData = PlayerSaveData.get(serverPlayer).getEntityData();
            CompoundTag merged = mergeAllowedEditorPatch(serverData, patch);
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            data.setEntityData(merged);
            data.setEntityDataSet(true);
            syncFamilyTree(player, entity, merged);
            serverPlayer.serverLevel().players().forEach(p -> Network.sendToPlayer(new PlayerDataMessage(serverPlayer.getUUID(), merged), p));
            Network.sendToPlayer(new GetVillagerResponse(merged), player);
            return;
        }

        CompoundTag merged = applyVillagerPatch(player, entity, patch);
        if (merged != null) {
            CompoundTag fresh = GetVillagerRequest.getVillagerData(entity);
            Network.sendToPlayer(new GetVillagerResponse(fresh != null ? fresh : merged), player);
        }
    }

    private CompoundTag applyVillagerPatch(ServerPlayer player, Entity entity, CompoundTag patch) {
        if (!(entity instanceof VillagerLike<?> villagerLike)) {
            return null;
        }

        CompoundTag serverData = GetVillagerRequest.getVillagerData(entity);
        if (serverData == null) {
            return null;
        }

        CompoundTag merged = mergeAllowedEditorPatch(serverData, patch);
        sanitizeVisualIdentifiers(entity, merged);

        villagerLike.syncFromEditor(merged);
        entity.refreshDimensions();
        syncFamilyTree(player, entity, merged);

        if (entity instanceof VillagerEntityMCA villager) {
            villager.getResidency().getHomeVillage().ifPresent(b -> b.updateResident(villager));
        }

        return merged;
    }

    private void sanitizeVisualIdentifiers(Entity entity, CompoundTag villagerData) {
        migrateLegacyHairStyle(villagerData);
        CompoundTag fallbackData = GetVillagerRequest.getVillagerData(entity);
        CompoundTag fallback = fallbackData == null ? new CompoundTag() : fallbackData;
        Gender gender = getGender(villagerData);
        clearInvalidIdentifier(villagerData, fallback, "Skin", identifier -> SkinVisualIds.isBodySkin(identifier, gender));
        clearInvalidIdentifier(villagerData, fallback, "Clothes", identifier -> SkinVisualIds.isClothing(identifier, gender));
        clearInvalidIdentifier(villagerData, fallback, "Hair", identifier -> SkinVisualIds.isHairStyle(identifier, gender));
        clearInvalidIdentifier(villagerData, fallback, "HairStyle", identifier -> SkinVisualIds.isHairStyle(identifier, gender));
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            clearInvalidIdentifier(villagerData, fallback, category.getDataKey(), identifier -> SkinVisualIds.isHairLayer(identifier, category, gender));
        }
    }

    private void migrateLegacyHairStyle(CompoundTag mcaData) {
        String hairStyle = mcaData.getString("HairStyle");
        if (SkinVisualIds.isLegacyHairTexture(hairStyle)) {
            mcaData.putString("HairBase", hairStyle);
            mcaData.putString("HairStyle", "");
            mcaData.putString("Hair", "");
        }
    }

    private void clearInvalidIdentifier(CompoundTag mcaData, CompoundTag fallbackMcaData, String key, Predicate<String> validator) {
        String identifier = mcaData.getString(key);
        if (!MCA.isBlankString(identifier) && !validator.test(identifier)) {
            MCA.LOGGER.warn("Ignoring unknown villager editor visual identifier {}={}", key, identifier);
            String fallback = fallbackMcaData.getString(key);
            mcaData.putString(key, !MCA.isBlankString(fallback) && validator.test(fallback) ? fallback : "");
        }
    }

    private Gender getGender(CompoundTag villagerData) {
        if (villagerData.contains("Gender")) {
            return Gender.byId(villagerData.getInt("Gender"));
        }
        return Gender.UNASSIGNED;
    }

    private Optional<FamilyTreeNode> getFamilyNode(ServerPlayer player, FamilyTree tree, String name, Gender gender) {
        try {
            UUID uuid = UUID.fromString(name);
            Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
            if (node.isPresent()) {
                player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_known", name, node.get().getName()), false);
                return node;
            } else {
                player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_unknown", name).withStyle(ChatFormatting.RED), false);
                return Optional.empty();
            }
        } catch (IllegalArgumentException exception) {
            List<FamilyTreeNode> nodes = tree.getAllWithName(name).toList();
            if (nodes.isEmpty()) {
                player.displayClientMessage(Component.translatable("gui.villager_editor.name_created", name).withStyle(ChatFormatting.YELLOW), false);
                return Optional.of(tree.getOrCreate(UUID.randomUUID(), name, gender));
            } else {
                if (nodes.size() > 1) {
                    player.displayClientMessage(Component.translatable("gui.villager_editor.name_not_unique", name).withStyle(ChatFormatting.RED), false);
                    String uuids = nodes.stream().map(FamilyTreeNode::id).map(UUID::toString).collect(Collectors.joining(", "));
                    player.displayClientMessage(Component.translatable("gui.villager_editor.list_of_ids", uuids), false);
                } else {
                    player.displayClientMessage(Component.translatable("gui.villager_editor.name_unique", name), false);
                }

                return Optional.ofNullable(nodes.getFirst());
            }
        }
    }

    private void syncFamilyTree(ServerPlayer player, Entity entity, CompoundTag villagerData) {
        FamilyTree tree = FamilyTree.get((ServerLevel) entity.level());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(getGender(villagerData));

        if (villagerData.contains("CustomName", 8)) {
            String serializedName = villagerData.getString("CustomName");
            if (!serializedName.isEmpty()) {
                try {
                    Component name = Component.Serializer.fromJson(serializedName, entity.registryAccess());
                    if (name != null) {
                        entry.setName(name.getString());
                    }
                } catch (Exception exception) {
                    MCA.LOGGER.warn("Failed to parse custom name for villager: {}", serializedName, exception);
                }
            }
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
