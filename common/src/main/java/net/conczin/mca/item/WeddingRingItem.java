package net.conczin.mca.item;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class WeddingRingItem extends RelationshipItem {
   public WeddingRingItem(Properties properties) {
      super(properties);
   }

   @Override
   protected int getHeartsRequired() {
      return Config.getInstance().marriageHeartsRequirement;
   }

   @Override
   public boolean handle(ServerPlayer player, VillagerEntityMCA villager) {
      PlayerSaveData playerData = PlayerSaveData.get(player);
      if (super.handle(player, villager)) {
         return false;
      } else {
         String response = "interaction.marry.success";
         playerData.marry(villager);
         villager.getRelationships().marry(player);
         villager.getVillagerBrain().modifyMoodValue(15);
         villager.sendChatMessage(player, response);
         return true;
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
