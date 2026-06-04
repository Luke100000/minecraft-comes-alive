package net.conczin.mca.server.world.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.core.HolderLookup.Provider;
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
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerSaveData extends SavedData implements WorldUtils.NbtSavedData, EntityRelationship {
   private static final String EQUIPPED_RING_KEY = "MCAEquippedRing";
   private final ServerLevel world;
   private final UUID uuid;
   private final List<PlayerSaveData.Letter> inbox = new LinkedList<>();
   private Optional<Integer> lastSeenVillage = Optional.empty();
   private boolean entityDataSet;
   private CompoundTag entityData;
   private ItemStack equippedRing = ItemStack.EMPTY;

   PlayerSaveData(ServerLevel world, UUID uuid) {
      this.world = world;
      this.uuid = uuid;
      this.resetEntityData();
   }

   PlayerSaveData(ServerLevel world, UUID uuid, CompoundTag nbt) {
      this.world = world;
      this.uuid = uuid;
      this.lastSeenVillage = Optional.of(nbt.getInt("lastSeenVillage").orElse(-1)).filter(id -> id >= 0);
      this.entityDataSet = nbt.getBoolean("entityDataSet").orElse(false);
      if (nbt.contains("entityData")) {
         this.entityData = (CompoundTag)nbt.getCompound("entityData").orElseGet(CompoundTag::new);
      } else {
         this.resetEntityData();
      }

      this.equippedRing = normalizeEquippedRing(readEquippedRing(nbt, world.registryAccess()));
      this.ensureDefaultPlayerModel();
      ListTag inbox = (ListTag)nbt.getList("inbox").orElseGet(ListTag::new);
      this.inbox.addAll(NbtHelper.toList(inbox, e -> new PlayerSaveData.Letter((CompoundTag)e, world.registryAccess())));
   }

   public static PlayerSaveData get(ServerPlayer player) {
      return get(player.level(), player.getUUID());
   }

   public static PlayerSaveData get(ServerLevel world, UUID uuid) {
      return WorldUtils.loadData(
         world.getServer().overworld(), (nbt, provider) -> new PlayerSaveData(world, uuid, nbt), w -> new PlayerSaveData(world, uuid), "mca_player_" + uuid
      );
   }

   public static Optional<PlayerSaveData> getIfPresent(ServerLevel world, UUID uuid) {
      return Optional.ofNullable(
         (PlayerSaveData)world.getDataStorage()
            .get(
               new SavedDataType(
                  "mca_player_" + uuid,
                  () -> new PlayerSaveData(world, uuid),
                  CompoundTag.CODEC.xmap(nbt -> new PlayerSaveData(world, uuid, nbt), data -> data.save(new CompoundTag(), world.registryAccess())),
                  DataFixTypes.LEVEL
               )
            )
      );
   }

   public static void showMailNotification(ServerPlayer player) {
      Network.sendToPlayer(new ShowToastRequest("server.mail.title", "server.mail.description"), player);
   }

   private void resetEntityData() {
      VillagerEntityMCA villager = (VillagerEntityMCA)EntitiesMCA.MALE_VILLAGER.create(this.world, EntitySpawnReason.COMMAND);

      assert villager != null;

      villager.initializeSkin(true);
      villager.getGenetics().randomize();
      villager.getTraits().randomize();
      villager.getVillagerBrain().randomize();
      TagValueOutput output = WorldUtils.createValueOutput(this.world.registryAccess());
      villager.mca$addAdditionalSaveData(output);
      this.entityData = output.buildResult();
      this.ensureDefaultPlayerModel();
   }

   private void ensureDefaultPlayerModel() {
      if (this.entityData != null) {
         int playerModel = this.entityData.getInt("PlayerModel").orElse(VillagerLike.PlayerModel.PLAYER.ordinal());
         if (!this.entityData.contains("PlayerModel") || !this.entityDataSet && playerModel == VillagerLike.PlayerModel.VANILLA.ordinal()) {
            this.entityData.putInt("PlayerModel", VillagerLike.PlayerModel.PLAYER.ordinal());
            this.setDirty();
         }
      }
   }

   public boolean isEntityDataSet() {
      return this.entityDataSet;
   }

   public void setEntityDataSet(boolean entityDataSet) {
      this.entityDataSet = entityDataSet;
      this.setDirty();
   }

   public CompoundTag getEntityData() {
      this.ensureDefaultPlayerModel();
      return this.entityData;
   }

   public void setEntityData(CompoundTag entityData) {
      this.entityData = entityData.copy();
      this.ensureDefaultPlayerModel();
      this.setDirty();
   }

   public ItemStack getEquippedRing() {
      return this.equippedRing.copy();
   }

   public void setEquippedRing(ItemStack stack) {
      this.equippedRing = normalizeEquippedRing(stack);
      this.setDirty();
   }

   public CompoundTag createNetworkData() {
      CompoundTag nbt = this.getEntityData().copy();
      storeEquippedRing(nbt, this.world.registryAccess(), this.equippedRing);
      return nbt;
   }

   public static ItemStack readEquippedRing(CompoundTag nbt, Provider provider) {
      return WorldUtils.createValueInput(nbt, provider).read("MCAEquippedRing", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
   }

   public static void sync(ServerPlayer player) {
      PlayerSaveData data = get(player);
      CompoundTag nbt = data.createNetworkData();
      MCA.getServer()
         .ifPresent(server -> server.getPlayerList().getPlayers().forEach(target -> Network.sendToPlayer(new PlayerDataMessage(player.getUUID(), nbt), target)));
   }

   @Override
   public void onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity victim) {
      EntityRelationship.super.onTragedy(cause, burialSite, type, victim);
      if (victim instanceof VillagerEntityMCA victimVillager) {
         this.sendLetterOfCondolence(
            victimVillager.getName().getString(),
            victimVillager.getResidency().getHomeVillage().map(Village::getName).orElse(API.getVillagePool().pickVillageName("village"))
         );
      }
   }

   public void updateLastSeenVillage(VillageManager manager, ServerPlayer self) {
      Optional<Village> prevVillage = this.getLastSeenVillage(manager);
      Optional<Village> nextVillage = prevVillage.filter(v -> v.isWithinBorder(self)).or(() -> manager.findNearestVillage(self));
      this.setLastSeenVillage(self, prevVillage.orElse(null), nextVillage.orElse(null));
      if (nextVillage.isPresent()) {
         Rank rank = Tasks.getRank(nextVillage.get(), self);
         CriterionMCA.RANK.trigger(self, rank);
      }
   }

   public void setLastSeenVillage(ServerPlayer self, Village oldVillage, @Nullable Village newVillage) {
      this.lastSeenVillage = Optional.ofNullable(newVillage).map(Village::getId);
      this.setDirty();
      if (oldVillage != newVillage) {
         if (oldVillage != null) {
            this.onLeave(self, oldVillage);
         }

         if (newVillage != null) {
            this.onEnter(self, newVillage);
         }
      }
   }

   public Optional<Village> getLastSeenVillage(VillageManager manager) {
      return this.lastSeenVillage.flatMap(manager::getOrEmpty);
   }

   public Optional<Integer> getLastSeenVillageId() {
      return this.lastSeenVillage;
   }

   protected void onLeave(Player self, Village village) {
      if (Config.getInstance().enterVillageNotification && village.isVillage()) {
         self.displayClientMessage(Component.translatable("gui.village.left", new Object[]{village.getName()}).withStyle(ChatFormatting.GOLD), true);
      }
   }

   protected void onEnter(Player self, Village village) {
      if (Config.getInstance().enterVillageNotification && village.isVillage()) {
         self.displayClientMessage(Component.translatable("gui.village.welcome", new Object[]{village.getName()}).withStyle(ChatFormatting.GOLD), true);
      }

      village.onEnter(this.world);
   }

   @Override
   public void marry(Entity spouse) {
      EntityRelationship.super.marry(spouse);
      this.setDirty();
   }

   @Override
   public void engage(Entity spouse) {
      EntityRelationship.super.engage(spouse);
      this.setDirty();
   }

   @Override
   public void promise(Entity spouse) {
      EntityRelationship.super.promise(spouse);
      this.setDirty();
   }

   @Override
   public void endRelationShip(RelationshipState newState) {
      EntityRelationship.super.endRelationShip(newState);
      this.setDirty();
   }

   @Override
   public ServerLevel getWorld() {
      return this.world;
   }

   @Override
   public UUID getUUID() {
      return this.uuid;
   }

   @Override
   public Gender getGender() {
      return Gender.byId(this.getEntityData().getInt("Gender").orElse(this.getEntityData().getInt("gender").orElse(0)));
   }

   @NotNull
   @Override
   public FamilyTreeNode getFamilyEntry() {
      return this.getFamilyTree().getOrEmpty(this.uuid).orElseGet(() -> {
         String name = Optional.ofNullable(this.world.getPlayerByUUID(this.uuid)).map(p -> p.getName().getString()).orElse("Unnamed Adventurer");
         return this.getFamilyTree().getOrCreate(this.uuid, name, this.getGender(), true);
      });
   }

   public void reset() {
      this.endRelationShip(RelationshipState.SINGLE);
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag nbt, Provider provider) {
      this.lastSeenVillage.ifPresent(id -> nbt.putInt("lastSeenVillage", id));
      nbt.put("entityData", this.entityData);
      nbt.putBoolean("entityDataSet", this.entityDataSet);
      nbt.put("inbox", NbtHelper.fromList(this.inbox, v -> v.toTag(provider)));
      storeEquippedRing(nbt, provider, this.equippedRing);
      return nbt;
   }

   public void sendMail(PlayerSaveData.Letter pages) {
      if (Config.getInstance().enableVillagerMailingPlayers) {
         this.inbox.add(pages);
      }

      this.setDirty();
   }

   public boolean hasMail() {
      return !this.inbox.isEmpty();
   }

   public ItemStack getMail() {
      if (this.hasMail()) {
         PlayerSaveData.Letter letter = this.inbox.removeFirst();
         ItemStack stack = new ItemStack(ItemsMCA.LETTER, 1);
         stack.set(DataComponentsMCA.BOOK_PAGES, letter.pages());
         return stack;
      } else {
         return null;
      }
   }

   public void sendLetterOfCondolence(String name, String village) {
      this.sendLetter(Component.translatable("mca.letter.condolence", new Object[]{this.getFamilyEntry().getName(), name, village}));
   }

   public void sendLetter(Component... lines) {
      this.sendMail(new PlayerSaveData.Letter("", Arrays.asList(lines)));
      Optional.ofNullable(this.world.getPlayerByUUID(this.uuid)).ifPresent(p -> showMailNotification((ServerPlayer)p));
   }

   private static ItemStack normalizeEquippedRing(ItemStack stack) {
      if (!stack.isEmpty() && RelationshipItem.isRing(stack)) {
         ItemStack copy = stack.copy();
         copy.setCount(1);
         return copy;
      } else {
         return ItemStack.EMPTY;
      }
   }

   private static void storeEquippedRing(CompoundTag nbt, Provider provider, ItemStack ring) {
      if (!ring.isEmpty()) {
         TagValueOutput output = WorldUtils.createValueOutput(provider);
         output.store("MCAEquippedRing", ItemStack.OPTIONAL_CODEC, ring);
         CompoundTag stored = WorldUtils.getCompoundTag(output);
         if (stored.contains("MCAEquippedRing")) {
            Tag tag = stored.get("MCAEquippedRing");
            if (tag != null) {
               nbt.put("MCAEquippedRing", tag);
            }
         }
      }
   }

   public record Letter(String title, List<Component> pages) {
      private static final Codec<List<Component>> PAGES_CODEC = ComponentSerialization.CODEC.listOf();

      public Letter(CompoundTag nbt, Provider registries) {
         this(
            nbt.getString("title").orElse(""),
            Optional.ofNullable(nbt.get("pages"))
               .flatMap(tag -> PAGES_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).resultOrPartial(MCA.LOGGER::error))
               .orElse(List.of())
         );
      }

      CompoundTag toTag(Provider registries) {
         CompoundTag nbt = new CompoundTag();
         nbt.putString("title", this.title);
         DynamicOps<Tag> dynamicOps = registries.createSerializationContext(NbtOps.INSTANCE);
         ComponentSerialization.CODEC.listOf().encodeStart(dynamicOps, this.pages).resultOrPartial(MCA.LOGGER::error).ifPresent(tag -> nbt.put("pages", tag));
         return nbt;
      }
   }
}
