package mca.entity.ai;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.mixin.MixinMemoryModuleType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

import com.mojang.serialization.Codec;

import mca.MCA;
import net.minecraft.util.registry.Registry;

public interface MemoryModuleTypeMCA {

    DeferredRegister<MemoryModuleType<?>> MEMORY_MODULES = DeferredRegister.create(MCA.MOD_ID, Registry.MEMORY_MODULE_TYPE_KEY);

    //if you do not provide a codec, it does not save! however, for things like players, you will likely need to save their UUID beforehand.
    RegistrySupplier<MemoryModuleType<PlayerEntity>> PLAYER_FOLLOWING = register("player_following_memory", Optional.empty());
    RegistrySupplier<MemoryModuleType<Boolean>> STAYING = register("staying_memory", Optional.of(Codec.BOOL));
    RegistrySupplier<MemoryModuleType<LivingEntity>> NEAREST_GUARD_ENEMY = register("nearest_guard_enemy", Optional.empty());
    RegistrySupplier<MemoryModuleType<Boolean>> WEARS_ARMOR = register("wears_armor", Optional.of(Codec.BOOL));
    RegistrySupplier<MemoryModuleType<Integer>> SMALL_BOUNTY = register("small_bounty", Optional.of(Codec.INT));

    static void bootstrap() {
        MEMORY_MODULES.register();
    }

    static <U> RegistrySupplier<MemoryModuleType<U>> register(String name, Optional<Codec<U>> codec) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return MEMORY_MODULES.register(id, () -> MixinMemoryModuleType.init(codec));
    }
}
