package net.mca.item;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class PotionOfMetamorphosisItem extends TooltippedItem {
    private final Gender gender;

    public PotionOfMetamorphosisItem(Settings properties, Gender gender) {
        super(properties);
        this.gender = gender;
    }

    @Override
    public final TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            // set gender
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            NbtCompound villagerData = data.getEntityData();
            NbtCompound mcaData = villagerData.contains(VillagerEntityMCA.MCA_DATA_KEY, NbtElement.COMPOUND_TYPE)
                    ? villagerData.getCompound(VillagerEntityMCA.MCA_DATA_KEY)
                    : villagerData;
            mcaData.putInt("Gender", gender.ordinal());
            data.setEntityData(villagerData);

            common(serverPlayer);

            // also update players
            serverPlayer.getServerWorld().getPlayers().forEach(p -> NetworkHandler.sendToPlayer(new PlayerDataMessage(player.getUuid(), villagerData), p));

            // remove item
            ItemStack stack = player.getStackInHand(hand);
            stack.decrement(1);
            return TypedActionResult.success(stack);
        }
        return super.use(world, player, hand);
    }

    public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, LivingEntity entity, Hand hand) {
        if (entity instanceof VillagerLike<?> villager && !entity.getWorld().isClient) {
            villager.getGenetics().setGender(gender);

            common(entity);

            stack.decrement(1);
            return ActionResult.SUCCESS;
        } else {
            return ActionResult.CONSUME;
        }
    }

    private void common(Entity entity) {
        // sound
        entity.playSound(SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 1.0f);

        // update family tree
        FamilyTree tree = FamilyTree.get((ServerWorld)entity.getWorld());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(gender);
    }
}
