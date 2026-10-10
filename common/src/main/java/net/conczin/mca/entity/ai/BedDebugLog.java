package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.Objects;
import java.util.List;
import java.util.Set;

/** Temporary bed diagnostics. No gameplay state is changed or persisted. */
public final class BedDebugLog {
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("mca.bedDebug", "false"));
    public static final boolean SHELTER_ENABLED = Boolean.parseBoolean(System.getProperty("mca.shelterDebug", "false"));
    private GlobalPos previousHome;
    private BlockPos previousSleepingPos;
    private String previousWalk;
    private String previousActivity;
    private Boolean previousNight;
    private long nextSnapshot;

    public void tick(VillagerEntityMCA villager) {
        // Sample transitions once per second; stagger villagers to spread diagnostic work across ticks.
        if (!ENABLED || (villager.tickCount + villager.getId()) % 20 != 0
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        var brain = villager.getBrain();
        GlobalPos home = brain.getMemory(MemoryModuleType.HOME).orElse(null);
        BlockPos sleepingPos = villager.getSleepingPos().orElse(null);
        String walk = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getClass().getSimpleName() + "/" + target.getTarget().getClass().getSimpleName()
                        + "@" + target.getTarget().currentBlockPosition().toShortString())
                .orElse("none");
        String activity = brain.getActiveNonCoreActivity().map(Object::toString).orElse("none");
        long dayTime = level.getOverworldClockTime();
        long dayTick = Math.floorMod(dayTime, 24_000L);
        boolean night = dayTick >= 13_000L && dayTick < 23_000L;
        boolean activePeriod = night || sleepingPos != null || brain.isActive(Activity.REST);
        boolean changed = !Objects.equals(home, previousHome) || !Objects.equals(sleepingPos, previousSleepingPos)
                || !Objects.equals(night, previousNight)
                || activePeriod && (!walk.equals(previousWalk) || !activity.equals(previousActivity));
        boolean snapshot = level.getGameTime() >= nextSnapshot && activePeriod;
        if (!changed && !snapshot) {
            previousWalk = walk;
            previousActivity = activity;
            return;
        }

        String changes = "night=" + previousNight + "->" + night + ",home=" + previousHome + "->" + home
                + ",sleep=" + previousSleepingPos + "->" + sleepingPos + ",walk=" + previousWalk + "->" + walk;
        previousHome = home;
        previousSleepingPos = sleepingPos;
        previousWalk = walk;
        previousActivity = activity;
        previousNight = night;

        BlockPos under = villager.blockPosition();
        if (!BedPoiCompatibility.isCompatibleBedState(level.getBlockState(under))) {
            under = under.below();
        }
        var path = villager.getNavigation().getPath();
        var village = villager.getResidency().getHomeVillage();
        String movementOwner = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target instanceof WalkTargetFailureMemory.TargetIdentityOwner owner
                        ? owner.failureTarget().toString() : "notDestinationOwned")
                .orElse("none");
        String homeBed = home == null ? "none" : home.dimension().equals(level.dimension())
                ? describeBed(level, home.pos()) : "differentDimension";
        MCA.LOGGER.info("[MCA-BED][server] name={} uuid={} dimension={} gameTime={} dayTime={} night={} age={} pos={} activity={} home={} homeBed={} forced={} sleeping={} walk={} movementOwner={} nearestBed={} path={} navDone={} stuck={} velocity={} cantReachSince={} onBed={} village={} building={} homeBuilding={} changes={}",
                villager.getName().getString(), villager.getUUID(), level.dimension().identifier(), level.getGameTime(), dayTime, night,
                villager.getAgeState(), villager.position(), activity, home,
                homeBed, brain.getMemory(MemoryModuleTypeMCA.FORCED_HOME), sleepingPos, walk, movementOwner,
                brain.getMemory(MemoryModuleType.NEAREST_BED), describePath(path),
                villager.getNavigation().isDone(), villager.getNavigation().isStuck(), villager.getDeltaMovement(),
                brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE), describeBed(level, under),
                village.map(v -> v.getId()).orElse(-1),
                village.flatMap(v -> v.getBuildingAt(villager.blockPosition())).map(b -> b.getId()).orElse(-1),
                home == null || !home.dimension().equals(level.dimension()) ? -1
                        : village.flatMap(v -> v.getBuildingAt(home.pos())).map(b -> b.getId()).orElse(-1), changes);

        if (snapshot) {
            nextSnapshot = level.getGameTime() + 200;
            var nearby = level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(16));
            var beds = level.getPoiManager().findAllClosestFirstWithType(type -> type.is(PoiTypes.HOME), pos -> true,
                            villager.blockPosition(), 24, PoiManager.Occupancy.ANY)
                    .limit(8).map(poi -> describeBed(level, poi.getSecond())).toList();
            MCA.LOGGER.info("[MCA-BED][neighborhood] uuid={} bedsRadius24First8={} villagersRadius16Count={} first16={} running={}",
                    villager.getUUID(), beds, nearby.size(), nearby.stream().limit(16)
                            .map(other -> other.getName().getString() + "/" + other.getUUID() + "@" + other.blockPosition().toShortString()
                                    + ":home=" + other.getBrain().getMemory(MemoryModuleType.HOME)
                                    + ":sleep=" + other.getSleepingPos()).toList(),
                    brain.getRunningBehaviors().stream().map(task -> task.getClass().getSimpleName()).toList());
        }
    }

    public static void selection(VillagerEntityMCA villager, Set<BlockPos> candidates, Path path, String result, List<String> exclusions) {
        if (!ENABLED || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        // HOME acquisition already runs only once every 200-400 ticks.
        MCA.LOGGER.info("[MCA-BED][selection] name={} uuid={} dimension={} dayTime={} pos={} eligibleBeforePath={} candidateStatesAfterAttempt={} excludedFirst8={} homePoiCountRadius48={} freeHomePoiCountRadius48={} path={} result={} homeAfterCallback={}",
                villager.getName().getString(), villager.getUUID(), level.dimension().identifier(), level.getOverworldClockTime(),
                villager.position(), candidates, candidates.stream().map(pos -> describeBed(level, pos)).toList(),
                exclusions,
                level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), villager.blockPosition(), 48, PoiManager.Occupancy.ANY),
                level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), villager.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE),
                describePath(path), result, villager.getBrain().getMemory(MemoryModuleType.HOME));
    }

    private static String describePath(Path path) {
        return path == null ? "none" : "target=" + path.getTarget().toShortString() + ",reachable=" + path.canReach()
                + ",node=" + path.getNextNodeIndex() + "/" + path.getNodeCount();
    }

    private static String describeBed(ServerLevel level, BlockPos pos) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return pos.toShortString() + ":unloaded";
        }
        BlockState state = level.getBlockState(pos);
        if (!BedPoiCompatibility.isCompatibleBedState(state)) {
            return pos.toShortString() + ":notBed=" + state.getBlock();
        }
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
        boolean freeTicket = level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), head, 0, PoiManager.Occupancy.HAS_SPACE) > 0;
        var sleepers = level.getEntitiesOfClass(LivingEntity.class, new AABB(head).inflate(1),
                entity -> entity.isSleeping() && entity.getSleepingPos().filter(head::equals).isPresent());
        return pos.toShortString() + ":facing=" + state.getValue(BedBlock.FACING)
                + ":part=" + state.getValue(BedBlock.PART) + ":occupied=" + state.getValue(BedBlock.OCCUPIED)
                + ":head=" + head.toShortString() + ":freePoiTicket=" + freeTicket + ":sleepers="
                + sleepers.stream().map(entity -> entity.getName().getString() + "/" + entity.getUUID()).toList();
    }
}
