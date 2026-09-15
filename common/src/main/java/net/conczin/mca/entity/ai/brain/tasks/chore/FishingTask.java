package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
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
    private static final int MAX_REEL_TICKS = 40;
    private static final double REEL_DELIVERY_DISTANCE_SQR = 1.5 * 1.5;

    private BlockPos targetWater;
    private MCAFishingBobberEntity bobber;
    private ItemEntity reelItem;
    private int reelTicks;
    private boolean biteAttempted;

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
    protected boolean timedOut(long time) {
        return false;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);
        equipFishingRod(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.tick(world, villager, time);

        if (tickReelItem(villager)) {
            return;
        }

        if (!equipFishingRod(villager)) {
            discardBobber();
            return;
        }

        if (targetWater != null && !world.getBlockState(targetWater).is(Blocks.WATER)) {
            discardBobber();
            targetWater = null;
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

            if (bobber == null || bobber.isRemoved()) {
                ItemStack fishingRod = villager.getItemInHand(villager.getDominantHand());
                villager.swing(villager.getDominantHand(), fishingRod.getInteractAnimation());
                bobber = MCAFishingBobberEntity.cast(world, villager, targetWater);
            }

            if (!bobber.isBobbing()) {
                return;
            }

            if (!bobber.isBiting()) {
                biteAttempted = false;
            } else if (!biteAttempted) {
                biteAttempted = true;
                if (shouldReelBite(villager)) {
                    beginReel(world, villager);
                }
            }
        } else {
            villager.moveTowards(targetWater);
        }

    }

    boolean shouldReelBite(VillagerEntityMCA villager) {
        return villager.getRandom().nextFloat() >= 0.35F;
    }

    private void beginReel(ServerLevel world, VillagerEntityMCA villager) {
        ItemStack caught = getFishingLoot(world, villager);
        ItemStack fishingRod = villager.getItemInHand(villager.getDominantHand());
        villager.swing(villager.getDominantHand(), fishingRod.getInteractAnimation());

        ItemEntity item = new ItemEntity(world, bobber.getX(), bobber.getY(), bobber.getZ(), caught);
        item.setThrower(villager);
        item.setTarget(villager.getUUID());
        item.setNeverPickUp();

        double dx = villager.getX() - bobber.getX();
        double dy = villager.getY() - bobber.getY();
        double dz = villager.getZ() - bobber.getZ();
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        item.setDeltaMovement(
                dx * 0.1,
                dy * 0.1 + Math.sqrt(Math.sqrt(distanceSqr)) * 0.08,
                dz * 0.1
        );

        world.addFreshEntity(item);
        reelItem = item;
        reelTicks = 0;

        discardBobber();
        villager.getItemInHand(villager.getDominantHand())
                .hurtAndBreak(1, villager, villager.getDominantSlot());
    }

    private boolean tickReelItem(VillagerEntityMCA villager) {
        if (reelItem == null) {
            return false;
        }
        if (reelItem.isRemoved()) {
            clearReelReference();
            return false;
        }

        reelTicks++;
        if (reelItem.distanceToSqr(villager) <= REEL_DELIVERY_DISTANCE_SQR || reelTicks >= MAX_REEL_TICKS) {
            finishReelItem(villager);
        }
        return true;
    }

    private void finishReelItem(VillagerEntityMCA villager) {
        if (reelItem == null || reelItem.isRemoved()) {
            clearReelReference();
            return;
        }

        ItemStack remainder = villager.getInventory().addItem(reelItem.getItem());
        if (remainder.isEmpty()) {
            reelItem.discard();
        } else {
            reelItem.setItem(remainder);
            releasePickupProtection(reelItem);
        }
        clearReelReference();
    }

    private void releaseReelItem() {
        if (reelItem != null && !reelItem.isRemoved()) {
            releasePickupProtection(reelItem);
        }
        clearReelReference();
    }

    private static void releasePickupProtection(ItemEntity item) {
        item.setTarget(null);
        item.setNoPickUpDelay();
    }

    private void clearReelReference() {
        reelItem = null;
        reelTicks = 0;
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

    private void discardBobber() {
        if (bobber != null && !bobber.isRemoved()) {
            bobber.discard();
        }
        bobber = null;
        biteAttempted = false;
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (reelItem != null) {
            if (villager.isAlive() && !villager.isRemoved()) {
                finishReelItem(villager);
            } else {
                releaseReelItem();
            }
        }

        discardBobber();
        targetWater = null;
        clearChoreItem(villager);
    }
}
