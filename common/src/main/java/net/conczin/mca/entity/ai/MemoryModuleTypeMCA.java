package net.conczin.mca.entity.ai;

import com.mojang.serialization.Codec;
import net.conczin.mca.MCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public interface MemoryModuleTypeMCA {
    Map<ResourceLocation, MemoryModuleType<?>> MEMORY_MODULES = new HashMap<>();

    //if you do not provide a codec, it does not save! however, for things like players, you will likely need to save their UUID beforehand.
    MemoryModuleType<Player> PLAYER_FOLLOWING = register("player_following_memory", Optional.empty());
    MemoryModuleType<Boolean> STAYING = register("staying_memory", Optional.of(Codec.BOOL));
    MemoryModuleType<LivingEntity> NEAREST_GUARD_ENEMY = register("nearest_guard_enemy", Optional.empty());
    MemoryModuleType<Boolean> WEARS_ARMOR = register("wears_armor", Optional.of(Codec.BOOL));
    MemoryModuleType<Integer> SMALL_BOUNTY = register("small_bounty", Optional.of(Codec.INT));
    MemoryModuleType<LivingEntity> HIT_BY_PLAYER = register("hit_by_player", Optional.empty());
    MemoryModuleType<Long> LAST_GRIEVE = register("last_grieve", Optional.of(Codec.LONG));
    MemoryModuleType<BlockPos> MOURNING_SITE = register("mourning_site", Optional.of(BlockPos.CODEC));
    MemoryModuleType<GlobalPos> MOURNING_POSITION = register("mourning_position", Optional.of(GlobalPos.CODEC));
    MemoryModuleType<Boolean> FORCED_HOME = register("forced_home", Optional.of(Codec.BOOL));

    static <U> MemoryModuleType<U> register(String name, Optional<Codec<U>> codec) {
        ResourceLocation id = MCA.locate(name);
        MemoryModuleType<U> memory = new MemoryModuleType<>(codec);
        MEMORY_MODULES.put(id, memory);
        return memory;
    }

    static void registerTypes(MCA.RegisterHelper<MemoryModuleType<?>> helper) {
        MEMORY_MODULES.forEach(helper::register);
    }
}
