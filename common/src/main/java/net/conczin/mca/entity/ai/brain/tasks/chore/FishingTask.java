package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class FishingTask extends AbstractChoreTask {

    private BlockPos targetWater;
    private boolean hasCastRod;
    private int ticks;

    public FishingTask() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));

    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getCurrentJob() == Chore.FISH && super.checkExtraStartConditions(world, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return checkExtraStartConditions(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);
        equipFishingRod(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.tick(world, villager, time);

        if (!equipFishingRod(villager)) {
            return;
        }

        if (targetWater == null) {
            List<BlockPos> nearbyStaticLiquid = TaskUtils.getNearbyBlocks(villager.blockPosition(), villager.level(), blockState -> blockState.is(Blocks.WATER), 12, 3);
            targetWater = nearbyStaticLiquid.stream()
                    .filter((p) -> villager.level().getBlockState(p).getBlock() == Blocks.WATER)
                    .min(Comparator.comparingDouble(d -> villager.distanceToSqr(d.getX(), d.getY(), d.getZ()))).orElse(null);

            if (targetWater == null) {
                failedTicks = FAILED_COOLDOWN;
            }
        } else if (villager.distanceToSqr(targetWater.getX(), targetWater.getY(), targetWater.getZ()) < 5.0D) {
            villager.getNavigation().stop();
            villager.lookAt(targetWater);

            if (!hasCastRod) {
                villager.swing(villager.getDominantHand());
                hasCastRod = true;
            }

            ticks++;

            if (ticks >= villager.getRandom().nextInt(200) + 200) {
                if (villager.getRandom().nextFloat() >= 0.35F) {
                    ItemStack stack = getFishingLoot(world, villager);

                    villager.swing(villager.getDominantHand());
                    villager.getInventory().addItem(stack);
                    villager.getItemInHand(villager.getDominantHand()).hurtAndBreak(1, villager, villager.getDominantSlot());
                }
                ticks = 0;
            }
        } else {
            villager.moveTowards(targetWater);
        }

    }

    private boolean equipFishingRod(VillagerEntityMCA villager) {
        ItemStack heldStack = villager.getItemInHand(villager.getDominantHand());
        if (heldStack.getItem() instanceof FishingRodItem) {
            return true;
        }

        int slot = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof FishingRodItem);
        if (slot == -1) {
            abandonJobWithMessage("chore.fishing.norod");
            return false;
        }

        villager.setItemInHand(villager.getDominantHand(), villager.getInventory().getItem(slot));
        return true;
    }

    private ItemStack getFishingLoot(ServerLevel world, VillagerEntityMCA villager) {
        LootTable lootTable = world.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        Vec3 origin = Vec3.atCenterOf(targetWater);
        ItemStack fishingRod = villager.getItemInHand(villager.getDominantHand());
        LootParams.Builder builder = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .withLuck(0F);
        List<ItemStack> loot = lootTable.getRandomItems(builder.create(LootContextParamSets.FISHING));

        if (loot.isEmpty()) {
            return new ItemStack(Items.COD);
        }

        return loot.get(villager.getRandom().nextInt(loot.size())).copy();
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        clearChoreItem(villager);
    }
}
