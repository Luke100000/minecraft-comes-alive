package net.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.TaskUtils;
import net.mca.util.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;

public class FishingTask extends AbstractChoreTask {

    private BlockPos targetWater;
    private boolean hasCastRod;
    private int ticks;
    private List<ItemStack> list;

    public FishingTask() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryModuleState.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT));

    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getCurrentJob() == Chore.FISH && super.shouldRun(world, villager);
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        return shouldRun(world, villager);
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.run(world, villager, time);
        if (!villager.hasStackEquipped(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof FishingRodItem);
            if (i == -1) {
                abandonJobWithMessage("chore.fishing.norod");
            } else {
                villager.setStackInHand(villager.getDominantHand(), villager.getInventory().getStack(i));
            }
        }

        LootTable loottable = world.getServer().getLootManager().getLootTable(LootTables.FISHING_GAMEPLAY);
        LootContextParameterSet.Builder lootcontext$builder = (new LootContextParameterSet.Builder(world)).add(LootContextParameters.ORIGIN, villager.getPos()).add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD)).add(LootContextParameters.THIS_ENTITY, villager).luck(0F);
        this.list = loottable.generateLoot(lootcontext$builder.build(LootContextTypes.FISHING));
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.keepRunning(world, villager, time);

        if (!InventoryUtils.contains(villager.getInventory(), FishingRodItem.class) && !villager.hasStackEquipped(villager.getDominantSlot())) {
            abandonJobWithMessage("chore.fishing.norod");
        } else if (!villager.hasStackEquipped(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof FishingRodItem);
            ItemStack stack = villager.getInventory().getStack(i);
            villager.setStackInHand(villager.getDominantHand(), stack);
        }

        if (targetWater == null) {
            List<BlockPos> nearbyStaticLiquid = TaskUtils.getNearbyBlocks(villager.getBlockPos(), villager.getWorld(), blockState -> blockState.isOf(Blocks.WATER), 12, 3);
            targetWater = nearbyStaticLiquid.stream()
                    .filter((p) -> villager.getWorld().getBlockState(p).getBlock() == Blocks.WATER)
                    .min(Comparator.comparingDouble(d -> villager.squaredDistanceTo(d.getX(), d.getY(), d.getZ()))).orElse(null);

            if (targetWater == null) {
                failedTicks = FAILED_COOLDOWN;
            }
        } else if (villager.squaredDistanceTo(targetWater.getX(), targetWater.getY(), targetWater.getZ()) < 5.0D) {
            villager.getNavigation().stop();
            villager.lookAt(targetWater);

            if (!hasCastRod) {
                villager.swingHand(villager.getDominantHand());
                hasCastRod = true;
            }

            ticks++;

            if (ticks >= villager.getWorld().random.nextInt(200) + 200) {
                if (villager.getWorld().random.nextFloat() >= 0.35F) {
                    ItemStack stack = list.get(villager.getRandom().nextInt(list.size())).copy();

                    villager.swingHand(villager.getDominantHand());
                    villager.getInventory().addStack(stack);
                    villager.getMainHandStack().damage(1, villager, e -> e.sendEquipmentBreakStatus(e.getDominantSlot()));
                }
                ticks = 0;
            }
        } else {
            villager.moveTowards(targetWater);
        }

    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        ItemStack stack = villager.getStackInHand(villager.getDominantHand());
        if (!stack.isEmpty()) {
            villager.setStackInHand(villager.getDominantHand(), ItemStack.EMPTY);
        }
    }
}
