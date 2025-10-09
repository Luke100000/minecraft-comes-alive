package net.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.TaskUtils;
import net.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Comparator;
import java.util.List;

public class FishingTask extends AbstractChoreTask {

    private BlockPos targetWater;
    private boolean hasCastRod;
    private int ticks;
    private List<ItemStack> list;

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
        if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof FishingRodItem);
            if (i == -1) {
                abandonJobWithMessage("chore.fishing.norod");
            } else {
                villager.setItemInHand(villager.getDominantHand(), villager.getInventory().getItem(i));
            }
        }

        LootTable loottable = world.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        LootParams.Builder lootcontext$builder = (new LootParams.Builder(world)).withParameter(LootContextParams.ORIGIN, villager.position()).withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD)).withParameter(LootContextParams.THIS_ENTITY, villager).withLuck(0F);
        this.list = loottable.getRandomItems(lootcontext$builder.create(LootContextParamSets.FISHING));
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.tick(world, villager, time);

        if (!InventoryUtils.contains(villager.getInventory(), FishingRodItem.class) && !villager.hasItemInSlot(villager.getDominantSlot())) {
            abandonJobWithMessage("chore.fishing.norod");
        } else if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof FishingRodItem);
            ItemStack stack = villager.getInventory().getItem(i);
            villager.setItemInHand(villager.getDominantHand(), stack);
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

            if (ticks >= villager.level().random.nextInt(200) + 200) {
                if (villager.level().random.nextFloat() >= 0.35F) {
                    ItemStack stack = list.get(villager.getRandom().nextInt(list.size())).copy();

                    villager.swing(villager.getDominantHand());
                    villager.getInventory().addItem(stack);
                    villager.getMainHandItem().hurtAndBreak(1, villager, e -> e.broadcastBreakEvent(e.getDominantSlot()));
                }
                ticks = 0;
            }
        } else {
            villager.moveTowards(targetWater);
        }

    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        ItemStack stack = villager.getItemInHand(villager.getDominantHand());
        if (!stack.isEmpty()) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        }
    }
}
