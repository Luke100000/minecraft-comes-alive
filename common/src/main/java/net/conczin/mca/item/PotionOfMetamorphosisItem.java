package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
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
import net.minecraft.world.level.Level;

public class PotionOfMetamorphosisItem extends TooltippedItem {
    private final Gender gender;

    public PotionOfMetamorphosisItem(Properties properties, Gender gender) {
        super(properties);
        this.gender = gender;
    }

    @Override
    public final InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            // set gender
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            CompoundTag villagerData = data.getEntityData();
            villagerData.putInt("Gender", gender.ordinal());
            data.setEntityData(villagerData);

            common(serverPlayer);

            // also update players
            serverPlayer.level().players().forEach(p -> Network.sendToPlayer(new PlayerDataMessage(player.getUUID(), villagerData), p));

            // remove item
            ItemStack stack = player.getItemInHand(hand);
            stack.shrink(1);
            return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(stack);
        }
        return super.use(world, player, hand);
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof VillagerLike<?> villager && !entity.level().isClientSide()) {
            villager.getGenetics().setGender(gender);

            common(entity);

            stack.shrink(1);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }

    private void common(Entity entity) {
        // sound
        entity.playSound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 1.0f, 1.0f);

        // update family tree
        FamilyTree tree = FamilyTree.get((ServerLevel) entity.level());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(gender);
    }
}

