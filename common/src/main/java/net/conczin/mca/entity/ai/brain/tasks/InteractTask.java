package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class InteractTask extends Behavior<VillagerEntityMCA> {
    private final float speedModifier;
    private Player trackedPlayer;
    private UUID trackedInteractionId;
    private EntityTracker lookTarget;
    private WalkTarget walkTarget;
    private BlockPos destination;
    private Vec3 selectedPlayerPosition;
    private Vec3 selectedLookDirection;
    private long nextReplanTime;

    public InteractTask(float speedModifier) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), Integer.MAX_VALUE);
        this.speedModifier = speedModifier;
    }

    public static boolean shouldRun(VillagerEntityMCA villager) {
        return villager.isAlive()
               && villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && villager.getInteractions().getInteractingPlayer().filter(player -> villager.distanceToSqr(player) <= 25).isPresent()
               && !villager.getBrain().isActive(Activity.PANIC);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return shouldRun(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return this.checkExtraStartConditions(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        updateTargets(world, villager, time);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        clearOwnedTargets(villager);
        trackedPlayer = null;
        trackedInteractionId = null;
        lookTarget = null;
        selectedPlayerPosition = null;
        selectedLookDirection = null;
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        updateTargets(world, villager, time);
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    private void updateTargets(ServerLevel world, VillagerEntityMCA villager, long time) {
        Brain<?> brain = villager.getBrain();
        Player player = villager.getInteractions().getInteractingPlayer().orElse(null);
        if (player == null) {
            clearOwnedTargets(villager);
            return;
        }
        UUID interactionId = villager.getInteractions().interactionId();
        boolean newOwner = trackedPlayer != player || !interactionId.equals(trackedInteractionId);
        if (newOwner) {
            clearOwnedTargets(villager);
            // Release the previous work/chore/follow navigation when a conversation begins.
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            villager.getNavigation().stop();
            trackedPlayer = player;
            trackedInteractionId = interactionId;
            lookTarget = new EntityTracker(player, true);
            nextReplanTime = time;
        }
        if (brain.getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null) != lookTarget) {
            brain.setMemory(MemoryModuleType.LOOK_TARGET, lookTarget);
        }

        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 0.001D) {
            double yaw = Math.toRadians(player.getYRot());
            horizontal = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        } else {
            horizontal = horizontal.normalize();
        }
        boolean changed = selectedPlayerPosition == null
                || selectedPlayerPosition.distanceToSqr(player.position()) > 1.0D
                || selectedLookDirection.dot(horizontal) < 0.85D;
        boolean stalled = destination != null && villager.getNavigation().isDone()
                && !reached(villager, destination);
        // A temporary failure to create an initial path must not strand a
        // stationary player/villager pair permanently.
        if ((newOwner || changed || stalled || destination == null) && time >= nextReplanTime) {
            clearWalkTarget(villager);
            selectedPlayerPosition = player.position();
            selectedLookDirection = horizontal;
            nextReplanTime = time + 30L;
            destination = chooseDestination(world, villager, player, horizontal);
            if (destination != null && !reached(villager, destination)) {
                walkTarget = new WalkTarget(new BlockPosTracker(destination), speedModifier, 0);
                brain.setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
            }
        }
        if (destination != null && reached(villager, destination)) {
            clearWalkTarget(villager);
        } else if (walkTarget != null && !brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
        }
    }

    private BlockPos chooseDestination(ServerLevel world, VillagerEntityMCA villager, Player player, Vec3 forward) {
        Vec3 separation = villager.position().subtract(player.position());
        double horizontalDist = separation.horizontalDistanceSqr();
        // A villager already standing comfortably in front should hold position and talk.
        if (horizontalDist >= 0.8D && horizontalDist <= 9.0D
                && separation.x * forward.x + separation.z * forward.z > Math.sqrt(horizontalDist) * 0.65D) {
            return null;
        }
        for (int distance : new int[]{3, 2, 1}) {
            double x = player.getX() + forward.x * distance;
            double z = player.getZ() + forward.z * distance;
            BlockPos base = BlockPos.containing(x, player.getY(), z);
            for (int offset : new int[]{0, 1, -1}) {
                BlockPos candidate = base.offset(0, offset, 0);
                if (safeStand(world, villager, player, candidate)) {
                    Path path = villager.getNavigation().createPath(candidate, 0);
                    if (path != null && path.canReach()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static boolean safeStand(ServerLevel world, VillagerEntityMCA villager, Player player, BlockPos pos) {
        if (!world.hasChunkAt(pos) || !world.hasChunkAt(pos.below())
                || WalkNodeEvaluator.getPathTypeStatic(villager, pos) != PathType.WALKABLE) {
            return false;
        }
        Vec3 stand = Vec3.atBottomCenterOf(pos);
        AABB box = villager.getBoundingBox().move(stand.subtract(villager.position()));
        return !player.getBoundingBox().intersects(box) && world.noCollision(villager, box);
    }

    private static boolean reached(VillagerEntityMCA villager, BlockPos pos) {
        return villager.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) < 1.2D;
    }

    private void clearWalkTarget(VillagerEntityMCA villager) {
        if (villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == walkTarget) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        walkTarget = null;
        destination = null;
    }

    private void clearOwnedTargets(VillagerEntityMCA villager) {
        clearWalkTarget(villager);
        if (villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null) == lookTarget) {
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
    }
}
