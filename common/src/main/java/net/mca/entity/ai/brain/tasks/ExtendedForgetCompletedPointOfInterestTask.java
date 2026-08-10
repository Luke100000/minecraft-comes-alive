package net.mca.entity.ai.brain.tasks;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class ExtendedForgetCompletedPointOfInterestTask {
    private static final int MAX_RANGE = 16;

    public ExtendedForgetCompletedPointOfInterestTask() {
    }

    public static OneShot<LivingEntity> create(Predicate<Holder<PoiType>> poiTypePredicate, MemoryModuleType<GlobalPos> poiPosModule, Consumer<LivingEntity> onFinish) {
        return BehaviorBuilder.create((context) -> {
            return context.group(context.present(poiPosModule)).apply(context, (poiPos) -> {
                return (world, entity, time) -> {
                    GlobalPos globalPos = context.get(poiPos);
                    BlockPos blockPos = globalPos.pos();
                    if (world.dimension() == globalPos.dimension() && blockPos.closerToCenterThan(entity.position(), MAX_RANGE)) {
                        ServerLevel serverWorld = world.getServer().getLevel(globalPos.dimension());
                        if (serverWorld != null && serverWorld.getPoiManager().exists(blockPos, poiTypePredicate)) {
                            if (isBedOccupiedByOthers(serverWorld, blockPos, entity)) {
                                poiPos.erase();
                                world.getPoiManager().release(blockPos);
                                DebugPackets.sendPoiTicketCountPacket(world, blockPos);
                            }
                        } else {
                            poiPos.erase();
                        }

                        return true;
                    } else {
                        // We're finished -- run callback
                        if (entity.getBrain().checkMemory(poiPosModule, MemoryStatus.VALUE_ABSENT)) {
                            onFinish.accept(entity);
                        }
                        return false;
                    }
                };
            });
        });
    }

    private static boolean isBedOccupiedByOthers(ServerLevel world, BlockPos pos, LivingEntity entity) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.is(BlockTags.BEDS) && blockState.getValue(BedBlock.OCCUPIED) && !entity.isSleeping();
    }
}
