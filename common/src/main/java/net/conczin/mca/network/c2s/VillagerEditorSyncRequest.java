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
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

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
        for (String key : sourceNbt.keySet()) {
            if (isAllowedTopLevelKey(key)) {
                patch.put(key, Objects.requireNonNull(sourceNbt.get(key)).copy());
            }
        }

        CompoundTag mcaPatch = new CompoundTag();
        for (String key : sourceNbt.keySet()) {
            if (isAllowedMcaKey(key)) {
                mcaPatch.put(key, Objects.requireNonNull(sourceNbt.get(key)).copy());
            }
        }

        sourceNbt.getCompound("MCAData").ifPresent(sourceMca -> {
            for (String key : sourceMca.keySet()) {
                if (isAllowedMcaKey(key)) {
                    mcaPatch.put(key, Objects.requireNonNull(sourceMca.get(key)).copy());
                }
            }
        });

        if (!mcaPatch.isEmpty()) {
            patch.put("MCAData", mcaPatch);
        }
        return patch;
    }

    public static CompoundTag mergeAllowedEditorPatch(CompoundTag serverData, CompoundTag patch) {
        CompoundTag merged = serverData.copy();
        for (String key : patch.keySet()) {
            if (isAllowedTopLevelKey(key)) {
                merged.put(key, Objects.requireNonNull(patch.get(key)).copy());
            }
        }

        patch.getCompound("MCAData").ifPresent(patchMca -> {
            CompoundTag mergedMca = NbtHelper.getOrCreateCompound(merged, "MCAData");
            for (String key : patchMca.keySet()) {
                if (isAllowedMcaKey(key)) {
                    mergedMca.put(key, Objects.requireNonNull(patchMca.get(key)).copy());
                }
            }
        });

        // Defensive preservation:
        String[] preservedKeys = {"Offers", "Gossips", "Inventory", "VillagerXp", "UUID", "UUIDMost", "UUIDLeast"};
        for (String key : preservedKeys) {
            if (serverData.contains(key)) {
                merged.put(key, Objects.requireNonNull(serverData.get(key)).copy());
            } else {
                merged.remove(key);
            }
        }
        return merged;
    }

    public CompoundTag data() {
        return data.copy();
    }

    @Override
    public void handleServer(ServerPlayer sender) {
        Entity target = sender.level().getEntity(uuid);
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.level() != sender.level()) {
            return;
        }
        if (!(target instanceof ServerPlayer) && !(target instanceof VillagerLike<?>)) {
            return;
        }
        boolean professionEdit = command.equals("profession");
        if (!professionEdit && !isStandardEditorCommand(command)) {
            return;
        }
        if (!isValidPatch(professionEdit)) {
            return;
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
            Identifier id = data.getString("profession").map(Identifier::tryParse).orElse(null);
            if (id == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) {
                return;
            }
            profession = BuiltInRegistries.VILLAGER_PROFESSION.getValue(id);
        }

        CompoundTag merged = mergeAllowedEditorPatch(serverData, approved.patch());
        sanitizeVisualIdentifiers(merged, serverData);
        if (target instanceof ServerPlayer targetPlayer) {
            playerData.setEntityData(merged);
            playerData.setEntityDataSet(true);
            syncFamilyTree(sender, targetPlayer, merged, approved.fullEditor());
            targetPlayer.level().players().forEach(player ->
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

    private boolean isValidPatch(boolean professionEdit) {
        for (String key : data.keySet()) {
            if (key.equals("MCAData")) {
                Optional<CompoundTag> nested = data.getCompound(key);
                if (nested.isEmpty() || nested.get().keySet().stream().anyMatch(k -> !isAllowedMcaKey(k))) {
                    return false;
                }
            } else if (!isAllowedTopLevelKey(key) && !(professionEdit && key.equals("profession"))) {
                return false;
            }
        }
        return true;
    }

    private static CompoundTag editorFields(CompoundTag patch) {
        CompoundTag fields = patch.copy();
        fields.remove("MCAData");
        patch.getCompound("MCAData").ifPresent(nested -> {
            for (String key : nested.keySet()) {
                fields.put(key, Objects.requireNonNull(nested.get(key)).copy());
            }
        });
        return fields;
    }

    private ApprovedEdit approveEdit(ServerPlayer sender, Entity target, CompoundTag serverData, boolean professionEdit) {
        if (sender.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || (target instanceof VillagerLike<?> && sender.isHolding(ItemsMCA.VILLAGER_EDITOR))) {
            return !professionEdit || target instanceof VillagerEntityMCA ? new ApprovedEdit(data, true) : null;
        }
        if (professionEdit) {
            return null;
        }

        boolean self = target == sender;
        Config config = Config.getInstance();
        CompoundTag serverFields = editorFields(createEditorPatch(serverData));
        CompoundTag patchFields = editorFields(data);
        if (self && config.allowFullPlayerEditor
                && arePlayerTraitsValid(serverFields, patchFields) && isPersonalityValid(serverFields, patchFields)) {
            return new ApprovedEdit(selfEditPatch(), false);
        }
        if (self && ServerInteractionManager.getInstance().hasActiveDestinySession(sender)
                && isDestinyPatchPermitted(serverFields, patchFields)) {
            return new ApprovedEdit(selfEditPatch(), false);
        }
        boolean canUseTool = self || (target instanceof VillagerLike<?>
                && sender.distanceToSqr(target) <= InteractionVillagerMessage.MAX_INTERACTION_DISTANCE_SQR);
        if (canUseTool && sender.isHolding(ItemsMCA.COMB) && isCombCommand(command)
                && allChangedKeysIn(serverFields, patchFields, HAIR_KEYS)) {
            return new ApprovedEdit(retainOnlyKeys(data, HAIR_KEYS), false);
        }
        if (canUseTool && sender.isHolding(ItemsMCA.NEEDLE_AND_THREAD) && isNeedleCommand(command)
                && allChangedKeysIn(serverFields, patchFields, CLOTHING_KEYS)) {
            return new ApprovedEdit(retainOnlyKeys(data, CLOTHING_KEYS), false);
        }
        if (self && config.allowLimitedPlayerEditor && command.equals("sync")
                && allChangedKeysIn(serverFields, patchFields, LIMITED_PLAYER_KEYS)) {
            return new ApprovedEdit(retainOnlyKeys(data, LIMITED_PLAYER_KEYS), false);
        }
        return null;
    }

    private boolean isDestinyPatchPermitted(CompoundTag serverData, CompoundTag patchFields) {
        Config config = Config.getInstance();
        for (String key : patchFields.keySet()) {
            if (isProtectedSelfEditKey(key) || Objects.equals(serverData.get(key), patchFields.get(key))) {
                continue;
            }
            if (isDestinyIdentityOrVisualKey(key)) {
                continue;
            }
            if (key.equals("Personality")) {
                if (!isPersonalityValid(serverData, patchFields)) {
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
                if (!config.allowTraitCustomizationInDestiny || !arePlayerTraitsValid(serverData, patchFields)) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean allChangedKeysIn(CompoundTag serverData, CompoundTag patchFields, Set<String> allowedChangedKeys) {
        for (String key : patchFields.keySet()) {
            if (key.startsWith("FamilyTreeNew")) {
                return false;
            }
            if (PASSIVE_ECHOED_KEYS.contains(key)) {
                continue;
            }
            if (!Objects.equals(serverData.get(key), patchFields.get(key)) && !allowedChangedKeys.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPersonalityValid(CompoundTag serverData, CompoundTag patch) {
        if (!patch.contains("Personality") || Objects.equals(serverData.get("Personality"), patch.get("Personality"))) {
            return true;
        }
        return patch.getString("Personality").flatMap(Personality::get).isPresent();
    }

    private static boolean arePlayerTraitsValid(CompoundTag serverData, CompoundTag patch) {
        if (!patch.contains("Traits") || Objects.equals(serverData.get("Traits"), patch.get("Traits"))) {
            return true;
        }
        if (patch.getCompound("Traits").isEmpty()) {
            return false;
        }
        CompoundTag previousTraits = serverData.getCompound("Traits").orElseGet(CompoundTag::new);
        CompoundTag nextTraits = patch.getCompound("Traits").orElseThrow();
        Config config = Config.getInstance();
        for (String traitId : nextTraits.keySet()) {
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
        return filterPatch(data, key -> !isProtectedSelfEditKey(key));
    }

    private static CompoundTag retainOnlyKeys(CompoundTag patch, Set<String> allowedKeys) {
        return filterPatch(patch, allowedKeys::contains);
    }

    private static CompoundTag filterPatch(CompoundTag patch, Predicate<String> allowed) {
        CompoundTag narrowed = new CompoundTag();
        for (String key : patch.keySet()) {
            if (!key.equals("MCAData") && allowed.test(key)) {
                narrowed.put(key, Objects.requireNonNull(patch.get(key)).copy());
            }
        }
        patch.getCompound("MCAData").ifPresent(nested -> {
            CompoundTag narrowedMca = new CompoundTag();
            for (String key : nested.keySet()) {
                if (allowed.test(key)) {
                    narrowedMca.put(key, Objects.requireNonNull(nested.get(key)).copy());
                }
            }
            if (!narrowedMca.isEmpty()) {
                narrowed.put("MCAData", narrowedMca);
            }
        });
        return narrowed;
    }

    private void sanitizeVisualIdentifiers(CompoundTag villagerData, CompoundTag fallback) {
        CompoundTag mcaData = normalizeVisualData(villagerData);
        migrateLegacyHairStyle(mcaData);
        CompoundTag fallbackMcaData = getMcaData(fallback);
        // Gender affects random appearance selection, not explicit editor choices.
        clearInvalidIdentifier(mcaData, fallbackMcaData, "Skin", SkinVisualIds::isBodySkin);
        clearInvalidIdentifier(mcaData, fallbackMcaData, "Clothes", SkinVisualIds::isClothing);
        clearInvalidIdentifier(mcaData, fallbackMcaData, "Hair", SkinVisualIds::isHairStyle);
        clearInvalidIdentifier(mcaData, fallbackMcaData, "HairStyle", SkinVisualIds::isHairStyle);
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            clearInvalidIdentifier(mcaData, fallbackMcaData, category.getDataKey(), identifier -> SkinVisualIds.isHairLayer(identifier, category, Gender.UNASSIGNED));
        }
    }

    private void migrateLegacyHairStyle(CompoundTag mcaData) {
        String hairStyle = mcaData.getString("HairStyle").orElse("");
        if (SkinVisualIds.isLegacyHairTexture(hairStyle)) {
            if (MCA.isBlankString(mcaData.getString("HairBase").orElse(""))) {
                mcaData.putString("HairBase", hairStyle);
            }
            mcaData.putString("HairStyle", "");
            mcaData.putString("Hair", "");
        }
    }

    private CompoundTag normalizeVisualData(CompoundTag villagerData) {
        CompoundTag mcaData = getOrCreateMcaData(villagerData);
        for (String key : MCA_VISUAL_KEYS) {
            if (!mcaData.contains(key) && villagerData.contains(key)) {
                mcaData.put(key, Objects.requireNonNull(villagerData.get(key)).copy());
            }
            villagerData.remove(key);
        }
        return mcaData;
    }

    private void clearInvalidIdentifier(CompoundTag mcaData, CompoundTag fallbackMcaData, String key, Predicate<String> validator) {
        String identifier = mcaData.getString(key).orElse("");
        if (!MCA.isBlankString(identifier) && !validator.test(identifier)) {
            MCA.LOGGER.warn("Ignoring unknown villager editor visual identifier {}={}", key, identifier);
            String fallback = fallbackMcaData.getString(key).orElse("");
            mcaData.putString(key, !MCA.isBlankString(fallback) && validator.test(fallback) ? fallback : "");
        }
    }

    private Gender getGender(CompoundTag villagerData) {
        CompoundTag mcaData = getMcaData(villagerData);
        if (mcaData.contains("Gender")) {
            return Gender.byId(mcaData.getInt("Gender").orElse(0));
        }
        if (villagerData.contains("Gender")) {
            return Gender.byId(villagerData.getInt("Gender").orElse(0));
        }
        return Gender.UNASSIGNED;
    }

    private CompoundTag getMcaData(CompoundTag villagerData) {
        return NbtHelper.getCompoundOrSelf(villagerData, VillagerEntityMCA.MCA_DATA_KEY);
    }

    private CompoundTag getOrCreateMcaData(CompoundTag villagerData) {
        boolean hadMcaData = villagerData.getCompound(VillagerEntityMCA.MCA_DATA_KEY).isPresent();
        CompoundTag mcaData = NbtHelper.getOrCreateCompound(villagerData, VillagerEntityMCA.MCA_DATA_KEY);
        if (!hadMcaData) {
            for (String key : MCA_VISUAL_KEYS) {
                if (villagerData.contains(key)) {
                    mcaData.put(key, Objects.requireNonNull(villagerData.get(key)).copy());
                }
            }
        }
        return mcaData;
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

    private void syncFamilyTree(ServerPlayer player, Entity entity, CompoundTag villagerData, boolean fullEditor) {
        FamilyTree tree = FamilyTree.get((ServerLevel) entity.level());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(getGender(villagerData));

        VillagerLike.parseCustomName(entity.registryAccess(), villagerData)
                .map(Component::getString)
                .ifPresent(entry::setName);

        if (!fullEditor) {
            return;
        }

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
