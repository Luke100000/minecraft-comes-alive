package net.mca.server.world.data;

import net.mca.Config;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.PlayerDimensions;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.Gender;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.entity.ai.relationship.RelationshipType;
import net.mca.item.ItemsMCA;
import net.mca.network.s2c.ShowToastRequest;
import net.mca.resources.API;
import net.mca.resources.Rank;
import net.mca.resources.Tasks;
import net.mca.util.NbtHelper;
import net.mca.util.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class PlayerSaveData extends PersistentState implements EntityRelationship {
    private final ServerWorld world;
    private final UUID uuid;

    private Optional<Integer> lastSeenVillage = Optional.empty();

    private boolean entityDataSet;
    private boolean overrideVillageRequirements;
    private NbtCompound entityData;
    private PlayerDimensions.Scale dimensionsScale;

    private final List<NbtCompound> inbox = new LinkedList<>();

    public static PlayerSaveData get(ServerPlayerEntity player) {
        return get((ServerWorld) player.getWorld(), player.getUuid());
    }

    public static PlayerSaveData get(ServerWorld world, UUID uuid) {
        return WorldUtils.loadData(world.getServer().getOverworld(), nbt -> new PlayerSaveData(world, uuid, nbt), w -> new PlayerSaveData(world, uuid), "mca_player_" + uuid);
    }

    public static Optional<PlayerSaveData> getIfPresent(ServerWorld world, UUID uuid) {
        return Optional.ofNullable(world.getPersistentStateManager().get(nbt -> new PlayerSaveData(world, uuid, nbt), "mca_player_" + uuid));
    }

    PlayerSaveData(ServerWorld world, UUID uuid) {
        this.world = world;
        this.uuid = uuid;

        resetEntityData();
    }

    PlayerSaveData(ServerWorld world, UUID uuid, NbtCompound nbt) {
        this.world = world;
        this.uuid = uuid;

        lastSeenVillage = nbt.contains("lastSeenVillage", NbtElement.INT_TYPE) ? Optional.of(nbt.getInt("lastSeenVillage")) : Optional.empty();
        entityDataSet = nbt.contains("entityDataSet") && nbt.getBoolean("entityDataSet");
        overrideVillageRequirements = nbt.contains("overrideVillageRequirements") && nbt.getBoolean("overrideVillageRequirements");

        if (nbt.contains("entityData")) {
            entityData = nbt.getCompound("entityData");
        } else {
            resetEntityData();
        }

        NbtList inbox = nbt.getList("inbox", NbtElement.COMPOUND_TYPE);
        if (inbox != null) {
            this.inbox.clear();
            for (int i = 0; i < inbox.size(); i++) {
                this.inbox.add(inbox.getCompound(i));
            }
        }
    }

    private void resetEntityData() {
        entityData = new NbtCompound();

        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.get().create(world);
        assert villager != null;
        villager.initializeSkin(true);
        villager.getGenetics().randomize();
        villager.getTraits().randomize();
        villager.getVillagerBrain().randomize();
        ((MobEntity) villager).writeCustomDataToNbt(entityData);
    }

    public boolean isEntityDataSet() {
        return entityDataSet;
    }

    public NbtCompound getEntityData() {
        return entityData.copy();
    }

    public void setEntityDataSet(boolean entityDataSet) {
        if (this.entityDataSet == entityDataSet) {
            return;
        }
        this.entityDataSet = entityDataSet;
        markDirty();
        refreshPlayerDimensions();
    }

    public PlayerDimensions.Scale getDimensionsScale() {
        if (dimensionsScale == null) {
            dimensionsScale = PlayerDimensions.fromPlayerData(this);
        }
        return dimensionsScale;
    }

    public void setEntityData(NbtCompound entityData) {
        NbtCompound copy = entityData.copy();
        if (copy.equals(this.entityData)) {
            return;
        }
        this.entityData = copy;
        dimensionsScale = PlayerDimensions.fromPlayerData(this);
        markDirty();
        refreshPlayerDimensions();
    }

    private void refreshPlayerDimensions() {
        PlayerEntity player = world.getPlayerByUuid(uuid);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            PlayerDimensions.debugRefresh(serverPlayer, "before server player data refresh");
            serverPlayer.calculateDimensions();
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
        markDirty();
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

    public void updateLastSeenVillage(VillageManager manager, ServerPlayerEntity self) {
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

    public void setLastSeenVillage(ServerPlayerEntity self, Village oldVillage, @Nullable Village newVillage) {
        lastSeenVillage = Optional.ofNullable(newVillage).map(Village::getId);
        markDirty();

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

    protected void onLeave(PlayerEntity self, Village village) {
        if (Config.getInstance().enterVillageNotification && village.isVillage()) {
            self.sendMessage(Text.translatable("gui.village.left", village.getName()).formatted(Formatting.GOLD), true);
        }
    }

    protected void onEnter(PlayerEntity self, Village village) {
        if (Config.getInstance().enterVillageNotification && village.isVillage()) {
            self.sendMessage(Text.translatable("gui.village.welcome", village.getName()).formatted(Formatting.GOLD), true);
        }
        village.onEnter(world);
    }

    @Override
    public void marry(Entity spouse) {
        EntityRelationship.super.marry(spouse);
        markDirty();
    }

    @Override
    public void endRelationShip(RelationshipState newState) {
        EntityRelationship.super.endRelationShip(newState);
        markDirty();
    }

    @Override
    public ServerWorld getWorld() {
        return world;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public Gender getGender() {
        NbtCompound entityData = getEntityData();
        NbtCompound mcaData = entityData.contains(VillagerEntityMCA.MCA_DATA_KEY, NbtElement.COMPOUND_TYPE)
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
            String name = Optional.ofNullable(world.getPlayerByUuid(uuid)).map(p -> p.getName().getString()).orElse("Unnamed Adventurer");
            return getFamilyTree().getOrCreate(uuid, name, getGender(), true);
        });
    }

    public void reset() {
        endRelationShip(RelationshipState.SINGLE);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        lastSeenVillage.ifPresent(id -> nbt.putInt("lastSeenVillage", id));
        nbt.put("entityData", entityData);
        nbt.putBoolean("entityDataSet", entityDataSet);
        nbt.putBoolean("overrideVillageRequirements", overrideVillageRequirements);
        nbt.put("inbox", NbtHelper.fromList(inbox, v -> v));
        return nbt;
    }

    public void sendMail(NbtCompound nbt) {
        if (Config.getInstance().enableVillagerMailingPlayers) {
            inbox.add(nbt);
        }
        markDirty();
    }

    public boolean hasMail() {
        return !inbox.isEmpty();
    }

    public ItemStack getMail() {
        if (hasMail()) {
            NbtCompound nbt = inbox.remove(0);
            ItemStack stack = new ItemStack(ItemsMCA.LETTER.get(), 1);
            stack.setNbt(nbt);
            return stack;
        } else {
            return null;
        }
    }

    public void sendLetterOfCondolence(String name, String village) {
        sendLetter(List.of("{ \"translate\": \"mca.letter.condolence\", \"with\": [\"" + getFamilyEntry().getName() + "\", \"" + name + "\", \"" + village + "\"] }"));
    }

    public void sendLetter(List<String> lines) {
        NbtList l = new NbtList();
        for (String line : lines) {
            l.add(0, NbtString.of(line));
        }
        NbtCompound nbt = new NbtCompound();
        nbt.put("pages", l);
        sendMail(nbt);

        Optional.ofNullable(world.getPlayerByUuid(uuid)).ifPresent(p -> showMailNotification((ServerPlayerEntity) p));
    }

    public static void showMailNotification(ServerPlayerEntity player) {
        NetworkHandler.sendToPlayer(new ShowToastRequest(
                "server.mail.title",
                "server.mail.description"
        ), player);
    }
}
