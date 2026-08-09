package net.mca.network.c2s;

import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.relationship.Gender;
import net.mca.network.NbtDataMessage;
import net.mca.network.s2c.GetVillagerResponse;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.resources.SkinVisualIds;
import net.mca.resources.data.skin.LayeredHair;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.util.NbtHelper;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class VillagerEditorSyncRequest extends NbtDataMessage implements Message {
    @Serial
    private static final long serialVersionUID = -5581564927127176555L;

    private final String command;
    private final UUID uuid;

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
    public VillagerEditorSyncRequest(String command, UUID uuid, NbtCompound data) {
        super(data.copy());
        this.command = command;
        this.uuid = uuid;
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
        if (key.startsWith("Gene") || key.startsWith("gene_")) {
            return true;
        }
        if (key.equals(Genetics.GENDER_KEY) || key.equals("gender")
                || key.equals("Personality") || key.equals("personality")
                || key.equals("Traits") || key.equals("traits")
                || key.equals("AgeState") || key.equals("ageState")
                || key.equals("Hair") || key.equals("hair")
                || key.equals("Clothes") || key.equals("clothes")) {
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
        if (key.equals("InfectionProgress") || key.equals("infectionProgress")
                || key.equals("Mood") || key.equals("mood")
                || key.equals("Memories") || key.equals("memories")) {
            return true;
        }
        return false;
    }

    public static NbtCompound createEditorPatch(NbtCompound sourceNbt) {
        NbtCompound patch = new NbtCompound();
        for (String key : sourceNbt.getKeys()) {
            if (isAllowedTopLevelKey(key)) {
                patch.put(key, Objects.requireNonNull(sourceNbt.get(key)).copy());
            }
        }

        NbtCompound mcaPatch = new NbtCompound();
        for (String key : sourceNbt.getKeys()) {
            if (isAllowedMcaKey(key)) {
                mcaPatch.put(key, Objects.requireNonNull(sourceNbt.get(key)).copy());
            }
        }

        if (sourceNbt.contains("MCAData", 10)) {
            NbtCompound sourceMca = sourceNbt.getCompound("MCAData");
            for (String key : sourceMca.getKeys()) {
                if (isAllowedMcaKey(key)) {
                    mcaPatch.put(key, Objects.requireNonNull(sourceMca.get(key)).copy());
                }
            }
        }

        if (!mcaPatch.isEmpty()) {
            patch.put("MCAData", mcaPatch);
        }
        return patch;
    }

    public static NbtCompound mergeAllowedEditorPatch(NbtCompound serverData, NbtCompound patch) {
        NbtCompound merged = serverData.copy();
        for (String key : patch.getKeys()) {
            if (isAllowedTopLevelKey(key)) {
                merged.put(key, Objects.requireNonNull(patch.get(key)).copy());
            }
        }

        if (patch.contains("MCAData", 10)) {
            NbtCompound patchMca = patch.getCompound("MCAData");
            NbtCompound mergedMca = NbtHelper.getOrCreateCompound(merged, "MCAData");
            for (String key : patchMca.getKeys()) {
                if (isAllowedMcaKey(key)) {
                    mergedMca.put(key, Objects.requireNonNull(patchMca.get(key)).copy());
                }
            }
            if (patchMca.contains(Genetics.GENDER_KEY)) {
                Genetics.writeGender(mergedMca, Genetics.readGender(patchMca));
            }
        }

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

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity entity = player.getServerWorld().getEntity(uuid);
        switch (command) {
            case "skin", "hair", "layered_hair", "hair_base", "hair_bangs", "hair_back", "hair_front", "hair_extra", "clothing", "gender", "sync" ->
                    saveEntity(player, entity, getData().copy());
            case "profession" -> {
                if (entity instanceof VillagerEntityMCA villager) {
                    Identifier professionId = Identifier.tryParse(getData().getString("profession"));
                    if (professionId == null) {
                        return;
                    }
                    VillagerProfession profession = Registries.VILLAGER_PROFESSION.get(professionId);
                    if (profession != null) {
                        // Apply editor state (name, genetics, etc.) atomically with the profession change.
                        // This replaces the separate sync packet the client used to send first.
                        NbtCompound merged = applyVillagerPatch(player, entity, getData().copy());
                        villager.setProfession(profession);
                        NbtCompound fresh = GetVillagerRequest.getVillagerData(entity);
                        NetworkHandler.sendToPlayer(new GetVillagerResponse(fresh != null ? fresh : merged), player);
                    }
                }
            }
        }
    }

    private void saveEntity(ServerPlayerEntity player, Entity entity, NbtCompound patch) {
        if (entity == null) {
            return;
        }

        if (entity instanceof ServerPlayerEntity serverPlayer) {
            NbtCompound serverData = PlayerSaveData.get(serverPlayer).getEntityData();
            NbtCompound merged = mergeAllowedEditorPatch(serverData, patch);
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            data.setEntityData(merged);
            data.setEntityDataSet(true);
            syncFamilyTree(player, entity, merged);
            serverPlayer.getServerWorld().getPlayers().forEach(p -> NetworkHandler.sendToPlayer(new PlayerDataMessage(serverPlayer.getUuid(), merged), p));
            NetworkHandler.sendToPlayer(new GetVillagerResponse(merged), player);
            return;
        }

        NbtCompound merged = applyVillagerPatch(player, entity, patch);
        if (merged == null) {
            return;
        }
        NbtCompound fresh = GetVillagerRequest.getVillagerData(entity);
        NetworkHandler.sendToPlayer(new GetVillagerResponse(fresh != null ? fresh : merged), player);
    }

    /**
     * Merges {@code patch} into the entity's current server NBT, sanitizes visual identifiers,
     * writes the result back via {@link VillagerLike#syncFromEditor}, and handles
     * family-tree and village-residency side-effects.
     *
     * @return the merged compound, or {@code null} if {@code entity} is not a {@link VillagerLike}
     *         or its server data is unavailable.
     */
    private NbtCompound applyVillagerPatch(ServerPlayerEntity player, Entity entity, NbtCompound patch) {
        if (!(entity instanceof VillagerLike<?> villagerLike)) {
            return null;
        }
        NbtCompound serverData = GetVillagerRequest.getVillagerData(entity);
        if (serverData == null) {
            return null;
        }
        NbtCompound merged = mergeAllowedEditorPatch(serverData, patch);
        sanitizeVisualIdentifiers(entity, merged);
        villagerLike.syncFromEditor(merged);
        entity.calculateDimensions();
        syncFamilyTree(player, entity, merged);
        if (entity instanceof VillagerEntityMCA villager) {
            villager.getResidency().getHomeVillage().ifPresent(b -> b.updateResident(villager));
        }
        return merged;
    }

    private void sanitizeVisualIdentifiers(Entity entity, NbtCompound villagerData) {
        NbtCompound mcaData = normalizeVisualData(villagerData);
        migrateLegacyHairStyle(mcaData);
        NbtCompound fallbackData = GetVillagerRequest.getVillagerData(entity);
        NbtCompound fallbackMcaData = fallbackData == null ? new NbtCompound() : getMcaData(fallbackData);
        Gender gender = getGender(villagerData);
        clearInvalidIdentifier(mcaData, fallbackMcaData, "Skin", identifier -> SkinVisualIds.isBodySkin(identifier, gender));
        clearInvalidIdentifier(mcaData, fallbackMcaData, "Clothes", identifier -> SkinVisualIds.isClothing(identifier, gender));
        clearInvalidIdentifier(mcaData, fallbackMcaData, "Hair", identifier -> SkinVisualIds.isHairStyle(identifier, gender));
        clearInvalidIdentifier(mcaData, fallbackMcaData, "HairStyle", identifier -> SkinVisualIds.isHairStyle(identifier, gender));
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            clearInvalidIdentifier(mcaData, fallbackMcaData, category.getDataKey(), identifier -> SkinVisualIds.isHairLayer(identifier, category, gender));
        }
    }

    private void migrateLegacyHairStyle(NbtCompound mcaData) {
        String hairStyle = mcaData.getString("HairStyle");
        if (SkinVisualIds.isLegacyHairTexture(hairStyle)) {
            mcaData.putString("HairBase", hairStyle);
            mcaData.putString("HairStyle", "");
            mcaData.putString("Hair", "");
        }
    }

    private NbtCompound normalizeVisualData(NbtCompound villagerData) {
        NbtCompound source = getOrCreateMcaData(villagerData);
        NbtCompound sanitized = new NbtCompound();
        for (String key : MCA_VISUAL_KEYS) {
            if (!source.contains(key) && villagerData.contains(key)) {
                source.put(key, Objects.requireNonNull(villagerData.get(key)).copy());
            }
            villagerData.remove(key);
        }
        for (String key : source.getKeys()) {
            if (isAllowedMcaKey(key)) {
                sanitized.put(key, Objects.requireNonNull(source.get(key)).copy());
            }
        }
        villagerData.put(VillagerEntityMCA.MCA_DATA_KEY, sanitized);
        return sanitized;
    }

    private void clearInvalidIdentifier(NbtCompound mcaData, NbtCompound fallbackMcaData, String key, Predicate<String> validator) {
        String identifier = mcaData.getString(key);
        if (!MCA.isBlankString(identifier) && !validator.test(identifier)) {
            MCA.LOGGER.warn("Ignoring unknown villager editor visual identifier {}={}", key, identifier);
            String fallback = fallbackMcaData.getString(key);
            mcaData.putString(key, !MCA.isBlankString(fallback) && validator.test(fallback) ? fallback : "");
        }
    }

    private Gender getGender(NbtCompound villagerData) {
        NbtCompound mcaData = getMcaData(villagerData);
        Gender gender = Genetics.readGender(mcaData);
        if (gender != Gender.UNASSIGNED || mcaData == villagerData) {
            return gender;
        }
        return Genetics.readGender(villagerData);
    }

    private NbtCompound getMcaData(NbtCompound villagerData) {
        return NbtHelper.getCompoundOrSelf(villagerData, VillagerEntityMCA.MCA_DATA_KEY);
    }

    private NbtCompound getOrCreateMcaData(NbtCompound villagerData) {
        boolean hadMcaData = villagerData.contains(VillagerEntityMCA.MCA_DATA_KEY, 10);
        NbtCompound mcaData = NbtHelper.getOrCreateCompound(villagerData, VillagerEntityMCA.MCA_DATA_KEY);
        if (!hadMcaData) {
            for (String key : MCA_VISUAL_KEYS) {
                if (villagerData.contains(key)) {
                    mcaData.put(key, Objects.requireNonNull(villagerData.get(key)).copy());
                }
            }
        }
        return mcaData;
    }

    private Optional<FamilyTreeNode> getFamilyNode(ServerPlayerEntity player, FamilyTree tree, String name, Gender gender) {
        try {
            UUID uuid = UUID.fromString(name);
            Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
            if (node.isPresent()) {
                player.sendMessage(Text.translatable("gui.villager_editor.uuid_known", name, node.get().getName()), false);
                return node;
            } else {
                player.sendMessage(Text.translatable("gui.villager_editor.uuid_unknown", name).formatted(Formatting.RED), false);
                return Optional.empty();
            }
        } catch (IllegalArgumentException exception) {
            List<FamilyTreeNode> nodes = tree.getAllWithName(name).toList();
            if (nodes.isEmpty()) {
                player.sendMessage(Text.translatable("gui.villager_editor.name_created", name).formatted(Formatting.YELLOW), false);
                return Optional.of(tree.getOrCreate(UUID.randomUUID(), name, gender));
            } else {
                if (nodes.size() > 1) {
                    player.sendMessage(Text.translatable("gui.villager_editor.name_not_unique", name).formatted(Formatting.RED), false);
                    String uuids = nodes.stream().map(FamilyTreeNode::id).map(UUID::toString).collect(Collectors.joining(", "));
                    player.sendMessage(Text.translatable("gui.villager_editor.list_of_ids", uuids), false);
                } else {
                    player.sendMessage(Text.translatable("gui.villager_editor.name_unique", name), false);
                }

                return Optional.ofNullable(nodes.get(0));
            }
        }
    }

    private void syncFamilyTree(ServerPlayerEntity player, Entity entity, NbtCompound villagerData) {
        FamilyTree tree = FamilyTree.get((ServerWorld) entity.getWorld());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(getGender(villagerData));

        if (villagerData.contains("CustomName", 8)) {
            String serializedName = villagerData.getString("CustomName");
            if (!serializedName.isEmpty()) {
                try {
                    Text name = Text.Serializer.fromJson(serializedName);
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

}
