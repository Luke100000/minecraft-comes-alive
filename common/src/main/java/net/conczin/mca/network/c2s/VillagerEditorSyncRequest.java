package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetVillagerResponse;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.resources.SkinVisualIds;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record VillagerEditorSyncRequest(String command, UUID uuid, CompoundTag data) implements HandleablePayload {
    private record ApprovedEdit(CompoundTag patch, boolean fullEditor) {
    }

    private static final Set<String> MCA_VISUAL_KEYS = Set.of(
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
    );

    private static final Set<String> HAIR_KEYS = Set.of(
            "Hair",
            "HairStyle",
            "HairBase",
            "HairBangs",
            "HairBack",
            "HairFront",
            "HairExtra",
            "HairColor",
            "HairDye"
    );

    private static final Set<String> CLOTHING_KEYS = Set.of(
            "Clothes",
            "ClothingLocked",
            "SkinDye"
    );

    private static final Set<String> LIMITED_PLAYER_KEYS = Set.of(
            "CustomName",
            "CustomNameVisible",
            "PlayerModel"
    );

    private static final Set<String> DYE_KEYS = Set.of(
            "HairDye",
            "SkinDye",
            "EyeDye",
            "EyeLeftDye"
    );

    private static final Set<String> PASSIVE_ECHOED_KEYS = Set.of(
            "Age",
            "AgeState",
            "InfectionProgress",
            "Memories",
            "Mood"
    );

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
        return MCA_VISUAL_KEYS.contains(key) || DYE_KEYS.contains(key)
                || key.equals("InfectionProgress") || key.equals("Mood") || key.equals("Memories");
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
    public void handleServer(ServerPlayer sender) {
        Entity target = sender.serverLevel().getEntity(uuid);
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.level() != sender.serverLevel()) {
            return;
        }
        if (!(target instanceof ServerPlayer) && !(target instanceof VillagerLike<?>)) {
            return;
        }
        boolean professionEdit = command.equals("profession");
        if (!professionEdit && !isStandardEditorCommand(command)) {
            return;
        }
        for (String key : data.getAllKeys()) {
            if (!isAllowedTopLevelKey(key) && !isAllowedMcaKey(key) && !(professionEdit && key.equals("profession"))) {
                return;
            }
        }

        PlayerSaveData playerData = target instanceof ServerPlayer targetPlayer ? PlayerSaveData.get(targetPlayer) : null;
        CompoundTag serverData = playerData == null ? GetVillagerRequest.getVillagerData(target) : playerData.getEntityData();
        if (serverData == null) {
            return;
        }

        ApprovedEdit approved = approveEdit(sender, target, serverData, professionEdit);
        if (approved == null) {
            return;
        }

        VillagerProfession profession = null;
        if (professionEdit) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString("profession"));
            if (id == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) {
                return;
            }
            profession = BuiltInRegistries.VILLAGER_PROFESSION.get(id);
        }

        CompoundTag merged = mergeAllowedEditorPatch(serverData, approved.patch());
        sanitizeVisualIdentifiers(merged, serverData);
        if (target instanceof ServerPlayer targetPlayer) {
            playerData.setEntityData(merged);
            playerData.setEntityDataSet(true);
            syncFamilyTree(sender, targetPlayer, merged, approved.fullEditor());
            targetPlayer.serverLevel().players().forEach(player ->
                    Network.sendToPlayer(new PlayerDataMessage(targetPlayer.getUUID(), merged), player));
            Network.sendToPlayer(new GetVillagerResponse(merged), sender);
        } else if (target instanceof VillagerLike<?> villager) {
            villager.syncFromEditor(merged);
            target.refreshDimensions();
            syncFamilyTree(sender, target, merged, approved.fullEditor());
            if (target instanceof VillagerEntityMCA mcaVillager) {
                mcaVillager.getResidency().getHomeVillage().ifPresent(village -> village.updateResident(mcaVillager));
                if (profession != null) {
                    mcaVillager.setProfession(profession);
                }
            }
            CompoundTag fresh = GetVillagerRequest.getVillagerData(target);
            Network.sendToPlayer(new GetVillagerResponse(fresh != null ? fresh : merged), sender);
        }
    }

    private ApprovedEdit approveEdit(ServerPlayer sender, Entity target, CompoundTag serverData, boolean professionEdit) {
        if (sender.hasPermissions(2) || (target instanceof VillagerLike<?> && sender.isHolding(ItemsMCA.VILLAGER_EDITOR))) {
            return !professionEdit || target instanceof VillagerEntityMCA ? new ApprovedEdit(data, true) : null;
        }
        if (professionEdit) {
            return null;
        }

        boolean self = target == sender;
        Config config = Config.getInstance();
        if (self && config.allowFullPlayerEditor
                && arePlayerTraitsValid(serverData, data) && isPersonalityValid(serverData, data)) {
            return new ApprovedEdit(selfEditPatch(), false);
        }
        if (self && ServerInteractionManager.getInstance().hasActiveDestinySession(sender)
                && isDestinyPatchPermitted(serverData)) {
            return new ApprovedEdit(selfEditPatch(), false);
        }
        boolean canUseTool = self || (target instanceof VillagerLike<?>
                && sender.distanceToSqr(target) <= InteractionVillagerMessage.MAX_INTERACTION_DISTANCE_SQR);
        if (canUseTool && sender.isHolding(ItemsMCA.COMB) && isCombCommand(command)
                && allChangedKeysIn(serverData, HAIR_KEYS)) {
            return new ApprovedEdit(retainOnlyKeys(data, HAIR_KEYS), false);
        }
        if (canUseTool && sender.isHolding(ItemsMCA.NEEDLE_AND_THREAD) && isNeedleCommand(command)
                && allChangedKeysIn(serverData, CLOTHING_KEYS)) {
            return new ApprovedEdit(retainOnlyKeys(data, CLOTHING_KEYS), false);
        }
        if (self && config.allowLimitedPlayerEditor && command.equals("sync")
                && allChangedKeysIn(serverData, LIMITED_PLAYER_KEYS)) {
            return new ApprovedEdit(retainOnlyKeys(data, LIMITED_PLAYER_KEYS), false);
        }
        return null;
    }

    private boolean isDestinyPatchPermitted(CompoundTag serverData) {
        Config config = Config.getInstance();
        for (String key : data.getAllKeys()) {
            if (isProtectedSelfEditKey(key) || Objects.equals(serverData.get(key), data.get(key))) {
                continue;
            }
            if (isDestinyIdentityOrVisualKey(key)) {
                continue;
            }
            if (key.equals("Personality")) {
                if (!isPersonalityValid(serverData, data)) {
                    return false;
                }
                continue;
            }
            if (key.startsWith("Gene")) {
                if (!config.allowBodyCustomizationInDestiny) {
                    return false;
                }
                continue;
            }
            if (key.equals("Traits")) {
                if (!config.allowTraitCustomizationInDestiny || !arePlayerTraitsValid(serverData, data)) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean allChangedKeysIn(CompoundTag serverData, Set<String> allowedChangedKeys) {
        for (String key : data.getAllKeys()) {
            if (key.startsWith("FamilyTreeNew")) {
                return false;
            }
            if (PASSIVE_ECHOED_KEYS.contains(key)) {
                continue;
            }
            if (!Objects.equals(serverData.get(key), data.get(key)) && !allowedChangedKeys.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPersonalityValid(CompoundTag serverData, CompoundTag patch) {
        if (!patch.contains("Personality") || Objects.equals(serverData.get("Personality"), patch.get("Personality"))) {
            return true;
        }
        return patch.contains("Personality", Tag.TAG_STRING)
                && Personality.get(patch.getString("Personality")).isPresent();
    }

    private static boolean arePlayerTraitsValid(CompoundTag serverData, CompoundTag patch) {
        if (!patch.contains("Traits") || Objects.equals(serverData.get("Traits"), patch.get("Traits"))) {
            return true;
        }
        if (!patch.contains("Traits", Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag previousTraits = serverData.contains("Traits", Tag.TAG_COMPOUND)
                ? serverData.getCompound("Traits")
                : new CompoundTag();
        CompoundTag nextTraits = patch.getCompound("Traits");
        Config config = Config.getInstance();
        for (String traitId : nextTraits.getAllKeys()) {
            if (previousTraits.contains(traitId)) {
                continue;
            }
            Optional<Traits.Trait> resolved = Traits.get(traitId);
            if (resolved.isEmpty()) {
                return false;
            }
            Traits.Trait trait = resolved.get();
            boolean enabled = config.enabledTraits.getOrDefault(trait.getId().toString(), true);
            if (!enabled || (!config.bypassTraitRestrictions && !trait.isUsableOnPlayer())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStandardEditorCommand(String cmd) {
        return switch (cmd) {
            case "skin", "hair", "layered_hair", "hair_base", "hair_bangs", "hair_back", "hair_front", "hair_extra", "clothing", "gender", "sync" -> true;
            default -> false;
        };
    }

    private static boolean isCombCommand(String cmd) {
        return switch (cmd) {
            case "hair", "layered_hair", "hair_base", "hair_bangs", "hair_back", "hair_front", "hair_extra", "sync" -> true;
            default -> false;
        };
    }

    private static boolean isNeedleCommand(String cmd) {
        return cmd.equals("clothing") || cmd.equals("sync");
    }

    private static boolean isDestinyIdentityOrVisualKey(String key) {
        return LIMITED_PLAYER_KEYS.contains(key) || DYE_KEYS.contains(key) || MCA_VISUAL_KEYS.contains(key);
    }

    private static boolean isProtectedSelfEditKey(String key) {
        return PASSIVE_ECHOED_KEYS.contains(key) || key.startsWith("FamilyTreeNew");
    }

    private CompoundTag selfEditPatch() {
        CompoundTag patch = new CompoundTag();
        for (String key : data.getAllKeys()) {
            if (!isProtectedSelfEditKey(key)) {
                patch.put(key, Objects.requireNonNull(data.get(key)).copy());
            }
        }
        return patch;
    }

    private static CompoundTag retainOnlyKeys(CompoundTag patch, Set<String> allowedKeys) {
        CompoundTag narrowed = new CompoundTag();
        for (String key : patch.getAllKeys()) {
            if (allowedKeys.contains(key)) {
                narrowed.put(key, Objects.requireNonNull(patch.get(key)).copy());
            }
        }
        return narrowed;
    }

    private void sanitizeVisualIdentifiers(CompoundTag villagerData, CompoundTag fallback) {
        migrateLegacyHairStyle(villagerData);
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
            if (MCA.isBlankString(mcaData.getString("HairBase"))) {
                mcaData.putString("HairBase", hairStyle);
            }
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

    private void syncFamilyTree(ServerPlayer player, Entity entity, CompoundTag villagerData, boolean fullEditor) {
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

        if (!fullEditor) {
            return;
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
                Optional.ofNullable(entry.partner()).flatMap(tree::getOrEmpty).ifPresent(node -> node.updatePartner(null, null));
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
