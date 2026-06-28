package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetVillagerResponse;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.LayeredHairList;
import net.conczin.mca.resources.SkinVisualIds;
import net.conczin.mca.resources.data.skin.HairStyle;
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
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record VillagerEditorSyncRequest(String command, UUID uuid, CompoundTag data) implements HandleablePayload {
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

    @Override
    public void handleServer(ServerPlayer player) {
        Entity entity = player.serverLevel().getEntity(uuid);
        switch (command) {
            case "skin" -> setSkin(player, entity);
            case "hair" -> setHair(player, entity);
            case "layered_hair" -> setLayeredHair(player, entity);
            case "hair_base" -> setLayeredHair(player, entity, LayeredHair.Category.BASE);
            case "hair_bangs" -> setLayeredHair(player, entity, LayeredHair.Category.BANGS);
            case "hair_back" -> setLayeredHair(player, entity, LayeredHair.Category.BACK);
            case "hair_front" -> setLayeredHair(player, entity, LayeredHair.Category.FRONT);
            case "hair_extra" -> setLayeredHair(player, entity, LayeredHair.Category.EXTRA);
            case "clothing" -> setClothing(player, entity);
            case "gender" -> {
                setSkin(player, entity);
                setClothing(player, entity);
            }
            case "sync" -> saveEntity(player, entity, data.copy());
            case "profession" -> {
                if (entity instanceof VillagerEntityMCA villager) {
                    VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(data.getString("profession")));
                    villager.setProfession(profession);
                }
            }
        }
    }

    private void setSkin(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        BodySkinList list = BodySkinList.getInstance();
        if (villagerData != null && list != null) {
            String skin;
            if (data.contains("offset")) {
                skin = list.getPool(getGender(villagerData)).pickNext(getStringValue(villagerData, "Skin"), data.getInt("offset"));
            } else {
                skin = list.getPool(getGender(villagerData)).pickOne();
            }
            getOrCreateMcaData(villagerData).putString("Skin", skin);
            saveEntity(player, entity, villagerData);
        }
    }

    private void setHair(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        HairStyleList styles = HairStyleList.getInstance();
        if (villagerData != null && styles != null) {
            CompoundTag mcaData = getOrCreateMcaData(villagerData);
            Gender gender = getGender(villagerData);

            HairStyle style;
            if (data.contains("offset")) {
                String currentStyleId = getCurrentHairStyleId(villagerData, styles, gender);
                style = styles.pickNext(gender, currentStyleId, data.getInt("offset"));
            } else {
                style = styles.pick(gender);
            }

            if (style == null) {
                return;
            }

            applyHairStyle(mcaData, style);
            saveEntity(player, entity, villagerData);
        }
    }

    private void setLayeredHair(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        LayeredHairList list = LayeredHairList.getInstance();
        if (villagerData != null && list != null) {
            CompoundTag mcaData = getOrCreateMcaData(villagerData);
            clearHair(mcaData);
            list.pickAll(getGender(villagerData)).forEach((category, hair) -> mcaData.putString(category.getDataKey(), hair));
            saveEntity(player, entity, villagerData);
        }
    }

    private void setLayeredHair(ServerPlayer player, Entity entity, LayeredHair.Category category) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        LayeredHairList list = LayeredHairList.getInstance();
        if (villagerData != null && list != null) {
            CompoundTag mcaData = getOrCreateMcaData(villagerData);
            String key = category.getDataKey();
            String hair;
            if (data.contains("offset")) {
                hair = list.getPool(category, getGender(villagerData)).pickNext(getStringValue(villagerData, key), data.getInt("offset"));
            } else {
                hair = list.pick(category, getGender(villagerData));
            }

            clearHair(mcaData);
            mcaData.putString(key, hair);
            saveEntity(player, entity, villagerData);
        }
    }

    private void setClothing(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        if (villagerData != null) {
            CompoundTag mcaData = getOrCreateMcaData(villagerData);
            String clothes = "mca:missing";
            if (entity instanceof Player) {
                if (data.contains("offset")) {
                    clothes = ClothingList.getInstance().getEditorPool(getGender(villagerData)).pickNext(getStringValue(villagerData, "Clothes"), data.getInt("offset"));
                } else {
                    clothes = ClothingList.getInstance().getPool(getGender(villagerData), VillagerProfession.NONE).pickOne();
                }
            } else if (entity instanceof VillagerLike<?> villager) {
                if (data.contains("offset")) {
                    clothes = ClothingList.getInstance().getEditorPool(villager.getGenetics().getGender()).pickNext(getStringValue(villagerData, "Clothes"), data.getInt("offset"));
                } else {
                    clothes = ClothingList.getInstance().getPool(villager).pickOne();
                }
            }
            mcaData.putString("Clothes", clothes);
            saveEntity(player, entity, villagerData);
        }
    }

    private void saveEntity(ServerPlayer player, Entity entity, CompoundTag villagerData) {
        sanitizeVisualIdentifiers(villagerData);
        if (entity instanceof ServerPlayer serverPlayer) {
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            data.setEntityData(villagerData);
            data.setEntityDataSet(true);
            syncFamilyTree(player, entity, villagerData);
            serverPlayer.serverLevel().players().forEach(p -> Network.sendToPlayer(new PlayerDataMessage(serverPlayer.getUUID(), villagerData), p));
        } else if (entity instanceof VillagerLike<?> villagerLike) {
            villagerLike.syncFromEditor(villagerData);
            entity.refreshDimensions();
            syncFamilyTree(player, entity, villagerData);

            if (entity instanceof VillagerEntityMCA villager) {
                villager.getResidency().getHomeVillage().ifPresent(b -> b.updateResident(villager));
            }
        }
        Network.sendToPlayer(new GetVillagerResponse(villagerData), player);
    }

    private void sanitizeVisualIdentifiers(CompoundTag villagerData) {
        CompoundTag mcaData = getOrCreateMcaData(villagerData);
        clearInvalidIdentifier(mcaData, "Skin", SkinVisualIds::isBodySkin);
        clearInvalidIdentifier(mcaData, "Clothes", SkinVisualIds::isClothing);
        clearInvalidIdentifier(mcaData, "Hair", SkinVisualIds::isHairStyle);
        clearInvalidIdentifier(mcaData, "HairStyle", SkinVisualIds::isHairStyle);
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            clearInvalidIdentifier(mcaData, category.getDataKey(), identifier -> SkinVisualIds.isHairLayer(identifier, category));
        }
    }

    private void clearInvalidIdentifier(CompoundTag mcaData, String key, Predicate<String> validator) {
        String identifier = mcaData.getString(key);
        if (!MCA.isBlankString(identifier) && !validator.test(identifier)) {
            MCA.LOGGER.warn("Ignoring unknown villager editor visual identifier {}={}", key, identifier);
            mcaData.putString(key, "");
        }
    }

    private Gender getGender(CompoundTag villagerData) {
        CompoundTag mcaData = getMcaData(villagerData);
        if (mcaData.contains("Gender")) {
            return Gender.byId(mcaData.getInt("Gender"));
        }
        if (villagerData.contains("Gender")) {
            return Gender.byId(villagerData.getInt("Gender"));
        }
        return Gender.UNASSIGNED;
    }

    private CompoundTag getMcaData(CompoundTag villagerData) {
        return villagerData.contains(VillagerEntityMCA.MCA_DATA_KEY, 10) ? villagerData.getCompound(VillagerEntityMCA.MCA_DATA_KEY) : villagerData;
    }

    private CompoundTag getOrCreateMcaData(CompoundTag villagerData) {
        if (!villagerData.contains(VillagerEntityMCA.MCA_DATA_KEY, 10)) {
            CompoundTag mcaData = new CompoundTag();
            if (villagerData.contains("Gender")) {
                mcaData.putInt("Gender", villagerData.getInt("Gender"));
            }
            villagerData.put(VillagerEntityMCA.MCA_DATA_KEY, mcaData);
        }
        return villagerData.getCompound(VillagerEntityMCA.MCA_DATA_KEY);
    }

    private String getStringValue(CompoundTag villagerData, String key) {
        if (villagerData.contains(key)) {
            return villagerData.getString(key);
        }
        if (villagerData.contains(VillagerEntityMCA.MCA_DATA_KEY, 10)) {
            CompoundTag mcaData = villagerData.getCompound(VillagerEntityMCA.MCA_DATA_KEY);
            if (mcaData.contains(key)) {
                return mcaData.getString(key);
            }
        }
        return "";
    }

    private String getCurrentHairStyleId(CompoundTag villagerData, HairStyleList styles, Gender gender) {
        String storedStyle = getStringValue(villagerData, "HairStyle");
        if (!MCA.isBlankString(storedStyle)) {
            return storedStyle;
        }
        String legacyHair = getStringValue(villagerData, "Hair");
        if (!MCA.isBlankString(legacyHair)) {
            return legacyHair;
        }
        return styles.findMatchingStyleId(gender, category -> getStringValue(villagerData, category.getDataKey())).orElse("");
    }

    private void applyHairStyle(CompoundTag mcaData, HairStyle style) {
        mcaData.putString("HairStyle", style.getIdentifier());
        mcaData.putString("Hair", "");
        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            mcaData.putString(category.getDataKey(), style.layer(category));
        }
    }

    private void clearHair(CompoundTag mcaData) {
        mcaData.putString("Hair", "");
        mcaData.putString("HairStyle", "");
        clearLayeredHair(mcaData);
    }

    private void clearLayeredHair(CompoundTag mcaData) {
        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            mcaData.putString(category.getDataKey(), "");
        }
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
