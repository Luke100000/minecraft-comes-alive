package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
    private static final int MIN_BITE_REACTION_TICKS = 5;
    private static final int MAX_BITE_REACTION_TICKS = 12;
    private static final double REEL_DELIVERY_DISTANCE_SQR = 0.5 * 0.5;

    private BlockPos targetWater;
    private MCAFishingBobberEntity bobber;
    private ItemEntity reelItem;
    private int reelTicks;
    private boolean biteAttempted;
    private int biteReactionTicksRemaining = -1;

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
        if (!villager.isUsingRecoveryFood()) {
            equipFishingRod(villager);
        }
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.tick(world, villager, time);

        if (tickReelItem(villager)) {
            return;
        }

        if (villager.isUsingRecoveryFood()) {
            if (bobber != null) {
                MCA.LOGGER.info(
                        "[MCA Fishing Debug] recovery-pause villager={} bobber={} held={} inventoryRods={}",
                        villager.getUUID(), bobber.getId(), villager.getItemInHand(villager.getDominantHand()),
                        villager.getInventory().countItem(Items.FISHING_ROD)
                );
            }
            discardBobber();
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
                villager.swing(villager.getDominantHand());
                bobber = MCAFishingBobberEntity.cast(world, villager, targetWater);
            }

            if (!bobber.isBobbing()) {
                return;
            }

            if (!bobber.isBiting()) {
                biteAttempted = false;
                biteReactionTicksRemaining = -1;
            } else if (!biteAttempted) {
                if (biteReactionTicksRemaining < 0) {
                    biteReactionTicksRemaining = Mth.nextInt(
                            villager.getRandom(),
                            MIN_BITE_REACTION_TICKS,
                            MAX_BITE_REACTION_TICKS
                    );
                    MCA.LOGGER.info(
                            "[MCA Fishing Debug] bite-reaction-start villager={} bobber={} delayTicks={}",
                            villager.getUUID(), bobber.getId(), biteReactionTicksRemaining
                    );
                }

                if (biteReactionTicksRemaining-- > 0) {
                    return;
                }

                biteAttempted = true;
                boolean catchSucceeded = shouldReelBite(villager);
                MCA.LOGGER.info(
                        "[MCA Fishing Debug] bite-observed villager={} bobber={} result={} held={} inventoryRods={}",
                        villager.getUUID(), bobber.getId(), catchSucceeded ? "CATCH" : "MISS",
                        villager.getItemInHand(villager.getDominantHand()), villager.getInventory().countItem(Items.FISHING_ROD)
                );
                if (catchSucceeded) {
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
        villager.swing(villager.getDominantHand());

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
        MCA.LOGGER.info(
                "[MCA Fishing Debug] reel-start villager={} bobber={} item={} itemEntity={} inventoryRods={}",
                villager.getUUID(), bobber.getId(), caught, item.getId(), villager.getInventory().countItem(Items.FISHING_ROD)
        );

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
        Vec3 reelTarget = new Vec3(
                villager.getX(),
                villager.getY() + villager.getEyeHeight() / 2.0,
                villager.getZ()
        );
        Vec3 attraction = reelTarget.subtract(reelItem.position());
        double distanceSqr = attraction.lengthSqr();
        if (distanceSqr < 64.0) {
            double strength = 1.0 - Math.sqrt(distanceSqr) / 8.0;
            reelItem.setDeltaMovement(
                    reelItem.getDeltaMovement().add(attraction.normalize().scale(strength * strength * 0.1))
            );
            reelItem.hasImpulse = true;
        }

        if (distanceSqr <= REEL_DELIVERY_DISTANCE_SQR || reelTicks >= MAX_REEL_TICKS) {
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
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] reel-delivered villager={} itemEntity={} inventoryRods={}",
                    villager.getUUID(), reelItem.getId(), villager.getInventory().countItem(Items.FISHING_ROD)
            );
            reelItem.discard();
        } else {
            reelItem.setItem(remainder);
            releasePickupProtection(reelItem);
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] reel-remainder villager={} itemEntity={} remainder={} inventoryRods={}",
                    villager.getUUID(), reelItem.getId(), remainder, villager.getInventory().countItem(Items.FISHING_ROD)
            );
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
        MCA.LOGGER.info(
                "[MCA Fishing Debug] rod-equipped villager={} slot={} held={} inventoryRods={}",
                villager.getUUID(), slot, villager.getItemInHand(villager.getDominantHand()),
                villager.getInventory().countItem(Items.FISHING_ROD)
        );
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
                .withLuck(EnchantmentHelper.getFishingLuckBonus(world, fishingRod, villager));
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
        biteReactionTicksRemaining = -1;
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

        if (villager.isUsingRecoveryFood()) {
            villager.stopUsingItem();
        }

        ItemStack stack = villager.getItemInHand(villager.getDominantHand());
        if (stack.getItem() instanceof FishingRodItem) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        }
    }
}
