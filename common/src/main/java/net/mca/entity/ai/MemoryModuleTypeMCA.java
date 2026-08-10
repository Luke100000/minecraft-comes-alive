package net.mca.entity.ai;

import com.mojang.serialization.Codec;
import net.mca.MCA;
import net.mca.util.RegistryRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface MemoryModuleTypeMCA {

    Map<ResourceLocation, RegistryRef<? extends MemoryModuleType<?>>> MEMORY_MODULES = new LinkedHashMap<>();

    //if you do not provide a codec, it does not save! however, for things like players, you will likely need to save their UUID beforehand.
    RegistryRef<MemoryModuleType<Player>> PLAYER_FOLLOWING = register("player_following_memory", Optional.empty());
    RegistryRef<MemoryModuleType<Boolean>> STAYING = register("staying_memory", Optional.of(Codec.BOOL));
    RegistryRef<MemoryModuleType<LivingEntity>> NEAREST_GUARD_ENEMY = register("nearest_guard_enemy", Optional.empty());
    RegistryRef<MemoryModuleType<Boolean>> WEARS_ARMOR = register("wears_armor", Optional.of(Codec.BOOL));
    RegistryRef<MemoryModuleType<Integer>> SMALL_BOUNTY = register("small_bounty", Optional.of(Codec.INT));
    RegistryRef<MemoryModuleType<LivingEntity>> HIT_BY_PLAYER = register("hit_by_player", Optional.empty());
    RegistryRef<MemoryModuleType<Long>> LAST_GRIEVE = register("last_grieve", Optional.of(Codec.LONG));
    RegistryRef<MemoryModuleType<BlockPos>> MOURNING_SITE = register("mourning_site", Optional.of(BlockPos.CODEC));
    RegistryRef<MemoryModuleType<GlobalPos>> MOURNING_POSITION = register("mourning_position", Optional.of(GlobalPos.CODEC));
    RegistryRef<MemoryModuleType<Boolean>> FORCED_HOME = register("forced_home", Optional.of(Codec.BOOL));

    static <U> RegistryRef<MemoryModuleType<U>> register(String name, Optional<Codec<U>> codec) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<MemoryModuleType<U>> ref = RegistryRef.of(id, () -> new MemoryModuleType<>(codec));
        MEMORY_MODULES.put(id, ref);
        return ref;
    }

    static void registerTypes(MCA.RegisterHelper<MemoryModuleType<?>> helper) {
        MEMORY_MODULES.forEach((id, ref) -> helper.register(id, ref.get()));
    }
}
