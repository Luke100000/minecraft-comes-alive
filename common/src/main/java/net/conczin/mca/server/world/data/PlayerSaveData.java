package net.conczin.mca.server.world.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.conczin.mca.Config;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerSaveData extends SavedData implements EntityRelationship {
    private ServerLevel world;
    private final UUID uuid;
    private final List<Letter> inbox = new LinkedList<>();
    private @Nullable Integer lastSeenVillageId;
    private boolean entityDataSet;
    private boolean overrideVillageRequirements = false;
    private String chatAIPrompt = "";
    private CompoundTag entityData;
    private PlayerDimensions.Scale dimensionsScale;

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

        entityData = nbt.getCompound("entityData").orElseGet(() -> {
            resetEntityData();
            return entityData;
        });

        ListTag inbox = nbt.getList("inbox").orElseGet(ListTag::new);
        this.inbox.addAll(NbtHelper.toList(inbox, e -> new Letter((CompoundTag) e, world.registryAccess())));
    }

    public static PlayerSaveData get(ServerPlayer player) {
        PlayerSaveData data = get(player.level(), player.getUUID());
        data.world = player.level();
        return data;
    }

    public static PlayerSaveData get(ServerLevel world, UUID uuid) {
        return WorldUtils.loadData(world.getServer().overworld(), (nbt, provider) -> new PlayerSaveData(world, uuid, nbt), w -> new PlayerSaveData(world, uuid), "mca_player_" + uuid);
    }

    public static Optional<PlayerSaveData> getIfPresent(ServerLevel world, UUID uuid) {
        return WorldUtils.loadDataIfPresent(
                world.getServer().overworld(),
                (nbt, provider) -> new PlayerSaveData(world, uuid, nbt),
                "mca_player_" + uuid
        );
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
        CompoundTag copy = entityData.copy();
        if (copy.equals(this.entityData)) {
            return;
        }
        this.entityData = copy;
        dimensionsScale = PlayerDimensions.fromEntityData(copy);
        setDirty();
        refreshPlayerDimensions();
    }

    private void refreshPlayerDimensions() {
        if (world.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
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
        village.onEnter(world);
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
        return Gender.byId(NbtHelper.getCompoundOrSelf(entityData, VillagerEntityMCA.MCA_DATA_KEY).getInt("Gender").orElse(0));
    }

    @Override
    public @NotNull FamilyTreeNode getFamilyEntry() {
        return getFamilyTree().getOrEmpty(uuid).orElseGet(() -> {
            String name = Optional.ofNullable(world.getPlayerByUUID(uuid)).map(p -> p.getName().getString()).orElse("Unnamed Adventurer");
            return getFamilyTree().getOrCreate(uuid, name, getGender(), true);
        });
    }

    public void reset() {
        endRelationShip(RelationshipState.SINGLE);
        setDirty();
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        if (lastSeenVillageId != null) {
            nbt.putInt("lastSeenVillage", lastSeenVillageId);
        }
        nbt.put("entityData", entityData.copy());
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
        Optional.ofNullable(world.getPlayerByUUID(uuid)).ifPresent(p -> showMailNotification((ServerPlayer) p));
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

