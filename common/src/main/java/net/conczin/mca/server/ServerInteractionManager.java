package net.conczin.mca.server;

import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.item.EngagementRingItem;
import net.conczin.mca.item.RelationshipItem;
import net.conczin.mca.item.WeddingRingItem;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenDestinyGuiRequest;
import net.conczin.mca.network.s2c.PlayerInteractionAnimationMessage;
import net.conczin.mca.network.s2c.ShowToastRequest;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ServerInteractionManager {
   private static final long SOCIAL_INTERACTION_COOLDOWN_MS = 2000L;
   private static final int KISS_BASE_DURATION_TICKS = 14;
   private static final int KISS_BASE_REGEN_TICKS = 60;
   private static final int KISS_BASE_XP = 2;
   private static final ServerInteractionManager INSTANCE = new ServerInteractionManager();
   private final Map<UUID, List<UUID>> proposals = new HashMap<>();
   private final Object2LongArrayMap<UUID> procreateMap = new Object2LongArrayMap();
   private final Object2LongArrayMap<UUID> socialCooldowns = new Object2LongArrayMap();

   private ServerInteractionManager() {
   }

   public static ServerInteractionManager getInstance() {
      return INSTANCE;
   }

   public static void launchDestiny(ServerPlayer player) {
      Network.sendToPlayer(new OpenDestinyGuiRequest(player), player);
   }

   public void tick() {
      this.pruneExpired(this.procreateMap);
      this.pruneExpired(this.socialCooldowns);
      MCA.getServer().ifPresent(server -> server.getPlayerList().getPlayers().forEach(this::applyNearbySpouseBenefits));
   }

   public void onPlayerJoin(ServerPlayer player) {
      PlayerSaveData playerData = PlayerSaveData.get(player);
      if (!playerData.isEntityDataSet()) {
         if (Config.getInstance().launchIntoDestiny) {
            launchDestiny(player);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3600));
            player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 3600));
         } else if (Config.getInstance().allowDestinyCommandOnce) {
            Network.sendToPlayer(new ShowToastRequest("server.destinyNotSet.title", "server.destinyNotSet.description"), player);
         } else if (Config.getInstance().allowFullPlayerEditor) {
            Network.sendToPlayer(new ShowToastRequest("server.playerNotCustomized.title", "server.playerNotCustomized.description"), player);
         }
      }

      if (playerData.hasMail()) {
         PlayerSaveData.showMailNotification(player);
      }
   }

   private boolean hasProposalFrom(ServerPlayer sender, ServerPlayer receiver) {
      return this.getProposalsFor(receiver).contains(sender.getUUID());
   }

   private List<UUID> getProposalsFor(ServerPlayer player) {
      return this.proposals.getOrDefault(player.getUUID(), new ArrayList<>());
   }

   private void removeProposalFor(ServerPlayer target, ServerPlayer proposer) {
      List<UUID> list = this.getProposalsFor(target);
      list.remove(proposer.getUUID());
      this.proposals.put(target.getUUID(), list);
   }

   private void pruneExpired(Object2LongArrayMap<UUID> timedMap) {
      List<UUID> removals = new ArrayList<>();
      timedMap.keySet().stream().filter(uuid -> timedMap.getLong(uuid) < System.currentTimeMillis()).forEach(removals::add);
      removals.forEach(timedMap::removeLong);
   }

   private void applyNearbySpouseBenefits(ServerPlayer player) {
      if (player.isAlive()) {
         PlayerSaveData data = PlayerSaveData.get(player);
         if (data.getRelationshipState() == RelationshipState.MARRIED_TO_PLAYER) {
            Player spousePlayer = data.getPartnerUUID().<Player>map(player.level()::getPlayerByUUID).orElse(null);
            ServerPlayer spouse = spousePlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (spouse != null && spouse.level() == player.level() && !(player.distanceToSqr(spouse) > 36.0)) {
               player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false, true));
            }
         }
      }
   }

   private boolean hasUsableRing(ServerPlayer player, Class<? extends Item> ringType) {
      if (ringType.isInstance(player.getMainHandItem().getItem())) {
         return true;
      } else {
         ItemStack equippedRing = PlayerSaveData.get(player).getEquippedRing();
         return !equippedRing.isEmpty() && ringType.isInstance(equippedRing.getItem());
      }
   }

   private void autoEquipHeldRing(ServerPlayer player, Class<? extends Item> ringType) {
      ItemStack held = player.getMainHandItem();
      if (ringType.isInstance(held.getItem())) {
         RelationshipItem.equipRing(player, held);
      } else {
         PlayerSaveData.sync(player);
      }
   }

   private boolean isOnSocialCooldown(ServerPlayer player) {
      return this.socialCooldowns.getLong(player.getUUID()) > System.currentTimeMillis();
   }

   private void playAffectionAnimation(ServerPlayer sender, ServerPlayer receiver, String action, int durationTicks) {
      this.playAffectionAnimation(sender, receiver, action, durationTicks, 1.0F);
   }

   private void playAffectionAnimation(ServerPlayer sender, ServerPlayer receiver, String action, int durationTicks, float strength) {
      long expiresAt = System.currentTimeMillis() + 2000L;
      this.socialCooldowns.put(sender.getUUID(), expiresAt);
      this.socialCooldowns.put(receiver.getUUID(), expiresAt);
      sender.swing(InteractionHand.MAIN_HAND);
      receiver.swing(InteractionHand.MAIN_HAND);
      ServerLevel serverLevel = sender.level();
      if (serverLevel instanceof ServerLevel) {
         PlayerInteractionAnimationMessage messagex = new PlayerInteractionAnimationMessage(
            sender.getUUID(), receiver.getUUID(), action, durationTicks, strength
         );
         serverLevel.players().forEach(player -> Network.sendToPlayer(messagex, player));
         double x = (sender.getX() + receiver.getX()) * 0.5;
         double y = (sender.getY(0.75) + receiver.getY(0.75)) * 0.5;
         double z = (sender.getZ() + receiver.getZ()) * 0.5;
         if ("kiss".equals(action)) {
            int hearts = 6 + Mth.ceil(Math.max(0.0F, strength - 1.0F) * 4.0F);
            serverLevel.sendParticles(ParticleTypes.HEART, x, y, z, hearts, 0.2 + strength * 0.05, 0.15 + strength * 0.03, 0.2 + strength * 0.05, 0.01);
         } else {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 8, 0.3, 0.15, 0.3, 0.02);
         }
      }
   }

   private ServerInteractionManager.FoodShare shareHeldFoodForKiss(ServerPlayer sender, ServerPlayer receiver) {
      for (InteractionHand hand : InteractionHand.values()) {
         ItemStack stack = sender.getItemInHand(hand);
         if (!stack.isEmpty()) {
            FoodProperties food = (FoodProperties)stack.get(DataComponents.FOOD);
            if (food != null && food.nutrition() > 0) {
               receiver.getFoodData().eat(food);
               if (!sender.getAbilities().instabuild) {
                  stack.shrink(1);
                  sender.setItemInHand(hand, stack);
                  sender.containerMenu.broadcastChanges();
               }

               return new ServerInteractionManager.FoodShare(food.nutrition(), food.saturation());
            }
         }
      }

      return ServerInteractionManager.FoodShare.NONE;
   }

   public void listProposals(ServerPlayer sender) {
      List<UUID> proposals = this.getProposalsFor(sender);
      if (proposals.isEmpty()) {
         this.infoMessage(sender, Component.translatable("server.noProposals"));
      } else {
         this.infoMessage(sender, Component.translatable("server.proposals"));
      }

      proposals.forEach(uuid -> {
         Player player = sender.level().getPlayerByUUID(uuid);
         if (player != null) {
            this.infoMessage(sender, Component.literal("- ").append(Component.literal(player.getScoreboardName())));
         }
      });
   }

   public void sendProposal(ServerPlayer sender, ServerPlayer receiver) {
      if (!Config.getInstance().allowPlayerMarriage) {
         this.failMessage(sender, Component.translatable("notify.playerMarriage.disabled"));
      } else {
         PlayerSaveData senderData = PlayerSaveData.get(sender);
         PlayerSaveData receiverData = PlayerSaveData.get(receiver);
         if (senderData.isMarried()) {
            this.failMessage(sender, Component.translatable("server.alreadyMarried"));
         } else if (senderData.isEngaged()) {
            this.failMessage(sender, Component.translatable("server.alreadyEngaged"));
         } else if (receiverData.isMarried()) {
            this.failMessage(sender, Component.translatable("server.targetAlreadyMarried", new Object[]{receiver.getScoreboardName()}));
         } else if (receiverData.isEngaged()) {
            this.failMessage(sender, Component.translatable("server.targetAlreadyEngaged", new Object[]{receiver.getScoreboardName()}));
         } else if (sender == receiver) {
            this.failMessage(sender, Component.translatable("server.proposedToYourself"));
         } else if (this.hasProposalFrom(sender, receiver)) {
            this.failMessage(sender, Component.translatable("server.sentProposal", new Object[]{receiver.getScoreboardName()}));
         } else {
            this.successMessage(sender, Component.translatable("server.proposalSent", new Object[]{receiver.getScoreboardName()}));
            this.infoMessage(receiver, Component.translatable("server.proposedMarriage", new Object[]{sender.getScoreboardName()}));
            List<UUID> list = this.getProposalsFor(receiver);
            list.add(sender.getUUID());
            this.proposals.put(receiver.getUUID(), list);
         }
      }
   }

   public void engage(ServerPlayer sender, ServerPlayer receiver) {
      if (!Config.getInstance().allowPlayerMarriage) {
         this.failMessage(sender, Component.translatable("notify.playerMarriage.disabled"));
      } else if (sender == receiver) {
         this.failMessage(sender, Component.translatable("server.cannotTargetYourself"));
      } else {
         PlayerSaveData senderData = PlayerSaveData.get(sender);
         PlayerSaveData receiverData = PlayerSaveData.get(receiver);
         if (senderData.isMarried()) {
            this.failMessage(sender, Component.translatable("server.alreadyMarried"));
         } else if (receiverData.isMarried()) {
            this.failMessage(sender, Component.translatable("server.targetAlreadyMarried", new Object[]{receiver.getScoreboardName()}));
         } else if (senderData.isEngaged()) {
            this.failMessage(sender, Component.translatable("server.alreadyEngaged"));
         } else if (receiverData.isEngaged()) {
            this.failMessage(sender, Component.translatable("server.targetAlreadyEngaged", new Object[]{receiver.getScoreboardName()}));
         } else if (!this.hasUsableRing(sender, EngagementRingItem.class)) {
            this.failMessage(sender, Component.translatable("server.needEngagementRing"));
         } else {
            senderData.engage(receiver);
            receiverData.engage(sender);
            this.removeProposalFor(receiver, sender);
            this.removeProposalFor(sender, receiver);
            this.autoEquipHeldRing(sender, EngagementRingItem.class);
            this.successMessage(sender, Component.translatable("server.engaged", new Object[]{receiver.getDisplayName()}));
            this.successMessage(receiver, Component.translatable("server.engaged", new Object[]{sender.getDisplayName()}));
         }
      }
   }

   public void marry(ServerPlayer sender, ServerPlayer receiver) {
      if (!Config.getInstance().allowPlayerMarriage) {
         this.failMessage(sender, Component.translatable("notify.playerMarriage.disabled"));
      } else if (sender == receiver) {
         this.failMessage(sender, Component.translatable("server.cannotTargetYourself"));
      } else {
         PlayerSaveData senderData = PlayerSaveData.get(sender);
         PlayerSaveData receiverData = PlayerSaveData.get(receiver);
         if (senderData.isMarried()) {
            this.failMessage(sender, Component.translatable("server.alreadyMarried"));
         } else if (receiverData.isMarried()) {
            this.failMessage(sender, Component.translatable("server.targetAlreadyMarried", new Object[]{receiver.getScoreboardName()}));
         } else if (!senderData.isEngagedWith(receiver.getUUID()) || !receiverData.isEngagedWith(sender.getUUID())) {
            this.failMessage(sender, Component.translatable("server.notEngaged"));
         } else if (!this.hasUsableRing(sender, WeddingRingItem.class)) {
            this.failMessage(sender, Component.translatable("server.needWeddingRing"));
         } else {
            senderData.marry(receiver);
            receiverData.marry(sender);
            this.removeProposalFor(receiver, sender);
            this.removeProposalFor(sender, receiver);
            this.autoEquipHeldRing(sender, WeddingRingItem.class);
            this.successMessage(sender, Component.translatable("server.married", new Object[]{receiver.getDisplayName()}));
            this.successMessage(receiver, Component.translatable("server.married", new Object[]{sender.getDisplayName()}));
         }
      }
   }

   public void hug(ServerPlayer sender, ServerPlayer receiver) {
      if (sender == receiver) {
         this.failMessage(sender, Component.translatable("server.cannotTargetYourself"));
      } else if (this.isOnSocialCooldown(sender)) {
         this.failMessage(sender, Component.translatable("server.socialInteractionCooldown"));
      } else {
         PlayerSaveData senderData = PlayerSaveData.get(sender);
         PlayerSaveData receiverData = PlayerSaveData.get(receiver);
         boolean engaged = senderData.isEngagedWith(receiver.getUUID()) && receiverData.isEngagedWith(sender.getUUID());
         boolean married = senderData.isMarriedTo(receiver.getUUID()) && receiverData.isMarriedTo(sender.getUUID());
         if (!engaged && !married) {
            this.failMessage(sender, Component.translatable("server.hug.requiresPartner"));
         } else {
            sender.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
            receiver.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
            sender.giveExperiencePoints(2);
            receiver.giveExperiencePoints(2);
            this.playAffectionAnimation(sender, receiver, "hug", 18);
            this.successMessage(sender, Component.translatable("server.hug.success", new Object[]{receiver.getDisplayName()}));
            this.successMessage(receiver, Component.translatable("server.hug.received", new Object[]{sender.getDisplayName()}));
         }
      }
   }

   public void kiss(ServerPlayer sender, ServerPlayer receiver) {
      if (sender == receiver) {
         this.failMessage(sender, Component.translatable("server.cannotTargetYourself"));
      } else if (this.isOnSocialCooldown(sender)) {
         this.failMessage(sender, Component.translatable("server.socialInteractionCooldown"));
      } else {
         PlayerSaveData senderData = PlayerSaveData.get(sender);
         PlayerSaveData receiverData = PlayerSaveData.get(receiver);
         if (senderData.isMarriedTo(receiver.getUUID()) && receiverData.isMarriedTo(sender.getUUID())) {
            ServerInteractionManager.FoodShare sharedFood = this.shareHeldFoodForKiss(sender, receiver);
            int strengthBonus = sharedFood.strengthBonus();
            int regenTicks = 60 + strengthBonus * 20;
            int sharedXp = 2 + strengthBonus;
            int durationTicks = 14 + strengthBonus * 2;
            float animationStrength = 1.0F + strengthBonus * 0.18F;
            sender.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenTicks, 0, true, false, true));
            receiver.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenTicks, 0, true, false, true));
            sender.giveExperiencePoints(sharedXp);
            receiver.giveExperiencePoints(sharedXp);
            this.playAffectionAnimation(sender, receiver, "kiss", durationTicks, animationStrength);
            this.successMessage(sender, Component.translatable("server.kiss.success", new Object[]{receiver.getDisplayName()}));
            this.successMessage(receiver, Component.translatable("server.kiss.received", new Object[]{sender.getDisplayName()}));
         } else {
            this.failMessage(sender, Component.translatable("server.kiss.requiresSpouse"));
         }
      }
   }

   public void rejectProposal(ServerPlayer sender, ServerPlayer receiver) {
      if (!this.hasProposalFrom(receiver, sender)) {
         this.failMessage(sender, Component.translatable("server.noProposal", new Object[]{receiver.getDisplayName()}));
      } else {
         this.successMessage(sender, Component.translatable("server.proposalRejectionSent"));
         this.failMessage(receiver, Component.translatable("server.proposalRejected", new Object[]{sender.getScoreboardName()}));
         this.removeProposalFor(sender, receiver);
      }
   }

   public void acceptProposal(ServerPlayer sender, ServerPlayer receiver) {
      if (!this.hasProposalFrom(receiver, sender)) {
         this.failMessage(sender, Component.translatable("server.noProposal", new Object[]{receiver.getDisplayName()}));
      } else {
         this.successMessage(receiver, Component.translatable("server.proposalAccepted", new Object[]{sender.getDisplayName()}));
         PlayerSaveData.get(sender).marry(receiver);
         PlayerSaveData.get(receiver).marry(sender);
         this.successMessage(sender, Component.translatable("server.married", new Object[]{receiver.getDisplayName()}));
         this.successMessage(receiver, Component.translatable("server.married", new Object[]{sender.getDisplayName()}));
         this.removeProposalFor(sender, receiver);
      }
   }

   public void endMarriage(ServerPlayer sender) {
      PlayerSaveData senderData = PlayerSaveData.get(sender);
      if (!senderData.isMarried()) {
         this.failMessage(sender, Component.translatable("server.endMarriageNotMarried"));
      } else if (senderData.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
         this.failMessage(sender, Component.translatable("server.marriedToVillager"));
      } else {
         UUID partnerId = senderData.getPartnerUUID().orElse(null);
         senderData.getPartnerName()
            .ifPresent(name -> this.successMessage(sender, Component.translatable("server.endMarriage", new Object[]{name.getString()})));
         ServerPlayer spouse = (partnerId == null ? null : sender.level().getPlayerByUUID(partnerId)) instanceof ServerPlayer serverPlayer
            ? serverPlayer
            : null;
         if (spouse != null) {
            this.failMessage(spouse, Component.translatable("server.marriageEnded", new Object[]{sender.getScoreboardName()}));
            PlayerSaveData.get(spouse).endRelationShip(RelationshipState.SINGLE);
            PlayerSaveData.sync(spouse);
         } else if (partnerId != null) {
            ServerLevel var7 = sender.level();
            if (var7 instanceof ServerLevel) {
               PlayerSaveData.getIfPresent(var7, partnerId).ifPresent(partnerData -> partnerData.endRelationShip(RelationshipState.SINGLE));
            }
         }

         senderData.endRelationShip(RelationshipState.SINGLE);
         PlayerSaveData.sync(sender);
      }
   }

   public void procreate(ServerPlayer sender) {
      PlayerSaveData senderData = PlayerSaveData.get(sender);
      if (!senderData.isMarried()) {
         this.failMessage(sender, Component.translatable("server.notMarried"));
      } else if (senderData.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
         this.failMessage(sender, Component.translatable("server.marriedToVillager"));
      } else {
         senderData.getPartner().filter(e -> e instanceof Player).map(Player.class::cast).ifPresentOrElse(spouse -> {
            if (!this.procreateMap.containsKey(spouse.getUUID())) {
               this.procreateMap.put(sender.getUUID(), System.currentTimeMillis() + 10000L);
               this.infoMessage(spouse, Component.translatable("server.procreationRequest", new Object[]{sender.getScoreboardName()}));
            } else {
               this.successMessage(sender, Component.translatable("server.procreationSuccessful"));
               this.successMessage(spouse, Component.translatable("server.procreationSuccessful"));
               spouse.addItem(BabyItem.createItem(spouse, sender, spouse.getRandom().nextLong()));
            }
         }, () -> this.failMessage(sender, Component.translatable("server.spouseNotPresent")));
      }
   }

   public void procreate(ServerPlayer sender, ServerPlayer receiver) {
      PlayerSaveData senderData = PlayerSaveData.get(sender);
      if (senderData.getPartnerUUID().filter(receiver.getUUID()::equals).isEmpty()) {
         this.failMessage(sender, Component.translatable("server.spouseNotPresent"));
      } else {
         this.procreate(sender);
      }
   }

   private void successMessage(Player player, MutableComponent message) {
      player.displayClientMessage(message.withStyle(ChatFormatting.GREEN), false);
   }

   private void failMessage(Player player, MutableComponent message) {
      player.displayClientMessage(message.withStyle(ChatFormatting.RED), false);
   }

   private void infoMessage(Player player, MutableComponent message) {
      player.displayClientMessage(message.withStyle(ChatFormatting.YELLOW), false);
   }

   private record FoodShare(int nutrition, float saturationModifier) {
      private static final ServerInteractionManager.FoodShare NONE = new ServerInteractionManager.FoodShare(0, 0.0F);

      private int strengthBonus() {
         return this.nutrition <= 0 ? 0 : Math.min(6, Math.max(1, this.nutrition / 2 + Mth.ceil(this.saturationModifier * 2.0F)));
      }
   }
}
