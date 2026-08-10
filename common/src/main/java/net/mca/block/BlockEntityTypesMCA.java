package net.mca.block;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.function.BiFunction;

public interface BlockEntityTypesMCA {
    DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(MCA.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    RegistrySupplier<BlockEntityType<TombstoneBlock.Data>> TOMBSTONE = register("tombstone", TombstoneBlock.Data::new, List.of(
            BlocksMCA.GRAVELLING_HEADSTONE,
            BlocksMCA.UPRIGHT_HEADSTONE,
            BlocksMCA.SLANTED_HEADSTONE,
            BlocksMCA.CROSS_HEADSTONE,
            BlocksMCA.WALL_HEADSTONE,
            BlocksMCA.COBBLESTONE_UPRIGHT_HEADSTONE,
            BlocksMCA.COBBLESTONE_SLANTED_HEADSTONE,
            BlocksMCA.WOODEN_UPRIGHT_HEADSTONE,
            BlocksMCA.WOODEN_SLANTED_HEADSTONE,
            BlocksMCA.GOLDEN_UPRIGHT_HEADSTONE,
            BlocksMCA.GOLDEN_SLANTED_HEADSTONE,
            BlocksMCA.DEEPSLATE_UPRIGHT_HEADSTONE,
            BlocksMCA.DEEPSLATE_SLANTED_HEADSTONE
    ));

    static void bootstrap() {
        BLOCK_ENTITY_TYPES.register();
    }

    static <T extends BlockEntity> RegistrySupplier<BlockEntityType<T>> register(String name, BiFunction<BlockPos, BlockState, T> factory, List<RegistrySupplier<Block>> suppliers) {
        ResourceLocation id = new ResourceLocation(MCA.MOD_ID, name);
        return BLOCK_ENTITY_TYPES.register(id, () -> BlockEntityType.Builder.of(
                factory::apply, suppliers.stream().map(RegistrySupplier::get).toArray(Block[]::new)
        ).build(Util.fetchChoiceType(References.BLOCK_ENTITY, id.toString())));
    }
}
