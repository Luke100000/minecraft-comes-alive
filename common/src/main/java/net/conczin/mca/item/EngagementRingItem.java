package net.conczin.mca.item;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class EngagementRingItem extends RelationshipItem {
   public EngagementRingItem(Properties properties) {
      super(properties);
   }

   @Override
   protected int getHeartsRequired() {
      return Config.getInstance().engagementHeartsRequirement;
   }

   @Override
   public boolean handle(ServerPlayer player, VillagerEntityMCA villager) {
      PlayerSaveData playerData = PlayerSaveData.get(player);
      boolean consume = false;
      if (super.handle(player, villager)) {
         return false;
      } else {
         String response;
         if (Relationship.IS_ENGAGED.test((CompassionateEntity<?>)villager, (Entity)player)) {
            response = "interaction.engage.fail.engaged";
         } else {
            response = "interaction.engage.success";
            playerData.engage(villager);
            villager.getRelationships().engage(player);
            villager.getVillagerBrain().modifyMoodValue(10);
            consume = true;
         }

         villager.sendChatMessage(player, response);
         return consume;
      }
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (hand != InteractionHand.MAIN_HAND || stack.isEmpty()) {
         return InteractionResult.PASS;
      } else if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (player instanceof ServerPlayer serverPlayer) {
         equipRing(serverPlayer, stack);
         return InteractionResult.SUCCESS_SERVER;
      } else {
         return InteractionResult.PASS;
      }
   }
}
