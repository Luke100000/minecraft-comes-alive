package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class FishingTask extends AbstractChoreTask {
    private static final int MAX_REEL_TICKS = 40;
    private static final int MIN_BITE_REACTION_TICKS = 5;
    private static final int MAX_BITE_REACTION_TICKS = 12;
    private static final double REEL_DELIVERY_DISTANCE_SQR = 0.5 * 0.5;

    private final FishingSiteSearch siteSearch = new FishingSiteSearch();
    private FishingSiteSearch.Site currentSite;
    private BlockPos previousCastWater;
    private WalkTarget fishingWalkTarget;
    private MCAFishingBobberEntity castBobber;
    private boolean castReachedWater;
    private long lastProgress;
    private long approachStarted;
    private Vec3 previousApproachPosition;
    private long nextSiteCheck;
    private final List<ItemEntity> reelItems = new ArrayList<>();
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
        siteSearch.reset();
        currentSite = null;
        previousCastWater = null;
        resetApproach(time);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.tick(world, villager, time);

        if (failedTicks > 0 || villager.getVillagerBrain().getCurrentJob() != Chore.FISH) {
            return;
        }

        if (tickReelItems(villager)) {
            return;
        }

        MCAFishingBobberEntity bobber = villager.getFishingBobber();

        if (villager.isUsingRecoveryFood()) {
            if (bobber != null) {
                MCA.LOGGER.info(
                        "[MCA Fishing Debug] recovery-pause villager={} bobber={} held={} inventoryRods={}",
                        villager.getUUID(), bobber.getId(), villager.getItemInHand(villager.getDominantHand()),
                        villager.getInventory().countItem(Items.FISHING_ROD)
                );
            }
            discardBobber(villager);
            clearFishingMovement(villager);
            resetApproach(time);
            return;
        }

        if (!hasFishingRod(villager)) {
            abandonJobWithMessage("chore.fishing.norod");
            discardBobber(villager);
            return;
        }

        if (castBobber != null) {
            if (castBobber.isBobbing()) castReachedWater = true;
            if (bobber != castBobber || castBobber.isRemoved()) {
                boolean failedFlight = !castReachedWater;
                castBobber = null;
                castReachedWater = false;
                if (failedFlight) {
                    rejectSite(villager);
                    return;
                }
            }
        }

        if (currentSite == null) {
            // Discovery path probes mutate navigation's cached target. Yield before
            // probing when another behaviour has acquired movement during the scan.
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                failedTicks = FAILED_COOLDOWN;
                return;
            }
            currentSite = siteSearch.tick(world, villager);
            if (currentSite == null) {
                if (siteSearch.exhausted()) failedTicks = FAILED_COOLDOWN;
                return;
            }
            resetApproach(time);
            nextSiteCheck = time;
        }

        if (time >= nextSiteCheck || bobber == null) {
            if (!FishingSiteSearch.isUsable(world, villager, currentSite)) {
                rejectSite(villager);
                return;
            }
            nextSiteCheck = time + 10;
        }

        BlockPos standingPos = currentSite.standingPos();
        Vec3 delta = villager.position().subtract(Vec3.atBottomCenterOf(standingPos));
        // The arrival radius alone can include an adjacent block whose dry
        // footprint was never checked, especially for a two-block cast.
        if (delta.horizontalDistanceSqr() > 0.64 || Math.abs(delta.y) > 0.5
                || villager.getBlockX() != standingPos.getX() || villager.getBlockZ() != standingPos.getZ()) {
            if (bobber != null) discardBobber(villager);
            // A valid path can lead away from the destination around an obstacle.
            // Only an actual lack of motion should trip the approach watchdog.
            Vec3 position = villager.position();
            if (previousApproachPosition == null || position.distanceToSqr(previousApproachPosition) > 0.04) {
                previousApproachPosition = position;
                lastProgress = time;
            }
            // Bound an oscillating but moving route as well as a true stall.
            if (time - lastProgress >= 60 || time - approachStarted >= 1200) {
                rejectSite(villager);
                return;
            }
            WalkTarget current = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if (current != null && current != fishingWalkTarget) {
                // A different producer now owns movement; let normal chore cleanup run.
                failedTicks = FAILED_COOLDOWN;
                return;
            }
            villager.moveTowardsPersistent(currentSite.standingPos(), 0.5F, 0);
            fishingWalkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            return;
        }

        clearFishingMovement(villager);
        if (bobber == null || bobber.isRemoved()) {
            FishingSiteSearch.Site castSite = siteSearch.chooseCastSite(world, villager, currentSite, previousCastWater);
            if (castSite == null) {
                rejectSite(villager);
                return;
            }
            currentSite = castSite;
        }
        villager.lookAt(currentSite.waterPos());
        if (!equipFishingRod(villager)) return;

        if (bobber == null || bobber.isRemoved()) {
            villager.swing(villager.getDominantHand());
            bobber = MCAFishingBobberEntity.cast(world, villager, currentSite.waterPos());
            previousCastWater = currentSite.waterPos();
            castBobber = bobber;
            castReachedWater = false;
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
    }

    private boolean hasFishingRod(VillagerEntityMCA villager) {
        return Chore.FISH.matchesTool(villager.getItemInHand(villager.getDominantHand()))
                || InventoryUtils.contains(villager.getInventory(), Chore.FISH::matchesTool);
    }

    private void resetApproach(long time) {
        lastProgress = time;
        approachStarted = time;
        previousApproachPosition = null;
    }

    private void rejectSite(VillagerEntityMCA villager) {
        discardBobber(villager);
        clearFishingMovement(villager);
        if (currentSite != null) siteSearch.reject(currentSite);
        currentSite = null;
    }

    private void clearFishingMovement(VillagerEntityMCA villager) {
        WalkTarget current = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (fishingWalkTarget != null && (current == fishingWalkTarget || current == null)) {
            if (current == fishingWalkTarget) villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
        }
        fishingWalkTarget = null;
    }

    boolean shouldReelBite(VillagerEntityMCA villager) {
        return villager.getRandom().nextFloat() >= 0.35F;
    }

    private void beginReel(ServerLevel world, VillagerEntityMCA villager) {
        MCAFishingBobberEntity bobber = villager.getFishingBobber();
        if (bobber == null || bobber.isRemoved()) {
            return;
        }

        List<ItemStack> caught = getFishingLoot(world, villager);
        villager.swing(villager.getDominantHand());
        world.playSound(
                null,
                villager.getX(),
                villager.getY(),
                villager.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL,
                1.0F,
                0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F)
        );
        villager.gameEvent(GameEvent.ITEM_INTERACT_FINISH);

        Vec3 reelOrigin = bobber.position();
        double dx = villager.getX() - bobber.getX();
        double dy = villager.getY() - bobber.getY();
        double dz = villager.getZ() - bobber.getZ();
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        for (ItemStack stack : caught) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemEntity item = new ItemEntity(world, reelOrigin.x, reelOrigin.y, reelOrigin.z, stack);
            item.setThrower(villager);
            item.setTarget(villager.getUUID());
            item.setNeverPickUp();
            item.setDeltaMovement(
                    dx * 0.1,
                    dy * 0.1 + Math.sqrt(Math.sqrt(distanceSqr)) * 0.08,
                    dz * 0.1
            );
            world.addFreshEntity(item);
            reelItems.add(item);
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] reel-start villager={} bobber={} item={} itemEntity={} inventoryRods={}",
                    villager.getUUID(), bobber.getId(), stack, item.getId(), villager.getInventory().countItem(Items.FISHING_ROD)
            );
        }
        reelTicks = 0;

        discardBobber(villager);
        villager.getItemInHand(villager.getDominantHand())
                .hurtAndBreak(1, villager, villager.getDominantSlot());
    }

    private boolean tickReelItems(VillagerEntityMCA villager) {
        if (reelItems.isEmpty()) {
            return false;
        }

        reelTicks++;
        Vec3 reelTarget = new Vec3(
                villager.getX(),
                villager.getY() + villager.getEyeHeight() / 2.0,
                villager.getZ()
        );
        for (int i = reelItems.size() - 1; i >= 0; i--) {
            ItemEntity item = reelItems.get(i);
            if (item.isRemoved()) {
                reelItems.remove(i);
                continue;
            }

            Vec3 attraction = reelTarget.subtract(item.position());
            double distanceSqr = attraction.lengthSqr();
            if (distanceSqr < 64.0 && distanceSqr > 0.0) {
                double strength = 1.0 - Math.sqrt(distanceSqr) / 8.0;
                item.setDeltaMovement(
                        item.getDeltaMovement().add(attraction.normalize().scale(strength * strength * 0.1))
                );
                item.needsSync = true;
            }

            if (distanceSqr <= REEL_DELIVERY_DISTANCE_SQR || reelTicks >= MAX_REEL_TICKS) {
                finishReelItem(villager, item);
                reelItems.remove(i);
            }
        }

        if (reelItems.isEmpty()) {
            reelTicks = 0;
        }
        return true;
    }

    private void finishReelItem(VillagerEntityMCA villager, ItemEntity item) {
        if (item.isRemoved()) {
            return;
        }

        ItemStack remainder = villager.getInventory().addItem(item.getItem());
        if (remainder.isEmpty()) {
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] reel-delivered villager={} itemEntity={} inventoryRods={}",
                    villager.getUUID(), item.getId(), villager.getInventory().countItem(Items.FISHING_ROD)
            );
            item.discard();
        } else {
            item.setItem(remainder);
            releasePickupProtection(item);
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] reel-remainder villager={} itemEntity={} remainder={} inventoryRods={}",
                    villager.getUUID(), item.getId(), remainder, villager.getInventory().countItem(Items.FISHING_ROD)
            );
        }
    }

    private void finishReelItems(VillagerEntityMCA villager) {
        for (ItemEntity item : reelItems) {
            finishReelItem(villager, item);
        }
        clearReelItems();
    }

    private void releaseReelItems() {
        for (ItemEntity item : reelItems) {
            if (!item.isRemoved()) {
                releasePickupProtection(item);
            }
        }
        clearReelItems();
    }

    private static void releasePickupProtection(ItemEntity item) {
        item.setTarget(null);
        item.setNoPickUpDelay();
    }

    private void clearReelItems() {
        reelItems.clear();
        reelTicks = 0;
    }

    private boolean equipFishingRod(VillagerEntityMCA villager) {
        ItemStack heldStack = villager.getItemInHand(villager.getDominantHand());
        if (Chore.FISH.matchesTool(heldStack)) {
            return true;
        }

        int slot = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), Chore.FISH::matchesTool);
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

    List<ItemStack> getFishingLoot(ServerLevel world, VillagerEntityMCA villager) {
        MCAFishingBobberEntity bobber = villager.getFishingBobber();
        if (bobber == null || bobber.isRemoved()) {
            return List.of();
        }

        LootTable lootTable = world.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        ItemStack fishingRod = villager.getItemInHand(villager.getDominantHand());
        LootParams.Builder builder = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, bobber.position())
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, bobber)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(world, fishingRod, villager));
        return lootTable.getRandomItems(builder.create(LootContextParamSets.FISHING));
    }

    private void discardBobber(VillagerEntityMCA villager) {
        MCAFishingBobberEntity bobber = villager.getFishingBobber();
        if (bobber != null && !bobber.isRemoved()) {
            bobber.discard();
        }
        biteAttempted = false;
        biteReactionTicksRemaining = -1;
        castBobber = null;
        castReachedWater = false;
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (!reelItems.isEmpty()) {
            if (villager.isAlive() && !villager.isRemoved()) {
                finishReelItems(villager);
            } else {
                releaseReelItems();
            }
        }

        discardBobber(villager);
        clearFishingMovement(villager);
        currentSite = null;
        siteSearch.reset();
        previousCastWater = null;

        if (villager.isUsingRecoveryFood()) {
            villager.stopUsingItem();
        }

        ItemStack stack = villager.getItemInHand(villager.getDominantHand());
        if (Chore.FISH.matchesTool(stack)) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        }
    }
}
