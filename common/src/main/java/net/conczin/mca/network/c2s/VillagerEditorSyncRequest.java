package net.conczin.mca.network.c2s;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairList;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;

public record VillagerEditorSyncRequest(String command, UUID uuid, CompoundTag data) implements HandleablePayload {
   public static final Type<VillagerEditorSyncRequest> TYPE = new Type(MCA.locate("villager_editor_sync_request"));
   public static final StreamCodec<FriendlyByteBuf, VillagerEditorSyncRequest> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8,
      VillagerEditorSyncRequest::command,
      UUIDUtil.STREAM_CODEC,
      VillagerEditorSyncRequest::uuid,
      ByteBufCodecs.COMPOUND_TAG,
      VillagerEditorSyncRequest::data,
      VillagerEditorSyncRequest::new
   );

   @Override
   public void handleServer(ServerPlayer player) {
      Entity entity = player.level().getEntity(this.uuid);
      String var3 = this.command;
      switch (var3) {
         case "hair":
            this.setHair(player, entity);
            break;
         case "clothing":
            this.setClothing(player, entity);
            break;
         case "gender":
            this.setHair(player, entity);
            this.setClothing(player, entity);
            break;
         case "sync":
            this.saveEntity(player, entity, this.data());
            break;
         case "profession":
            if (entity instanceof VillagerEntityMCA villager) {
               BuiltInRegistries.VILLAGER_PROFESSION
                  .getOptional(Identifier.parse(this.data.getString("profession").orElse(VillagerProfession.NONE.identifier().toString())))
                  .ifPresent(villager::setProfession);
            }
      }
   }

   private void setHair(ServerPlayer player, Entity entity) {
      CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
      if (villagerData != null) {
         String hair;
         if (this.data.contains("offset")) {
            hair = HairList.getInstance()
               .getPool(this.getGender(villagerData))
               .pickNext(villagerData.getString("Hair").orElse(""), this.data.getInt("offset").orElse(0));
         } else {
            hair = HairList.getInstance().getPool(this.getGender(villagerData)).pickOne();
         }

         villagerData.putString("Hair", hair);
         this.saveEntity(player, entity, villagerData);
      }
   }

   private void setClothing(ServerPlayer player, Entity entity) {
      CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
      if (villagerData != null) {
         String clothes = "mca:missing";
         if (entity instanceof Player) {
            if (this.data.contains("offset")) {
               clothes = ClothingList.getInstance()
                  .getPool(this.getGender(villagerData), ProfessionsMCA.value(VillagerProfession.NONE))
                  .pickNext(villagerData.getString("Clothes").orElse(""), this.data.getInt("offset").orElse(0));
            } else {
               clothes = ClothingList.getInstance().getPool(this.getGender(villagerData), ProfessionsMCA.value(VillagerProfession.NONE)).pickOne();
            }
         } else if (entity instanceof VillagerLike<?> villager) {
            if (this.data.contains("offset")) {
               clothes = ClothingList.getInstance().getPool(villager).pickNext(villager.getClothes(), this.data.getInt("offset").orElse(0));
            } else {
               clothes = ClothingList.getInstance().getPool(villager).pickOne();
            }
         }

         villagerData.putString("Clothes", clothes);
         this.saveEntity(player, entity, villagerData);
      }
   }

   private void saveEntity(ServerPlayer player, Entity entity, CompoundTag villagerData) {
      if (entity instanceof ServerPlayer serverPlayer) {
         PlayerSaveData data = PlayerSaveData.get(serverPlayer);
         data.setEntityDataSet(true);
         data.setEntityData(villagerData);
         this.syncFamilyTree(player, entity, villagerData);
         PlayerSaveData.sync(serverPlayer);
      } else if (entity instanceof VillagerLike<?> villagerLike) {
         villagerLike.syncFromEditor(villagerData);
         entity.refreshDimensions();
         this.syncFamilyTree(player, entity, villagerData);
         if (entity instanceof VillagerEntityMCA villager) {
            villager.getResidency().getHomeVillage().ifPresent(b -> b.updateResident(villager));
         }
      }
   }

   private Gender getGender(CompoundTag villagerData) {
      return Gender.byId(villagerData.getInt("Gender").orElse(villagerData.getInt("gender").orElse(Gender.MALE.ordinal())));
   }

   private Optional<FamilyTreeNode> getFamilyNode(ServerPlayer player, FamilyTree tree, String name, Gender gender) {
      try {
         UUID uuid = UUID.fromString(name);
         Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
         if (node.isPresent()) {
            player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_known", new Object[]{name, node.get().getName()}), true);
            return node;
         } else {
            player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_unknown", new Object[]{name}).withStyle(ChatFormatting.RED), true);
            return Optional.empty();
         }
      } catch (IllegalArgumentException var8) {
         List<FamilyTreeNode> nodes = tree.getAllWithName(name).toList();
         if (nodes.isEmpty()) {
            player.displayClientMessage(Component.translatable("gui.villager_editor.name_created", new Object[]{name}).withStyle(ChatFormatting.YELLOW), true);
            return Optional.of(tree.getOrCreate(UUID.randomUUID(), name, gender));
         } else {
            if (nodes.size() > 1) {
               player.displayClientMessage(
                  Component.translatable("gui.villager_editor.name_not_unique", new Object[]{name}).withStyle(ChatFormatting.RED), true
               );
               String uuids = nodes.stream().map(FamilyTreeNode::id).map(UUID::toString).collect(Collectors.joining(", "));
               player.displayClientMessage(Component.translatable("gui.villager_editor.list_of_ids", new Object[]{uuids}), false);
            } else {
               player.displayClientMessage(Component.translatable("gui.villager_editor.name_unique", new Object[]{name}), true);
            }

            return Optional.ofNullable(nodes.getFirst());
         }
      }
   }

   private void syncFamilyTree(ServerPlayer player, Entity entity, CompoundTag villagerData) {
      FamilyTree tree = FamilyTree.get((ServerLevel)entity.level());
      FamilyTreeNode entry = tree.getOrCreate(entity);
      entry.setGender(this.getGender(this.data));
      String s = villagerData.getString("CustomName").orElse("");
      if (!s.isEmpty()) {
         try {
            entry.setName(
               ((Component)Objects.requireNonNull(
                     (Component)ComponentSerialization.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(s))
                        .resultOrPartial(error -> MCA.LOGGER.error("Failed to parse custom name for villager: {}", error))
                        .orElse(null)
                  ))
                  .getString()
            );
         } catch (Exception var8) {
            MCA.LOGGER.error("Failed to parse custom name for villager: {}", s, var8);
         }
      }

      if (villagerData.contains("FamilyTreeNewFatherName")) {
         String name = villagerData.getString("FamilyTreeNewFatherName").orElse("");
         if (MCA.isBlankString(name)) {
            entry.removeFather();
         } else {
            this.getFamilyNode(player, tree, name, Gender.MALE).ifPresent(entry::setFather);
         }
      }

      if (villagerData.contains("FamilyTreeNewMotherName")) {
         String name = villagerData.getString("FamilyTreeNewMotherName").orElse("");
         if (MCA.isBlankString(name)) {
            entry.removeMother();
         } else {
            this.getFamilyNode(player, tree, name, Gender.FEMALE).ifPresent(entry::setMother);
         }
      }

      if (villagerData.contains("FamilyTreeNewSpouseName")) {
         String name = villagerData.getString("FamilyTreeNewSpouseName").orElse("");
         if (MCA.isBlankString(name)) {
            Optional.of(entry.partner()).flatMap(tree::getOrEmpty).ifPresent(node -> node.updatePartner(null, null));
            entry.updatePartner(null, null);
         } else {
            this.getFamilyNode(player, tree, name, entry.gender().opposite()).ifPresent(node -> {
               entry.updatePartner(node);
               node.updatePartner(entry);
            });
         }
      }
   }

   public Type<VillagerEditorSyncRequest> type() {
      return TYPE;
   }
}
