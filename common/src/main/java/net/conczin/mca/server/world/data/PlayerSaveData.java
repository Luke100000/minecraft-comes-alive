package net.conczin.mca.server.world.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.item.RelationshipItem;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
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
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerSaveData extends SavedData implements WorldUtils.NbtSavedData, EntityRelationship {
    private static final String EQUIPPED_RING_KEY = "MCAEquippedRing";

    private final ServerLevel world;
    private final UUID uuid;
    private final List<Letter> inbox = new LinkedList<>();
    private Optional<Integer> lastSeenVillage = Optional.empty();
    private boolean entityDataSet;
    private CompoundTag entityData;
    private ItemStack equippedRing = ItemStack.EMPTY;

    PlayerSaveData(ServerLevel world, UUID uuid) {
        this.world = world;
        this.uuid = uuid;

        resetEntityData();
    }

    PlayerSaveData(ServerLevel world, UUID uuid, CompoundTag nbt) {
        this.world = world;
        this.uuid = uuid;

        lastSeenVillage = Optional.of(nbt.getInt("lastSeenVillage").orElse(-1)).filter(id -> id >= 0);
        entityDataSet = nbt.getBoolean("entityDataSet").orElse(false);

        if (nbt.contains("entityData")) {
            entityData = nbt.getCompound("entityData").orElseGet(CompoundTag::new);
        } else {
            resetEntityData();
        }

        equippedRing = normalizeEquippedRing(readEquippedRing(nbt, world.registryAccess()));

        ensureDefaultPlayerModel();

        ListTag inbox = nbt.getList("inbox").orElseGet(ListTag::new);
        this.inbox.addAll(NbtHelper.toList(inbox, e -> new Letter((CompoundTag) e, world.registryAccess())));
    }

    public static PlayerSaveData get(ServerPlayer player) {
        return get((ServerLevel) player.level(), player.getUUID());
    }

    public static PlayerSaveData get(ServerLevel world, UUID uuid) {
        return WorldUtils.loadData(world.getServer().overworld(), (nbt, provider) -> new PlayerSaveData(world, uuid, nbt), w -> new PlayerSaveData(world, uuid), "mca_player_" + uuid);
    }

    @SuppressWarnings("DataFlowIssue")
    public static Optional<PlayerSaveData> getIfPresent(ServerLevel world, UUID uuid) {
        return Optional.ofNullable(world.getDataStorage().get(new SavedDataType<>(
                "mca_player_" + uuid,
                () -> new PlayerSaveData(world, uuid),
                CompoundTag.CODEC.xmap(
                        nbt -> new PlayerSaveData(world, uuid, nbt),
                        data -> data.save(new CompoundTag(), world.registryAccess())
                ),
                DataFixTypes.LEVEL
        )));
    }

    public static void showMailNotification(ServerPlayer player) {
        Network.sendToPlayer(new ShowToastRequest(
                "server.mail.title",
                "server.mail.description"
        ), player);
    }

    private void resetEntityData() {
        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.create(world, EntitySpawnReason.COMMAND);
        assert villager != null;
        villager.initializeSkin(true);
        villager.getGenetics().randomize();
        villager.getTraits().randomize();
        villager.getVillagerBrain().randomize();
        var output = WorldUtils.createValueOutput(world.registryAccess());
        villager.mca$addAdditionalSaveData(output);
        entityData = output.buildResult();
        ensureDefaultPlayerModel();
    }

    private void ensureDefaultPlayerModel() {
        if (entityData != null) {
            int playerModel = entityData.getInt("PlayerModel").orElse(VillagerLike.PlayerModel.PLAYER.ordinal());
            if (!entityData.contains("PlayerModel") || (!entityDataSet && playerModel == VillagerLike.PlayerModel.VANILLA.ordinal())) {
                entityData.putInt("PlayerModel", VillagerLike.PlayerModel.PLAYER.ordinal());
                setDirty();
            }
        }
    }

    public boolean isEntityDataSet() {
        return entityDataSet;
    }

    public void setEntityDataSet(boolean entityDataSet) {
        this.entityDataSet = entityDataSet;
        setDirty();
    }

    public CompoundTag getEntityData() {
        ensureDefaultPlayerModel();
        return entityData;
    }

    public void setEntityData(CompoundTag entityData) {
        this.entityData = entityData.copy();
        ensureDefaultPlayerModel();
        setDirty();
    }

    public ItemStack getEquippedRing() {
        return equippedRing.copy();
    }

    public void setEquippedRing(ItemStack stack) {
        equippedRing = normalizeEquippedRing(stack);
        setDirty();
    }

    public CompoundTag createNetworkData() {
        CompoundTag nbt = getEntityData().copy();
        storeEquippedRing(nbt, world.registryAccess(), equippedRing);
        return nbt;
    }

    public static ItemStack readEquippedRing(CompoundTag nbt, HolderLookup.Provider provider) {
        return WorldUtils.createValueInput(nbt, provider).read(EQUIPPED_RING_KEY, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    public static void sync(ServerPlayer player) {
        PlayerSaveData data = get(player);
        CompoundTag nbt = data.createNetworkData();
        MCA.getServer().ifPresent(server ->
                server.getPlayerList().getPlayers().forEach(target -> Network.sendToPlayer(new PlayerDataMessage(player.getUUID(), nbt), target))
        );
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
            net.conczin.mca.util.PlayerMessageHelper.displayClientMessage(self, Component.translatable("gui.village.left", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    protected void onEnter(Player self, Village village) {
        if (Config.getInstance().enterVillageNotification && village.isVillage()) {
            net.conczin.mca.util.PlayerMessageHelper.displayClientMessage(self, Component.translatable("gui.village.welcome", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
        village.onEnter(world);
    }

    @Override
    public void marry(Entity spouse) {
        EntityRelationship.super.marry(spouse);
        setDirty();
    }

    @Override
    public void engage(Entity spouse) {
        EntityRelationship.super.engage(spouse);
        setDirty();
    }

    @Override
    public void promise(Entity spouse) {
        EntityRelationship.super.promise(spouse);
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
        return Gender.byId(getEntityData().getInt("Gender").orElse(getEntityData().getInt("gender").orElse(0)));
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
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        lastSeenVillage.ifPresent(id -> nbt.putInt("lastSeenVillage", id));
        nbt.put("entityData", entityData);
        nbt.putBoolean("entityDataSet", entityDataSet);
        nbt.put("inbox", NbtHelper.fromList(inbox, v -> v.toTag(provider)));
        storeEquippedRing(nbt, provider, equippedRing);
        return nbt;
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

    private static ItemStack normalizeEquippedRing(ItemStack stack) {
        if (stack.isEmpty() || !RelationshipItem.isRing(stack)) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static void storeEquippedRing(CompoundTag nbt, HolderLookup.Provider provider, ItemStack ring) {
        if (ring.isEmpty()) {
            return;
        }

        var output = WorldUtils.createValueOutput(provider);
        output.store(EQUIPPED_RING_KEY, ItemStack.OPTIONAL_CODEC, ring);

        CompoundTag stored = WorldUtils.getCompoundTag(output);
        if (stored.contains(EQUIPPED_RING_KEY)) {
            Tag tag = stored.get(EQUIPPED_RING_KEY);
            if (tag != null) {
                nbt.put(EQUIPPED_RING_KEY, tag);
            }
        }
    }

    public record Letter(String title, List<Component> pages) {
        private static final Codec<List<Component>> PAGES_CODEC = ComponentSerialization.CODEC.listOf();

        public Letter(CompoundTag nbt, HolderLookup.Provider registries) {
            this(
                    nbt.getString("title").orElse(""),
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
            ComponentSerialization.CODEC.listOf()
                    .encodeStart(dynamicOps, pages).
                    resultOrPartial(MCA.LOGGER::error)
                    .ifPresent(tag -> nbt.put("pages", tag));

            return nbt;
        }
    }
}
