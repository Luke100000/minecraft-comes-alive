package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.advancement.criterion.CriterionMCA;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.datafix.McaDataFixers;
import net.conczin.mca.entity.EntitiesMCA;
import net.conczin.mca.entity.PlayerDimensions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.item.ItemsMCA;
import net.conczin.mca.network.s2c.ShowToastRequest;
import net.conczin.mca.resources.API;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class PlayerSaveData extends SavedData implements EntityRelationship {
    private final ServerLevel world;
    private final UUID uuid;

    private Optional<Integer> lastSeenVillage = Optional.empty();

    private boolean entityDataSet;
    private boolean overrideVillageRequirements;
    private String chatAIPrompt = "";
    private CompoundTag entityData;
    private PlayerDimensions.Scale dimensionsScale;

    private final List<CompoundTag> inbox = new LinkedList<>();

    public static PlayerSaveData get(ServerPlayer player) {
        return get((ServerLevel) player.level(), player.getUUID());
    }

    public static PlayerSaveData get(ServerLevel world, UUID uuid) {
        return WorldUtils.loadData(world.getServer().overworld(), nbt -> new PlayerSaveData(world, uuid, nbt), w -> new PlayerSaveData(world, uuid), "mca_player_" + uuid);
    }

    public static Optional<PlayerSaveData> getIfPresent(ServerLevel world, UUID uuid) {
        return Optional.ofNullable(world.getDataStorage().get(nbt -> new PlayerSaveData(world, uuid, nbt), "mca_player_" + uuid));
    }

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
            entityData = McaDataFixers.update(storedEntityData);
            if (!entityData.equals(storedEntityData)) {
                setDirty();
            }
        } else {
            resetEntityData();
        }

        ListTag inbox = nbt.getList("inbox", Tag.TAG_COMPOUND);
        if (inbox != null) {
            this.inbox.clear();
            for (int i = 0; i < inbox.size(); i++) {
                this.inbox.add(inbox.getCompound(i));
            }
        }
    }

    private void resetEntityData() {
        entityData = new CompoundTag();

        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.get().create(world);
        assert villager != null;
        villager.initializeSkin(true);
        villager.getGenetics().randomize();
        villager.getTraits().randomize();
        villager.getVillagerBrain().randomize();
        ((Mob) villager).addAdditionalSaveData(entityData);
    }

    public boolean isEntityDataSet() {
        return entityDataSet;
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

    public void setEntityDataSet(boolean entityDataSet) {
        if (this.entityDataSet == entityDataSet) {
            return;
        }
        this.entityDataSet = entityDataSet;
        setDirty();
        refreshPlayerDimensions();
    }

    public PlayerDimensions.Scale getDimensionsScale() {
        if (dimensionsScale == null) {
            dimensionsScale = PlayerDimensions.fromPlayerData(this);
        }
        return dimensionsScale;
    }

    public void setEntityData(CompoundTag entityData) {
        CompoundTag copy = McaDataFixers.update(entityData.copy());
        if (copy.equals(this.entityData)) {
            return;
        }
        this.entityData = copy;
        dimensionsScale = PlayerDimensions.fromPlayerData(this);
        setDirty();
        refreshPlayerDimensions();
    }

    private void refreshPlayerDimensions() {
        Player player = world.getPlayerByUUID(uuid);
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerDimensions.debugRefresh(serverPlayer, "before server player data refresh");
            serverPlayer.refreshDimensions();
            PlayerDimensions.debugRefresh(serverPlayer, "after server player data refresh");
        }
    }

    public boolean shouldOverrideVillageRequirements() {
        return overrideVillageRequirements;
    }

    public boolean isOverrideVillageRequirements() {
        return overrideVillageRequirements;
    }

    public void setOverrideVillageRequirements(boolean overrideVillageRequirements) {
        this.overrideVillageRequirements = overrideVillageRequirements;
        setDirty();
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
        if (Config.getInstance().enterVillageNotification && village.isVillage()) {
            self.displayClientMessage(Component.translatable("gui.village.left", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    protected void onEnter(Player self, Village village) {
        if (Config.getInstance().enterVillageNotification && village.isVillage()) {
            self.displayClientMessage(Component.translatable("gui.village.welcome", village.getName()).withStyle(ChatFormatting.GOLD), true);
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
        CompoundTag entityData = getEntityData();
        CompoundTag mcaData = entityData.contains(VillagerEntityMCA.MCA_DATA_KEY, Tag.TAG_COMPOUND)
                ? entityData.getCompound(VillagerEntityMCA.MCA_DATA_KEY)
                : entityData;
        Gender gender = Genetics.readGender(mcaData);
        if (gender != Gender.UNASSIGNED || mcaData == entityData) {
            return gender;
        }
        return Genetics.readGender(entityData);
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

    @Override
    public CompoundTag save(CompoundTag nbt) {
        lastSeenVillage.ifPresent(id -> nbt.putInt("lastSeenVillage", id));
        McaDataFixers.stampCurrentVersion(entityData);
        nbt.put("entityData", entityData);
        nbt.putBoolean("entityDataSet", entityDataSet);
        nbt.putBoolean("overrideVillageRequirements", overrideVillageRequirements);
        nbt.putString("chatAIPrompt", chatAIPrompt);
        nbt.put("inbox", NbtHelper.fromList(inbox, v -> v));
        return nbt;
    }

    public void sendMail(CompoundTag nbt) {
        if (Config.getInstance().enableVillagerMailingPlayers) {
            inbox.add(nbt);
        }
        setDirty();
    }

    public boolean hasMail() {
        return !inbox.isEmpty();
    }

    public ItemStack getMail() {
        if (hasMail()) {
            CompoundTag nbt = inbox.remove(0);
            ItemStack stack = new ItemStack(ItemsMCA.LETTER.get(), 1);
            stack.setTag(nbt);
            return stack;
        } else {
            return null;
        }
    }

    public void sendLetterOfCondolence(String name, String village) {
        sendLetter(List.of("{ \"translate\": \"mca.letter.condolence\", \"with\": [\"" + getFamilyEntry().getName() + "\", \"" + name + "\", \"" + village + "\"] }"));
    }

    public void sendLetter(List<String> lines) {
        ListTag l = new ListTag();
        for (String line : lines) {
            l.add(0, StringTag.valueOf(line));
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("pages", l);
        sendMail(nbt);

        Optional.ofNullable(world.getPlayerByUUID(uuid)).ifPresent(p -> showMailNotification((ServerPlayer) p));
    }

    public static void showMailNotification(ServerPlayer player) {
        NetworkHandler.sendToPlayer(new ShowToastRequest(
                "server.mail.title",
                "server.mail.description"
        ), player);
    }
}
