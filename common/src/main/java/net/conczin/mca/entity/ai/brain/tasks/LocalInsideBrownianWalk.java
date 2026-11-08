package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects short walks within the same resolved room used for shelter entry.
 */
public final class LocalInsideBrownianWalk {
    private LocalInsideBrownianWalk() {
    }

    public static BehaviorControl<PathfinderMob> create(float speedModifier) {
        WalkTargetRetryGate retryGate = new WalkTargetRetryGate(40L, 1.0D);
        return BehaviorBuilder.create(context -> context.group(context.absent(MemoryModuleType.WALK_TARGET))
                .apply(context, walkTarget -> (level, mob, gameTime) -> {
                    BlockPos origin = mob.blockPosition();
                    if (!(mob instanceof VillagerEntityMCA villager)) {
                        return false;
                    }
                    var chunk = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
                    if (chunk == null) return false;
                    // A changed column (for example a newly added roof) permits an immediate retry.
                    BlockPos retryKey = new BlockPos(origin.getX(),
                            chunk.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX() & 15, origin.getZ() & 15),
                            origin.getZ());
                    if (!retryGate.tryReserve(retryKey, origin, gameTime)) {
                        PathRequestDiagnostics.recordDeferredProducerRetry(mob);
                        return false;
                    }
                    var room = SeekIndoorShelterTask.currentRoom(villager);
                    if (room.isEmpty()) return false;
                    List<BlockPos> candidates = new ArrayList<>();
                    for (BlockPos pos : room.orElseThrow().floorCells()) {
                        if (!pos.equals(origin) && pos.distSqr(origin) <= 16.0D
                                && EnterBuildingTask.isUsableFloor(level, mob, pos)) {
                            candidates.add(pos.immutable());
                        }
                    }
                    if (candidates.stream().anyMatch(pos -> pos.distSqr(origin) >= 4.0D)) {
                        // Prefer a short walk across the room, but keep small
                        // steps when furniture leaves only nearby floor space.
                        candidates.removeIf(pos -> pos.distSqr(origin) < 4.0D);
                    }
                    if (candidates.isEmpty()) return false;

                    BlockPos preferred = candidates.get(level.getRandom().nextInt(candidates.size()));
                    walkTarget.set(new WalkTarget(preferred, speedModifier, 0));
                    return true;
                }));
    }
}
