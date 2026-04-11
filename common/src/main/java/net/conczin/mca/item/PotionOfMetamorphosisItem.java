package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class PotionOfMetamorphosisItem extends TooltippedItem {
   private final Gender gender;

   public PotionOfMetamorphosisItem(Properties properties, Gender gender) {
      super(properties);
      this.gender = gender;
   }

   public final InteractionResult use(Level world, Player player, InteractionHand hand) {
      if (player instanceof ServerPlayer serverPlayer) {
         PlayerSaveData data = PlayerSaveData.get(serverPlayer);
         CompoundTag villagerData = data.getEntityData();
         villagerData.putInt("Gender", this.gender.ordinal());
         data.setEntityDataSet(true);
         data.setEntityData(villagerData);
         this.common(serverPlayer);
         PlayerSaveData.sync(serverPlayer);
         ItemStack stack = player.getItemInHand(hand);
         stack.shrink(1);
         return InteractionResult.SUCCESS.heldItemTransformedTo(stack);
      } else {
         return super.use(world, player, hand);
      }
   }

   public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
      if (entity instanceof VillagerLike<?> villager && !entity.level().isClientSide()) {
         villager.getGenetics().setGender(this.gender);
         this.common(entity);
         stack.shrink(1);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.CONSUME;
      }
   }

   private void common(Entity entity) {
      entity.playSound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 1.0F, 1.0F);
      FamilyTree tree = FamilyTree.get((ServerLevel)entity.level());
      FamilyTreeNode entry = tree.getOrCreate(entity);
      entry.setGender(this.gender);
   }
}
