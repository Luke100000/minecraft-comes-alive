package net.conczin.mca.server.world.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.datafix.McaDataFixers;
import net.conczin.mca.entity.PlayerDimensions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.ShowToastRequest;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.resources.API;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlayerSaveData extends SavedData implements EntityRelationship {
    private final ServerLevel world;
    private final UUID uuid;
    private final List<Letter> inbox = new LinkedList<>();
    private @Nullable Integer lastSeenVillageId;
    private boolean entityDataSet;
    private boolean overrideVillageRequirements = false;
    private String chatAIPrompt = "";
    private CompoundTag entityData;
    private PlayerDimensions.Scale dimensionsScale;
    private boolean unreadableSavedData;
    private String unreadableSavePath;

    PlayerSaveData(ServerLevel world, UUID uuid) {
        this.world = world;
        this.uuid = uuid;

        resetEntityData();
    }

    PlayerSaveData(ServerLevel world, UUID uuid, CompoundTag nbt) {
        this.world = world;
        this.uuid = uuid;

        lastSeenVillageId = nbt.getInt("lastSeenVillage").orElse(null);
        entityDataSet = nbt.contains("entityDataSet") && nbt.getBoolean("entityDataSet").orElse(false);
        overrideVillageRequirements = nbt.contains("overrideVillageRequirements") && nbt.getBoolean("overrideVillageRequirements").orElse(false);
        chatAIPrompt = nbt.getString("chatAIPrompt").orElse("");

        Optional<CompoundTag> storedEntityData = nbt.getCompound("entityData");
        if (storedEntityData.isPresent()) {
            CompoundTag stored = storedEntityData.get();
            entityData = normalizeEntityData(stored);
            if (!entityData.equals(stored)) {
                setDirty();
            }
        } else {
            resetEntityData();
        }

        ListTag inbox = nbt.getList("inbox").orElseGet(ListTag::new);
        this.inbox.addAll(NbtHelper.toList(inbox, e -> new Letter((CompoundTag) e, world.registryAccess())));
    }

    public static PlayerSaveData get(ServerPlayer player) {
        return get(player.level(), player.getUUID());
    }

    public static PlayerSaveData get(ServerLevel world, UUID uuid) {
        ServerLevel overworld = world.getServer().overworld();
        return loadSafely(overworld, uuid, overworld.getDataStorage(),
                WorldUtils.getSavedDataFolder(overworld.getDataStorage()));
    }

    static PlayerSaveData loadSafely(ServerLevel world, UUID uuid, SavedDataStorage storage, Path dataFolder) {
        ServerLevel overworld = world.getServer().overworld();
        String dataId = "mca_player_" + uuid;
        return WorldUtils.loadDataSafely(
                overworld,
                storage,
                dataFolder,
                dataId,
                ignored -> new PlayerSaveData(overworld, uuid),
                (nbt, provider) -> new PlayerSaveData(overworld, uuid, nbt),
                (data, path) -> data.markUnreadable(dataFolder, path),
                PlayerSaveData::migrateStoredMail
        );
    }

    public static String getRelativeSavePath(UUID uuid) {
        return relativeSavePath(MCA.locate("mca_player_" + uuid).withSuffix(".dat").resolveAgainst(Path.of("")));
    }

    private static String relativeSavePath(Path storageRelativePath) {
        return DimensionType.getStorageFolder(Level.OVERWORLD, Path.of(""))
                .resolve("data").resolve(storageRelativePath).normalize().toString().replace('\\', '/');
    }

    public String getRelativeSavePath() {
        return unreadableSavePath == null ? getRelativeSavePath(uuid) : unreadableSavePath;
    }

    private void markUnreadable(Path dataFolder, Path path) {
        unreadableSavedData = true;
        unreadableSavePath = relativeSavePath(dataFolder.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize()));
        setDirty(false);
        MCA.LOGGER.error("MCA player data {} could not be loaded. Original save will not be overwritten.", path);
    }

    public boolean hasUnreadableSavedData() {
        return unreadableSavedData;
    }

    public Set<UUID> getCallableFamilyIds() {
        FamilyTreeNode entry = getFamilyEntry();
        return Stream.concat(
                entry.getAllRelatives(4).filter(entry::isRelative),
                getPartnerUUID().stream()
        ).collect(Collectors.toUnmodifiableSet());
    }

    public static Optional<PlayerSaveData> getIfPresent(ServerLevel world, UUID uuid) {
        ServerLevel overworld = world.getServer().overworld();
        SavedDataStorage storage = overworld.getDataStorage();
        return loadIfPresentSafely(overworld, uuid, storage, WorldUtils.getSavedDataFolder(storage));
    }

    static Optional<PlayerSaveData> loadIfPresentSafely(ServerLevel world, UUID uuid, SavedDataStorage storage, Path dataFolder) {
        ServerLevel overworld = world.getServer().overworld();
        return WorldUtils.loadDataIfPresentSafely(overworld, storage, dataFolder, "mca_player_" + uuid,
                ignored -> null,
                (nbt, provider) -> new PlayerSaveData(overworld, uuid, nbt),
                (data, path) -> data.markUnreadable(dataFolder, path),
                PlayerSaveData::migrateStoredMail);
    }

    private static final int LEGACY_MAIL_DATA_VERSION = 3955; // Minecraft 1.21.1
    private static final int NATIVE_COMPONENT_DATA_VERSION = 4290; // Vanilla UnflattenTextComponentFix

    private static boolean migrateStoredMail(CompoundTag payload, @Nullable Tag storedVersion, boolean canonicalPath,
                                             HolderLookup.Provider registries) {
        Tag storedInbox = payload.get("inbox");
        if (storedInbox == null) return false;
        if (!(storedInbox instanceof ListTag inbox)) throw new IllegalArgumentException("Invalid player inbox list");
        if (inbox.isEmpty()) return false;

        int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        int version;
        if (storedVersion == null) {
            // The old writer used a flat filename; the native-component writer uses namespaced storage.
            version = canonicalPath ? currentVersion : LEGACY_MAIL_DATA_VERSION;
        } else if (storedVersion instanceof IntTag dataVersion && dataVersion.value() >= 0 && dataVersion.value() <= currentVersion) {
            version = dataVersion.value();
        } else {
            throw new IllegalArgumentException("Invalid player mail data version");
        }

        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag upgradedInbox = new ListTag();
        boolean migrated = false;
        for (Tag entry : inbox) {
            if (!(entry instanceof CompoundTag letter)) throw new IllegalArgumentException("Invalid player letter");
            Tag storedPages = letter.get("pages");
            if (storedPages == null) {
                upgradedInbox.add(letter);
                continue;
            }
            if (!(storedPages instanceof ListTag pages)) throw new IllegalArgumentException("Invalid player mail pages list");
            if (version == currentVersion || pages.isEmpty()) {
                // Validate without the normal reader's partial-result fallback; do not rewrite native literals.
                Letter.PAGES_CODEC.parse(ops, pages).getOrThrow();
                upgradedInbox.add(letter);
                continue;
            }

            List<Component> upgradedPages = new ArrayList<>(pages.size());
            for (Tag page : pages) {
                if (version < NATIVE_COMPONENT_DATA_VERSION) {
                    JsonElement json = parseLegacyMailPage(page);
                    unflattenLegacyHoverItemText(json);
                    page = StringTag.valueOf(json.toString());
                }
                Tag fixed = DataFixers.getDataFixer().update(References.TEXT_COMPONENT,
                        new Dynamic<>(NbtOps.INSTANCE, page), version, currentVersion).getValue();
                if (version < NATIVE_COMPONENT_DATA_VERSION && fixed.equals(page)) {
                    // DFU can return its input on failure. Never reinterpret old JSON as literal text.
                    throw new IllegalArgumentException("Legacy player mail component was not migrated");
                }
                fixed = fixed.copy();
                migrateTranslationArguments(fixed, Math.max(version, NATIVE_COMPONENT_DATA_VERSION), currentVersion);
                upgradedPages.add(ComponentSerialization.CODEC.parse(ops, fixed).getOrThrow());
            }
            CompoundTag upgradedLetter = letter.copy();
            upgradedLetter.put("pages", Letter.PAGES_CODEC.encodeStart(ops, upgradedPages).getOrThrow());
            upgradedInbox.add(upgradedLetter);
            migrated = true;
        }
        // Commit the replacement only when every letter has been decoded successfully.
        if (migrated) payload.put("inbox", upgradedInbox);
        return migrated;
    }

    private static void migrateTranslationArguments(@Nullable Tag component, int version, int currentVersion) {
        if (component instanceof ListTag components) {
            for (Tag child : components) {
                migrateTranslationArguments(child, version, currentVersion);
            }
        } else if (component instanceof CompoundTag compound) {
            // Vanilla's native text-component DFU schema visits extra, separator and hover text,
            // but leaves translatable arguments in the remainder. Fix those components separately.
            if (compound.get("translate") instanceof StringTag && compound.get("with") instanceof ListTag arguments) {
                for (int i = 0; i < arguments.size(); i++) {
                    Tag argument = arguments.get(i);
                    if (argument instanceof CompoundTag || argument instanceof ListTag) {
                        Tag fixed = DataFixers.getDataFixer().update(References.TEXT_COMPONENT,
                                new Dynamic<>(NbtOps.INSTANCE, argument), version, currentVersion).getValue();
                        migrateTranslationArguments(fixed, version, currentVersion);
                        arguments.set(i, fixed);
                    }
                }
            }
            migrateTranslationArguments(compound.get("extra"), version, currentVersion);
            migrateTranslationArguments(compound.get("separator"), version, currentVersion);
            if (compound.get("hover_event") instanceof CompoundTag hover) {
                switch (hover.getStringOr("action", "")) {
                    case "show_text" -> migrateTranslationArguments(hover.get("value"), version, currentVersion);
                    case "show_entity" -> migrateTranslationArguments(hover.get("name"), version, currentVersion);
                    case "show_item" -> migrateHoverItemTranslationArguments(hover, version, currentVersion);
                }
            }
        }
    }

    private static void migrateHoverItemTranslationArguments(@Nullable Tag item, int version, int currentVersion) {
        if (!(item instanceof CompoundTag stack) || !(stack.get("components") instanceof CompoundTag components)) return;
        migrateTranslationArguments(components.get("minecraft:custom_name"), version, currentVersion);
        migrateTranslationArguments(components.get("minecraft:item_name"), version, currentVersion);
        migrateTranslationArguments(components.get("minecraft:lore"), version, currentVersion);
        if (components.get("minecraft:written_book_content") instanceof CompoundTag book
                && book.get("pages") instanceof ListTag pages) {
            for (Tag page : pages) {
                if (page instanceof CompoundTag filtered && filtered.contains("raw")) {
                    migrateTranslationArguments(filtered.get("raw"), version, currentVersion);
                    migrateTranslationArguments(filtered.get("filtered"), version, currentVersion);
                } else {
                    migrateTranslationArguments(page, version, currentVersion);
                }
            }
        }
        for (String key : List.of("minecraft:bundle_contents", "minecraft:charged_projectiles")) {
            if (components.get(key) instanceof ListTag items) {
                for (Tag nested : items) migrateHoverItemTranslationArguments(nested, version, currentVersion);
            }
        }
        if (components.get("minecraft:container") instanceof ListTag slots) {
            for (Tag slot : slots) {
                if (slot instanceof CompoundTag entry) migrateHoverItemTranslationArguments(entry.get("item"), version, currentVersion);
            }
        }
        migrateHoverItemTranslationArguments(components.get("minecraft:use_remainder"), version, currentVersion);
    }

    private static void unflattenLegacyHoverItemText(@Nullable JsonElement component) {
        if (component == null) return;
        if (component.isJsonArray()) {
            component.getAsJsonArray().forEach(PlayerSaveData::unflattenLegacyHoverItemText);
        } else if (component.isJsonObject()) {
            JsonObject object = component.getAsJsonObject();
            unflattenLegacyHoverItemText(object.get("extra"));
            unflattenLegacyHoverItemText(object.get("separator"));
            if (object.has("translate")) unflattenLegacyHoverItemText(object.get("with"));
            if (object.get("hoverEvent") instanceof JsonObject hover && hover.has("action")) {
                JsonElement contents = hover.get("contents");
                switch (hover.get("action").getAsString()) {
                    case "show_text" -> unflattenLegacyHoverItemText(contents == null ? hover.get("value") : contents);
                    case "show_entity" -> {
                        if (contents instanceof JsonObject entity) unflattenLegacyHoverItemText(entity.get("name"));
                    }
                    case "show_item" -> unflattenLegacyHoverItemStackText(contents);
                }
            }
        }
    }

    private static void unflattenLegacyHoverItemStackText(@Nullable JsonElement item) {
        if (!(item instanceof JsonObject stack) || !(stack.get("components") instanceof JsonObject components)) return;
        // V3818_3 declares these native text and nested item-stack slots. The enclosing
        // legacy text is opaque to DFU until UnflattenTextComponentFix, so decode flat
        // strings before that transition, without interpreting arbitrary custom data.
        for (String key : List.of("minecraft:custom_name", "minecraft:item_name")) {
            if (components.has(key)) components.add(key, unflattenLegacyItemText(components.get(key)));
        }
        if (components.has("minecraft:lore")) {
            var lore = components.getAsJsonArray("minecraft:lore");
            for (int i = 0; i < lore.size(); i++) lore.set(i, unflattenLegacyItemText(lore.get(i)));
        }
        if (components.get("minecraft:written_book_content") instanceof JsonObject book && book.has("pages")) {
            var pages = book.getAsJsonArray("pages");
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i) instanceof JsonObject filtered) {
                    if (!filtered.has("raw")) throw new IllegalArgumentException("Missing legacy written book raw page");
                    filtered.add("raw", unflattenLegacyItemText(filtered.get("raw")));
                    if (filtered.has("filtered")) filtered.add("filtered", unflattenLegacyItemText(filtered.get("filtered")));
                } else {
                    pages.set(i, unflattenLegacyItemText(pages.get(i)));
                }
            }
        }
        for (String key : List.of("minecraft:bundle_contents", "minecraft:charged_projectiles")) {
            if (components.has(key)) components.getAsJsonArray(key).forEach(PlayerSaveData::unflattenLegacyHoverItemStackText);
        }
        if (components.has("minecraft:container")) {
            for (JsonElement slot : components.getAsJsonArray("minecraft:container")) {
                unflattenLegacyHoverItemStackText(slot.getAsJsonObject().get("item"));
            }
        }
        unflattenLegacyHoverItemStackText(components.get("minecraft:use_remainder"));
    }

    private static JsonElement unflattenLegacyItemText(JsonElement value) {
        if (!(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())) {
            throw new IllegalArgumentException("Expected legacy hover item text JSON string");
        }
        JsonElement component = parseLegacyMailPage(StringTag.valueOf(value.getAsString()));
        unflattenLegacyHoverItemText(component);
        return component;
    }

    private static JsonElement parseLegacyMailPage(Tag page) {
        if (!(page instanceof StringTag text)) throw new IllegalArgumentException("Expected legacy player mail JSON string");
        JsonElement json = StrictJsonParser.parse(text.value());
        if (!(json.isJsonObject() || (json.isJsonArray() && !json.getAsJsonArray().isEmpty())
                || (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()))) {
            throw new IllegalArgumentException("Invalid legacy player mail component JSON");
        }
        return json;
    }

    public static void showMailNotification(ServerPlayer player) {
        Network.sendToPlayer(new ShowToastRequest(
                "server.mail.title",
                "server.mail.description"
        ), player);
    }

    private void resetEntityData() {
        entityData = new CompoundTag();

        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.create(world, EntitySpawnReason.LOAD);
        assert villager != null;
        villager.initializeSkin(true);
        villager.getGenetics().randomize();
        villager.getTraits().randomize();
        villager.getVillagerBrain().randomize();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
        villager.saveWithoutId(output);
        entityData = output.buildResult();
    }

    public boolean isEntityDataSet() {
        return entityDataSet;
    }

    public void setEntityDataSet(boolean entityDataSet) {
        if (this.entityDataSet == entityDataSet) {
            return;
        }
        this.entityDataSet = entityDataSet;
        setDirty();
        refreshPlayerDimensions();
    }

    public CompoundTag getEntityData() {
        return entityData.copy();
    }

    public PlayerDimensions.Scale getDimensionsScale() {
        if (dimensionsScale == null) {
            dimensionsScale = PlayerDimensions.fromEntityData(entityData);
        }
        return dimensionsScale;
    }

    public void setEntityData(CompoundTag entityData) {
        CompoundTag copy = normalizeEntityData(entityData);
        if (copy.equals(this.entityData)) {
            return;
        }
        this.entityData = copy;
        dimensionsScale = PlayerDimensions.fromEntityData(copy);
        setDirty();
        refreshPlayerDimensions();
    }

    private static CompoundTag normalizeEntityData(CompoundTag entityData) {
        return McaDataFixers.update(entityData.copy());
    }

    @Nullable
    private ServerPlayer findOnlinePlayer() {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player;
        }
        for (ServerLevel level : world.getServer().getAllLevels()) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer levelPlayer) {
                return levelPlayer;
            }
        }
        return null;
    }

    private void refreshPlayerDimensions() {
        ServerPlayer player = findOnlinePlayer();
        if (player != null) {
            player.refreshDimensions();
        }
    }

    @Override
    public void onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity victim) {
        EntityRelationship.super.onTragedy(cause, burialSite, type, victim);

        // send letter of condolence
        if (victim instanceof VillagerEntityMCA victimVillager) {
            sendLetterOfCondolence(victimVillager.getName().getString(),
                    victimVillager.getResidency().getHomeVillage().map(Village::getName).orElse(API.getVillagePool().pickVillageName("village")));
        }
    }

    public void updateLastSeenVillage(VillageManager manager, ServerPlayer self) {
        Optional<Village> prevVillage = getLastSeenVillage(manager);
        Optional<Village> nextVillage = prevVillage
                .filter(v -> v.isWithinBorder(self))
                .or(() -> manager.findNearestVillage(self));

        setLastSeenVillage(self, prevVillage.orElse(null), nextVillage.orElse(null));

        // village rank advancement
        if (nextVillage.isPresent()) {
            Rank rank = Tasks.getRank(nextVillage.get(), self);
            CriterionMCA.RANK.trigger(self, rank);
        }
    }

    public void setLastSeenVillage(ServerPlayer self, Village oldVillage, @Nullable Village newVillage) {
        lastSeenVillageId = newVillage != null ? newVillage.getId() : null;
        setDirty();

        if (oldVillage != newVillage) {
            if (oldVillage != null) {
                onLeave(self, oldVillage);
            }
            if (newVillage != null) {
                onEnter(self, newVillage);
            }
        }
    }

    public Optional<Village> getLastSeenVillage(VillageManager manager) {
        return Optional.ofNullable(lastSeenVillageId).flatMap(manager::getOrEmpty);
    }

    public Optional<Integer> getLastSeenVillageId() {
        return Optional.ofNullable(lastSeenVillageId);
    }

    protected void onLeave(ServerPlayer self, Village village) {
        if (Config.getInstance().enterVillageNotification && village.isVillage()) {
            self.sendSystemMessage(Component.translatable("gui.village.left", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    protected void onEnter(ServerPlayer self, Village village) {
        if (Config.getInstance().enterVillageNotification && village.isVillage()) {
            self.sendSystemMessage(Component.translatable("gui.village.welcome", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
        village.onEnter((ServerLevel) self.level());
    }

    @Override
    public void marry(Entity spouse) {
        EntityRelationship.super.marry(spouse);
        setDirty();
    }

    @Override
    public void endRelationShip(RelationshipState newState) {
        EntityRelationship.super.endRelationShip(newState);
        setDirty();
    }

    @Override
    public ServerLevel getWorld() {
        return world;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public Gender getGender() {
        CompoundTag mcaData = NbtHelper.getCompoundOrSelf(entityData, VillagerEntityMCA.MCA_DATA_KEY);
        if (mcaData.contains("Gender")) {
            return Gender.byId(mcaData.getInt("Gender").orElse(Gender.UNASSIGNED.ordinal()));
        }
        if (entityData.contains("gender")) {
            return Gender.byId(entityData.getInt("gender").orElse(Gender.UNASSIGNED.ordinal()));
        }
        return Gender.UNASSIGNED;
    }

    @Override
    public @NotNull FamilyTreeNode getFamilyEntry() {
        return getFamilyTree().getOrEmpty(uuid).orElseGet(() -> {
            String name = Optional.ofNullable(findOnlinePlayer()).map(p -> p.getName().getString()).orElse("Unnamed Adventurer");
            return getFamilyTree().getOrCreate(uuid, name, getGender(), true);
        });
    }

    public void reset() {
        endRelationShip(RelationshipState.SINGLE);
        setDirty();
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty && !unreadableSavedData);
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        if (lastSeenVillageId != null) {
            nbt.putInt("lastSeenVillage", lastSeenVillageId);
        }
        CompoundTag storedEntityData = entityData.copy();
        McaDataFixers.stampCurrentVersion(storedEntityData);
        nbt.put("entityData", storedEntityData);
        nbt.putBoolean("entityDataSet", entityDataSet);
        nbt.putBoolean("overrideVillageRequirements", overrideVillageRequirements);
        nbt.putString("chatAIPrompt", chatAIPrompt);
        nbt.put("inbox", NbtHelper.fromList(inbox, v -> v.toTag(provider)));
        return nbt;
    }

    public String getChatAIPrompt() { return chatAIPrompt; }

    public void setChatAIPrompt(String chatAIPrompt) {
        this.chatAIPrompt = chatAIPrompt;
        setDirty();
    }

    public void sendMail(Letter pages) {
        if (Config.getInstance().enableVillagerMailingPlayers) {
            inbox.add(pages);
        }
        setDirty();
    }

    public boolean hasMail() {
        return !inbox.isEmpty();
    }

    public ItemStack getMail() {
        if (hasMail()) {
            Letter letter = inbox.removeFirst();
            ItemStack stack = new ItemStack(ItemsMCA.LETTER, 1);
            stack.set(DataComponentsMCA.BOOK_PAGES, letter.pages());
            return stack;
        } else {
            return null;
        }
    }

    public void sendLetterOfCondolence(String name, String village) {
        sendLetter(Component.translatable("mca.letter.condolence", getFamilyEntry().getName(), name, village));
    }

    public void sendLetter(Component... lines) {
        sendMail(new Letter("", Arrays.asList(lines)));
        ServerPlayer player = findOnlinePlayer();
        if (player != null) {
            showMailNotification(player);
        }
    }

    public record Letter(String title, List<Component> pages) {
        private static final Codec<List<Component>> PAGES_CODEC = ComponentSerialization.CODEC.listOf();

        public Letter(CompoundTag nbt, HolderLookup.Provider registries) {
            this(
                    nbt.getString("title").orElse(""),
                    nbt.read("pages", PAGES_CODEC, registries.createSerializationContext(NbtOps.INSTANCE))
                            .orElse(List.of())
            );
        }

        CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("title", title);

            DynamicOps<Tag> dynamicOps = registries.createSerializationContext(NbtOps.INSTANCE);
            nbt.store("pages", PAGES_CODEC, dynamicOps, pages);

            return nbt;
        }
    }

    public boolean isOverrideVillageRequirements() {
        return overrideVillageRequirements;
    }

    public void setOverrideVillageRequirements(boolean overrideVillageRequirements) {
        if (this.overrideVillageRequirements != overrideVillageRequirements) {
            this.overrideVillageRequirements = overrideVillageRequirements;
            setDirty();
        }
    }
}
