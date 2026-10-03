package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(SetWalkTargetFromBlockMemory.class)
abstract class MixinSetWalkTargetFromBlockMemory {
    @ModifyReturnValue(method = "create", at = @At("RETURN"))
    private static OneShot<Villager> mca$usePersistentPoiTarget(
            OneShot<Villager> original,
            MemoryModuleType<GlobalPos> memoryType,
            float speedModifier,
            int closeEnoughDistance,
            int ignoredTooFarDistance,
            int tooLongUnreachableDuration
    ) {
        // MCA navigation owns the search horizon. Preserve the logical POI here instead of
        // converting it to vanilla's random waypoint once it crosses tooFarDistance.
        OneShot<VillagerEntityMCA> persistentTarget = ExtendedWalkTowardsTask.create(
                memoryType,
                speedModifier,
                closeEnoughDistance,
                tooLongUnreachableDuration,
                villager -> true,
                villager -> { }
        );

        return new OneShot<>() {
            @Override
            public Set<MemoryModuleType<?>> getRequiredMemories() {
                return original.getRequiredMemories();
            }

            @Override
            public boolean trigger(ServerLevel level, Villager villager, long gameTime) {
                if (!(villager instanceof VillagerEntityMCA mcaVillager)) {
                    return original.trigger(level, villager, gameTime);
                }
                if (mcaVillager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                    return false;
                }
                return persistentTarget.trigger(level, mcaVillager, gameTime);
            }

            @Override
            public String debugString() {
                return original.debugString();
            }
        };
    }
}
