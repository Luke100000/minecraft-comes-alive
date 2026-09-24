package net.conczin.mca.entity.ai;

import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Mourning {
    private static final long RETRY_DELAY = 1_200L;
    private static final long SAFETY_RETRY_DELAY = 200L;
    private static final double MONSTER_HORIZONTAL_RANGE = 8.0D;
    private static final double MONSTER_VERTICAL_RANGE = 5.0D;

    private Mourning() {
    }

    public static void start(VillagerEntityMCA villager, BlockPos grave) {
        villager.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_SITE,
                GlobalPos.of(villager.level().dimension(), grave)
        );
        resume(villager);
    }

    public static void resume(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);

        if (isTemporarilyBlocked(villager)) {
            return;
        }
        if (isAssignedGraveUnsafe(villager)) {
            deferUnsafe(villager);
            return;
        }

        clearMovement(villager);
        villager.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension()))
                .ifPresent(site -> {
                    villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(site.pos()));
                    villager.getBrain().setActiveActivityIfPossible(ActivitiesMCA.GRIEVE);
                });
    }

    public static void clear(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
    }

    public static void pause(VillagerEntityMCA villager) {
        clearOwnedMovement(villager);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
        leaveGrievingActivity(villager);
    }

    public static void retry(VillagerEntityMCA villager) {
        clearMovement(villager);
        villager.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_RETRY_AT,
                villager.level().getGameTime() + RETRY_DELAY
        );
        leaveGrievingActivity(villager);
    }

    public static void deferUnsafe(VillagerEntityMCA villager) {
        clearOwnedMovement(villager);
        villager.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_RETRY_AT,
                villager.level().getGameTime() + SAFETY_RETRY_DELAY
        );
        leaveGrievingActivity(villager);
    }

    public static void finish(VillagerEntityMCA villager) {
        clear(villager);
        clearMovement(villager);
        leaveGrievingActivity(villager);
    }

    private static void clearMovement(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getNavigation().stop();
    }

    private static void clearOwnedMovement(VillagerEntityMCA villager) {
        boolean ownsWalkTarget = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                .filter(position -> position.dimension().equals(villager.level().dimension()))
                .flatMap(position -> villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .filter(walkTarget -> walkTarget.getTarget().currentBlockPosition().equals(position.pos())))
                .isPresent();
        if (ownsWalkTarget) {
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            villager.getNavigation().stop();
        }

        boolean ownsLookTarget = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension()))
                .flatMap(site -> villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET)
                        .filter(lookTarget -> lookTarget.currentBlockPosition().equals(site.pos())))
                .isPresent();
        if (ownsLookTarget) {
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }

        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
    }

    private static void leaveGrievingActivity(VillagerEntityMCA villager) {
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
        return isInterrupted(villager)
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
