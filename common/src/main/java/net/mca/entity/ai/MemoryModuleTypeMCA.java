package net.mca.entity.ai;

import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.mixin.MixinMemoryModuleType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

import java.util.Optional;

public interface MemoryModuleTypeMCA {

    DeferredRegister<MemoryModuleType<?>> MEMORY_MODULES = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.MEMORY_MODULE_TYPE);

    //if you do not provide a codec, it does not save! however, for things like players, you will likely need to save their UUID beforehand.
    RegistrySupplier<MemoryModuleType<PlayerEntity>> PLAYER_FOLLOWING = register("player_following_memory", Optional.empty());
    RegistrySupplier<MemoryModuleType<Boolean>> STAYING = register("staying_memory", Optional.of(Codec.BOOL));
    RegistrySupplier<MemoryModuleType<LivingEntity>> NEAREST_GUARD_ENEMY = register("nearest_guard_enemy", Optional.empty());
    RegistrySupplier<MemoryModuleType<Boolean>> WEARS_ARMOR = register("wears_armor", Optional.of(Codec.BOOL));
    RegistrySupplier<MemoryModuleType<Integer>> SMALL_BOUNTY = register("small_bounty", Optional.of(Codec.INT));
    RegistrySupplier<MemoryModuleType<LivingEntity>> HIT_BY_PLAYER = register("hit_by_player", Optional.empty());
    RegistrySupplier<MemoryModuleType<Long>> LAST_GRIEVE = register("last_grieve", Optional.of(Codec.LONG));
    RegistrySupplier<MemoryModuleType<BlockPos>> MOURNING_SITE = register("mourning_site", Optional.of(BlockPos.CODEC));
    RegistrySupplier<MemoryModuleType<GlobalPos>> MOURNING_POSITION = register("mourning_position", Optional.of(GlobalPos.CODEC));
    RegistrySupplier<MemoryModuleType<Boolean>> FORCED_HOME = register("forced_home", Optional.of(Codec.BOOL));

    static void bootstrap() {
        MEMORY_MODULES.register();
    }

    static <U> RegistrySupplier<MemoryModuleType<U>> register(String name, Optional<Codec<U>> codec) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return MEMORY_MODULES.register(id, () -> MixinMemoryModuleType.init(codec));
    }
}
