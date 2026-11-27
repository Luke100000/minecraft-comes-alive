package net.conczin.mca.server.world.data;

import com.mojang.serialization.Codec;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlayerSaveData extends SavedData implements EntityRelationship {
    private final ServerLevel world;
    private final UUID uuid;
    private final List<Letter> inbox = new LinkedList<>();
    private Optional<Integer> lastSeenVillage = Optional.empty();
    private boolean entityDataSet;
    private boolean overrideVillageRequirements;
    private String chatAIPrompt = "";
    private CompoundTag entityData;
    private PlayerDimensions.Scale dimensionsScale;
    private boolean unreadableSavedData;

    PlayerSaveData(ServerLevel world, UUID uuid) {
        this.world = world;
        this.uuid = uuid;

        resetEntityData();
    }

    PlayerSaveData(ServerLevel world, UUID uuid, CompoundTag nbt) {
        this.world = world;
        this.uuid = uuid;

        lastSeenVillage = nbt.contains("lastSeenVillage", Tag.TAG_INT) ? Optional.of(nbt.getInt("lastSeenVillage")) : Optional.empty();
        entityDataSet = nbt.contains("entityDataSet") && nbt.getBoolean("entityDataSet");
        overrideVillageRequirements = nbt.contains("overrideVillageRequirements") && nbt.getBoolean("overrideVillageRequirements");
        chatAIPrompt = nbt.getString("chatAIPrompt");

        if (nbt.contains("entityData")) {
            CompoundTag storedEntityData = nbt.getCompound("entityData");
            entityData = normalizeEntityData(storedEntityData);
            if (!entityData.equals(storedEntityData)) {
                setDirty();
            }
        } else {
            resetEntityData();
        }

        ListTag inbox = nbt.getList("inbox", Tag.TAG_COMPOUND);
        this.inbox.addAll(NbtHelper.toList(inbox, e -> new Letter((CompoundTag) e, world.registryAccess())));
    }

    public static PlayerSaveData get(ServerPlayer player) {
        return get((ServerLevel) player.level(), player.getUUID());
    }

    public static PlayerSaveData get(ServerLevel world, UUID uuid) {
        ServerLevel overworld = world.getServer().overworld();
        return loadSafely(overworld, uuid, overworld.getDataStorage(),
                overworld.getServer().getWorldPath(LevelResource.ROOT).resolve("data"));
    }

    static PlayerSaveData loadSafely(ServerLevel world, UUID uuid, DimensionDataStorage storage, Path dataFolder) {
        ServerLevel overworld = world.getServer().overworld();
        String dataId = "mca_player_" + uuid;
        return WorldUtils.loadDataSafely(
                storage,
                dataFolder,
                dataId,
                () -> new PlayerSaveData(overworld, uuid),
                (nbt, provider) -> new PlayerSaveData(overworld, uuid, nbt),
                data -> {
                    data.unreadableSavedData = true;
                    MCA.LOGGER.error("MCA player data {} could not be loaded. Original save will not be overwritten.", uuid);
                }
        );
    }

    public static String getRelativeSavePath(UUID uuid) {
        return "data/mca_player_" + uuid + ".dat";
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

    @SuppressWarnings("DataFlowIssue")
    public static Optional<PlayerSaveData> getIfPresent(ServerLevel world, UUID uuid) {
        ServerLevel overworld = world.getServer().overworld();
        return Optional.ofNullable(overworld.getDataStorage().get(new SavedData.Factory<>(
                () -> null,
                (nbt, provider) -> new PlayerSaveData(overworld, uuid, nbt),
                null
        ), "mca_player_" + uuid));
    }

    public static void showMailNotification(ServerPlayer player) {
        Network.sendToPlayer(new ShowToastRequest(
                "server.mail.title",
                "server.mail.description"
        ), player);
    }

    private void resetEntityData() {
        entityData = new CompoundTag();

        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.create(world);
        assert villager != null;
        villager.initializeSkin(true);
        villager.getGenetics().randomize();
        villager.getTraits().randomize();
        villager.getVillagerBrain().randomize();
        villager.addAdditionalSaveData(entityData);
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

    public String getChatAIPrompt() {
        return chatAIPrompt;
    }

    public void setChatAIPrompt(String chatAIPrompt) {
        this.chatAIPrompt = chatAIPrompt;
        setDirty();
    }

    public PlayerDimensions.Scale getDimensionsScale() {
        if (dimensionsScale == null) {
            dimensionsScale = PlayerDimensions.fromPlayerData(this);
        }
        return dimensionsScale;
    }

    public void setEntityData(CompoundTag entityData) {
        CompoundTag copy = normalizeEntityData(entityData);
        if (copy.equals(this.entityData)) {
            return;
        }
        this.entityData = copy;
        dimensionsScale = PlayerDimensions.fromPlayerData(this);
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
            PlayerDimensions.debugRefresh(player, "before server player data refresh");
            player.refreshDimensions();
            PlayerDimensions.debugRefresh(player, "after server player data refresh");
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
        lastSeenVillage = Optional.ofNullable(newVillage).map(Village::getId);
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
        return lastSeenVillage.flatMap(manager::getOrEmpty);
    }

    public Optional<Integer> getLastSeenVillageId() {
        return lastSeenVillage;
    }

    protected void onLeave(Player self, Village village) {
        if (Config.SERVER.enterVillageNotification.get() && village.isVillage()) {
            self.displayClientMessage(Component.translatable("gui.village.left", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    protected void onEnter(Player self, Village village) {
        if (Config.SERVER.enterVillageNotification.get() && village.isVillage()) {
            self.displayClientMessage(Component.translatable("gui.village.welcome", village.getName()).withStyle(ChatFormatting.GOLD), true);
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
        if (entityData.contains("Gender")) {
            return Gender.byId(entityData.getInt("Gender"));
        }
        if (entityData.contains("gender")) {
            return Gender.byId(entityData.getInt("gender"));
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
    public void save(File file, HolderLookup.Provider provider) {
        if (unreadableSavedData) {
            if (isDirty()) {
                MCA.LOGGER.error("Refusing to overwrite unreadable MCA player data at {}", file);
                setDirty(false);
            }
            return;
        }
        super.save(file, provider);
    }
    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        lastSeenVillage.ifPresent(id -> nbt.putInt("lastSeenVillage", id));
        McaDataFixers.stampCurrentVersion(entityData);
        nbt.put("entityData", entityData);
        nbt.putBoolean("entityDataSet", entityDataSet);
        nbt.putBoolean("overrideVillageRequirements", overrideVillageRequirements);
        nbt.putString("chatAIPrompt", chatAIPrompt);
        nbt.put("inbox", NbtHelper.fromList(inbox, v -> v.toTag(provider)));
        return nbt;
    }

    public void sendMail(Letter pages) {
        if (Config.SERVER.enableVillagerMailingPlayers.get()) {
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
        private static final Codec<List<Component>> PAGES_CODEC = ComponentSerialization.FLAT_CODEC.listOf();

        public Letter(CompoundTag nbt, HolderLookup.Provider registries) {
            this(
                    nbt.getString("title"),
                    Optional.ofNullable(nbt.get("pages"))
                            .flatMap(tag -> PAGES_CODEC
                                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                                    .resultOrPartial(MCA.LOGGER::error))
                            .orElse(List.of())
            );
        }

        CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("title", title);

            DynamicOps<Tag> dynamicOps = registries.createSerializationContext(NbtOps.INSTANCE);
            ComponentSerialization.FLAT_CODEC.listOf()
                    .encodeStart(dynamicOps, pages).
                    resultOrPartial(MCA.LOGGER::error)
                    .ifPresent(tag -> nbt.put("pages", tag));

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
