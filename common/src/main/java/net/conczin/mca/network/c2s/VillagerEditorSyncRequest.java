package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.resources.API;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairList;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.LayeredHairList;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
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

    public VillagerEditorSyncRequest {
        data = data.copy();
    }

    public CompoundTag data() {
        return data.copy();
    }

    @Override
    public void handleServer(ServerPlayer player) {
        Entity entity = player.level().getEntity(uuid);
        switch (command) {
            case "skin":
                setSkin(player, entity);
                break;
            case "hair":
                setHair(player, entity);
                break;
            case "layered_hair":
                setLayeredHair(player, entity);
                break;
            case "hair_base":
                setLayeredHair(player, entity, LayeredHair.Category.BASE);
                break;
            case "hair_bangs":
                setLayeredHair(player, entity, LayeredHair.Category.BANGS);
                break;
            case "hair_back":
                setLayeredHair(player, entity, LayeredHair.Category.BACK);
                break;
            case "hair_front":
                setLayeredHair(player, entity, LayeredHair.Category.FRONT);
                break;
            case "hair_extra":
                setLayeredHair(player, entity, LayeredHair.Category.EXTRA);
                break;
            case "clothing":
                setClothing(player, entity);
                break;
            case "gender":
                setSkin(player, entity);
                setClothing(player, entity);
                break;
            case "sync":
                saveEntity(player, entity, data());
                break;
            case "profession":
                if (entity instanceof VillagerEntityMCA villager) {
                    VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getValue(Identifier.parse(data.getString("profession").orElse("minecraft:none")));
                    villager.setProfession(profession);
                }
                break;
        }
    }

    private void setSkin(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        BodySkinList list = BodySkinList.getInstance();
        if (villagerData != null && list != null) {
            CompoundTag mcaData = getOrCreateMcaData(villagerData);
            String skin;
            if (data.contains("offset")) {
                skin = list.getPool(getGender(villagerData)).pickNext(mcaData.getString("Skin").orElse(""), data.getInt("offset").orElse(0));
            } else {
                skin = list.getPool(getGender(villagerData)).pickOne();
            }
            mcaData.putString("Skin", skin);
            saveEntity(player, entity, villagerData);
        }
    }

    private void setHair(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        if (villagerData != null) {
            CompoundTag mcaData = getOrCreateMcaData(villagerData);

            List<HairStyle> styles = getHairStyles(getGender(villagerData));
            if (styles.isEmpty()) {
                return;
            }
            HairStyle style;
            if (data.contains("offset")) {
                String currentStyleId = getCurrentHairStyleId(mcaData, styles);
                style = pickNextHairStyle(styles, currentStyleId, data.getInt("offset").orElse(0));
            } else {
                style = pickRandomHairStyle(styles);
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
            mcaData.putString("Hair", "");
            mcaData.putString("HairStyle", "");
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
                hair = list.getPool(category, getGender(villagerData)).pickNext(mcaData.getString(key).orElse(""), data.getInt("offset").orElse(0));
            } else {
                hair = list.pick(category, getGender(villagerData));
            }

            mcaData.putString("Hair", "");
            mcaData.putString("HairStyle", "");
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
                VillagerProfession noneProfession = BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.NONE);
                if (data.contains("offset")) {
                    clothes = ClothingList.getInstance().getPool(getGender(villagerData), noneProfession).pickNext(mcaData.getString("Clothes").orElse(""), data.getInt("offset").orElse(0));
                } else {
                    clothes = ClothingList.getInstance().getPool(getGender(villagerData), noneProfession).pickOne();
                }
            } else if (entity instanceof VillagerLike<?> villager) {
                if (data.contains("offset")) {
                    clothes = ClothingList.getInstance().getPool(villager).pickNext(villager.getClothes(), data.getInt("offset").orElse(0));
                } else {
                    clothes = ClothingList.getInstance().getPool(villager).pickOne();
                }
            }
            mcaData.putString("Clothes", clothes);
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
            serverPlayer.level().players().forEach(p -> Network.sendToPlayer(new PlayerDataMessage(serverPlayer.getUUID(), villagerData), p));
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
        return Gender.byId(getMcaData(villagerData).getInt("Gender").orElse(0));
    }

    private CompoundTag getMcaData(CompoundTag villagerData) {
        return NbtHelper.getCompoundOrSelf(villagerData, VillagerEntityMCA.MCA_DATA_KEY);
    }

    private CompoundTag getOrCreateMcaData(CompoundTag villagerData) {
        return NbtHelper.getOrCreateCompound(villagerData, VillagerEntityMCA.MCA_DATA_KEY);
    }

    private List<HairStyle> getHairStyles(Gender gender) {
        HairStyleList styles = HairStyleList.getInstance();
        if (styles == null) {
            return List.of();
        }

        HairList hairList = HairList.getInstance();
        Map<String, Hair> legacyHair = hairList == null ? Map.of() : hairList.hair;
        return styles.getAllStyles(legacyHair).values().stream()
                .filter(style -> style.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || style.getGender() == gender)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    private HairStyle pickRandomHairStyle(List<HairStyle> styles) {
        double totalChance = styles.stream().mapToDouble(HairStyle::getChance).sum() * API.getRng().nextDouble();
        for (HairStyle style : styles) {
            totalChance -= style.getChance();
            if (totalChance <= 0.0) {
                return style;
            }
        }
        return styles.get(styles.size() - 1);
    }

    private HairStyle pickNextHairStyle(List<HairStyle> styles, String currentStyleId, int offset) {
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).getIdentifier().equals(currentStyleId)) {
                return styles.get(Math.floorMod(i + offset, styles.size()));
            }
        }
        return styles.get(offset < 0 ? styles.size() - 1 : 0);
    }

    private String getCurrentHairStyleId(CompoundTag mcaData, List<HairStyle> styles) {
        String legacyHair = mcaData.getString("Hair").orElse("");
        if (!MCA.isBlankString(legacyHair)) {
            return legacyHair;
        }
        String storedStyle = mcaData.getString("HairStyle").orElse("");
        if (!MCA.isBlankString(storedStyle)) {
            return storedStyle;
        }
        return styles.stream()
                .filter(style -> matchesHairStyle(mcaData, style))
                .map(HairStyle::getIdentifier)
                .findFirst()
                .orElse("");
    }

    private boolean matchesHairStyle(CompoundTag mcaData, HairStyle style) {
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            String selectedLayer = mcaData.getString(category.getDataKey()).orElse("");
            if (!selectedLayer.equals(style.layer(category))) {
                return false;
            }
        }
        return true;
    }

    private void applyHairStyle(CompoundTag mcaData, HairStyle style) {
        if (style == null) {
            return;
        }

        mcaData.putString("HairStyle", style.getIdentifier());
        mcaData.putString("Hair", "");
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            mcaData.putString(category.getDataKey(), style.layer(category));
        }
    }

    private Optional<FamilyTreeNode> getFamilyNode(ServerPlayer player, FamilyTree tree, String name, Gender gender) {
        try {
            UUID uuid = UUID.fromString(name);
            Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
            if (node.isPresent()) {
                player.sendSystemMessage(Component.translatable("gui.villager_editor.uuid_known", name, node.get().getName()));
                return node;
            } else {
                player.sendSystemMessage(Component.translatable("gui.villager_editor.uuid_unknown", name).withStyle(ChatFormatting.RED));
                return Optional.empty();
            }
        } catch (IllegalArgumentException exception) {
            List<FamilyTreeNode> nodes = tree.getAllWithName(name).toList();
            if (nodes.isEmpty()) {
                //create a new entry
                player.sendSystemMessage(Component.translatable("gui.villager_editor.name_created", name).withStyle(ChatFormatting.YELLOW));
                return Optional.of(tree.getOrCreate(UUID.randomUUID(), name, gender));
            } else {
                if (nodes.size() > 1) {
                    player.sendSystemMessage(Component.translatable("gui.villager_editor.name_not_unique", name).withStyle(ChatFormatting.RED));

                    String uuids = nodes.stream().map(FamilyTreeNode::id).map(UUID::toString).collect(Collectors.joining(", "));
                    player.sendSystemMessage(Component.translatable("gui.villager_editor.list_of_ids", uuids));
                } else {
                    player.sendSystemMessage(Component.translatable("gui.villager_editor.name_unique", name));
                }

                return Optional.ofNullable(nodes.getFirst());
            }
        }
    }

    private void syncFamilyTree(ServerPlayer player, Entity entity, CompoundTag villagerData) {
        FamilyTree tree = FamilyTree.get((ServerLevel) entity.level());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(getGender(villagerData));

        VillagerLike.parseCustomName(entity.registryAccess(), villagerData)
                .map(Component::getString)
                .ifPresent(entry::setName);

        if (villagerData.contains("FamilyTreeNewFatherName")) {
            String name = villagerData.getString("FamilyTreeNewFatherName").orElse("");
            if (MCA.isBlankString(name)) {
                entry.removeFather();
            } else {
                getFamilyNode(player, tree, name, Gender.MALE).ifPresent(entry::setFather);
            }
        }

        if (villagerData.contains("FamilyTreeNewMotherName")) {
            String name = villagerData.getString("FamilyTreeNewMotherName").orElse("");
            if (MCA.isBlankString(name)) {
                entry.removeMother();
            } else {
                getFamilyNode(player, tree, name, Gender.FEMALE).ifPresent(entry::setMother);
            }
        }

        if (villagerData.contains("FamilyTreeNewSpouseName")) {
            String name = villagerData.getString("FamilyTreeNewSpouseName").orElse("");
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
