package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Mourning {
    private static final long RETRY_DELAY = 1_200L;
    private static final long FAILED_PATH_RETRY_DELAY = 4_800L;
    private static final long SAFETY_RETRY_DELAY = 200L;
    private static final double MONSTER_HORIZONTAL_RANGE = 8.0D;
    private static final double MONSTER_VERTICAL_RANGE = 5.0D;

    private Mourning() {
    }

    public static void start(VillagerEntityMCA villager, BlockPos grave) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_AMBIENT);
        assign(villager, grave);
    }

    public static void startAmbient(VillagerEntityMCA villager, BlockPos grave) {
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_AMBIENT, true);
        assign(villager, grave);
    }

    private static void assign(VillagerEntityMCA villager, BlockPos grave) {
        villager.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_SITE,
                GlobalPos.of(villager.level().dimension(), grave)
        );
        resume(villager);
    }

    public static void resume(VillagerEntityMCA villager) {
        if (!Config.SERVER.enableMourning.get()) {
            pause(villager);
            return;
        }

        clearOwnedMovement(villager);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);

        if (isTemporarilyBlocked(villager)) {
            return;
        }
        if (isAssignedGraveUnsafe(villager)) {
            deferUnsafe(villager);
            return;
        }

        activate(villager);
    }

    public static boolean canResumeScheduled(VillagerEntityMCA villager) {
        if (!Config.SERVER.enableMourning.get()) {
            pause(villager);
            return false;
        }

        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension())).isEmpty()) {
            return false;
        }
        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                .filter(deadline -> villager.level().getGameTime() < deadline).isPresent()) {
            return false;
        }
        if (isTemporarilyBlocked(villager)) {
            return false;
        }
        if (isKnownInvalidSite(villager)) {
            finish(villager);
            return false;
        }
        if (isAssignedGraveUnsafe(villager)) {
            deferUnsafe(villager);
            return false;
        }
        return true;
    }

    public static void resumeScheduled(VillagerEntityMCA villager) {
        clearOwnedMovement(villager);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
        activate(villager);
    }

    private static void activate(VillagerEntityMCA villager) {
        clearMovement(villager);
        villager.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension()))
                .ifPresent(site -> {
                    lookAtGrave(villager);
                    villager.getBrain().setActiveActivityIfPossible(ActivitiesMCA.GRIEVE);
                });
    }

    public static void clear(VillagerEntityMCA villager) {
        restorePreviousHand(villager);
        clearOwnedMovement(villager);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_AMBIENT);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
    }

    public static void pause(VillagerEntityMCA villager) {
        restorePreviousHand(villager);
        clearOwnedMovement(villager);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
        leaveGrievingActivity(villager);
    }

    public static void retry(VillagerEntityMCA villager) {
        retry(villager, RETRY_DELAY);
    }

    public static void noStandingPosition(VillagerEntityMCA villager) {
        if (isAmbient(villager)) {
            finish(villager);
        } else {
            retry(villager);
        }
    }

    public static void unreachable(VillagerEntityMCA villager) {
        if (isAmbient(villager)) {
            finish(villager);
        } else {
            retry(villager, FAILED_PATH_RETRY_DELAY);
        }
    }

    private static boolean isAmbient(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_AMBIENT).orElse(false);
    }

    private static void retry(VillagerEntityMCA villager, long delay) {
        restorePreviousHand(villager);
        clearOwnedMovement(villager);
        villager.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_RETRY_AT,
                villager.level().getGameTime() + delay
        );
        leaveGrievingActivity(villager);
    }

    public static void deferUnsafe(VillagerEntityMCA villager) {
        restorePreviousHand(villager);
        clearOwnedMovement(villager);
        villager.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_RETRY_AT,
                villager.level().getGameTime() + SAFETY_RETRY_DELAY
        );
        leaveGrievingActivity(villager);
    }

    public static void finish(VillagerEntityMCA villager) {
        clear(villager);
        leaveGrievingActivity(villager);
    }

    public static void equipFlower(VillagerEntityMCA villager, ItemStack flower) {
        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND).isPresent()) {
            return;
        }

        ItemStack previousMainHand = villager.getMainHandItem().copy();
        ItemStack mourningFlower = InventoryUtils.temporary(flower);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND, previousMainHand);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_FLOWER, mourningFlower.copy());
        villager.setItemInHand(InteractionHand.MAIN_HAND, mourningFlower);
    }

    public static ItemStack getReloadableFlower(VillagerEntityMCA villager) {
        ItemStack mourningFlower = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_FLOWER)
                .orElse(ItemStack.EMPTY);
        return !mourningFlower.isEmpty()
                && villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND)
                && (villager.getMainHandItem().isEmpty()
                    || ItemStack.matches(villager.getMainHandItem(), mourningFlower))
                ? mourningFlower : ItemStack.EMPTY;
    }

    public static void restoreLoadedFlower(VillagerEntityMCA villager, ItemStack savedFlower) {
        ItemStack flower = InventoryUtils.temporary(savedFlower.copy());
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_FLOWER, flower.copy());
        villager.setItemInHand(InteractionHand.MAIN_HAND, flower);
    }

    private static void restorePreviousHand(VillagerEntityMCA villager) {
        ItemStack currentMainHand = villager.getMainHandItem();
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_FLOWER)
                .filter(mourningFlower -> ItemStack.matches(currentMainHand, mourningFlower))
                .flatMap(mourningFlower -> villager.getBrain()
                        .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND))
                .map(previousMainHand -> {
                    if (InventoryUtils.isTemporary(previousMainHand)) return previousMainHand.copy();
                    return InventoryUtils.stream(villager.getInventory())
                            .filter(stack -> ItemStack.matches(stack, previousMainHand))
                            .findFirst().orElse(ItemStack.EMPTY);
                })
                .ifPresent(previousMainHand -> villager.setItemInHand(
                        InteractionHand.MAIN_HAND,
                        previousMainHand
                ));
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_FLOWER);
    }

    private static void clearMovement(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        WalkTargetFailureMemory.clear(villager);
        villager.getNavigation().stop();
    }

    private static void clearOwnedMovement(VillagerEntityMCA villager) {
        ExtendedWalkTowardsTask.retractOwnedMovement(villager, MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                .ifPresent(position -> WalkTargetFailureMemory.clearForProducer(
                        villager, MemoryModuleTypeMCA.MOURNING_POSITION, position));
        if (villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET)
                .filter(MourningLookTarget.class::isInstance).isPresent()) {
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }

        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
    }

    public static void lookAtGrave(VillagerEntityMCA villager) {
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension()))
                .map(GlobalPos::pos)
                .filter(grave -> villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET)
                        .filter(target -> target.currentBlockPosition().equals(grave))
                        .isEmpty())
                .ifPresent(grave -> villager.getBrain().setMemory(
                        MemoryModuleType.LOOK_TARGET, new MourningLookTarget(grave)));
    }

    private static final class MourningLookTarget extends BlockPosTracker {
        private MourningLookTarget(BlockPos grave) {
            super(grave);
        }
    }

    private static void leaveGrievingActivity(VillagerEntityMCA villager) {
        // Disabling the feature cancels the assignment, including safety and movement retries.
        if (!Config.SERVER.enableMourning.get()) {
            clear(villager);
        }

        if (!villager.getBrain().isActive(ActivitiesMCA.GRIEVE)) {
            return;
        }

        Activity scheduled = villager.getBrain().getSchedule()
                .getActivityAt((int) (villager.level().getDayTime() % 24_000L));
        villager.getBrain().setActiveActivityIfPossible(scheduled);
    }

    public static boolean isInterrupted(VillagerEntityMCA villager) {
        return villager.getBrain().isActive(Activity.PANIC) || VillagerTasksMCA.isInDanger(villager);
    }

    public static boolean isTemporarilyBlocked(VillagerEntityMCA villager) {
        return !Config.SERVER.enableMourning.get()
                || isInterrupted(villager)
                || villager.isSleeping()
                // GRIEVE has no schedule updater, so check the scheduled activity itself.
                || villager.getBrain().getSchedule()
                        .getActivityAt((int) (villager.level().getDayTime() % 24_000L)) == Activity.REST
                || villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isPresent()
                || villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING).isPresent();
    }

    public static boolean isKnownInvalidSite(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .map(site -> site.dimension().equals(villager.level().dimension())
                        && villager.level().isLoaded(site.pos())
                        && !isMournableTombstone(villager.level(), site.pos()))
                .orElse(true);
    }

    public static boolean isAssignedGraveUnsafe(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension()))
                .map(GlobalPos::pos)
                .filter(villager.level()::isLoaded)
                .filter(grave -> !isSafeToMourn(villager.level(), grave))
                .isPresent();
    }

    public static boolean isSafeToMourn(Level level, BlockPos grave) {
        if (!level.isLoaded(grave)) {
            return true;
        }

        Vec3 center = Vec3.atBottomCenterOf(grave);
        AABB dangerZone = new AABB(
                center.x() - MONSTER_HORIZONTAL_RANGE,
                center.y() - MONSTER_VERTICAL_RANGE,
                center.z() - MONSTER_HORIZONTAL_RANGE,
                center.x() + MONSTER_HORIZONTAL_RANGE,
                center.y() + MONSTER_VERTICAL_RANGE,
                center.z() + MONSTER_HORIZONTAL_RANGE
        );
        return level.getEntitiesOfClass(Monster.class, dangerZone, Monster::isAlive).isEmpty();
    }

    public static boolean isMournableTombstone(Level level, BlockPos position) {
        return level.getBlockState(position).is(TagsMCA.Blocks.TOMBSTONES)
                && TombstoneBlock.Data.of(level.getBlockEntity(position))
                .filter(TombstoneBlock.Data::hasEntity)
                .filter(data -> !data.isResurrecting())
                .isPresent();
    }

    public static List<BlockPos> getMournableGraves(Village village, ServerLevel level) {
        List<BlockPos> registeredGraves = village.getBuildingsOfType("graveyard")
                .filter(Building::isComplete)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .filter(level::isLoaded)
                .filter(position -> isMournableTombstone(level, position))
                .toList();
        if (!registeredGraves.isEmpty()) {
            return registeredGraves;
        }

        BoundingBox villageBounds = village.getBox().inflatedBy(Village.BORDER_MARGIN);
        AABB searchBounds = new AABB(
                villageBounds.minX(),
                villageBounds.minY(),
                villageBounds.minZ(),
                villageBounds.maxX() + 1.0D,
                villageBounds.maxY() + 1.0D,
                villageBounds.maxZ() + 1.0D
        );
        return GraveyardManager.get(level)
                .findAll(searchBounds, false, true)
                .stream()
                .distinct()
                .filter(position -> village.isWithinBorder(position, Village.BORDER_MARGIN))
                .filter(level::isLoaded)
                .filter(position -> isMournableTombstone(level, position))
                .toList();
    }

    public static boolean canMournAmbiently(VillagerEntityMCA villager) {
        boolean ambientActivity = villager.getBrain().isActive(Activity.IDLE)
                || villager.getBrain().isActive(Activity.MEET);
        return villager.isAlive()
                && ambientActivity
                && !villager.getBrain().isActive(ActivitiesMCA.GRIEVE)
                && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty()
                && !isTemporarilyBlocked(villager)
                && villager.getVillagerBrain().getCurrentJob() == Chore.NONE;
    }
}
