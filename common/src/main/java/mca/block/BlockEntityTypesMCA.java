package mca.block;

import java.util.List;
import java.util.function.BiFunction;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.MCA;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

public interface BlockEntityTypesMCA {

    DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(MCA.MOD_ID, Registry.BLOCK_ENTITY_TYPE_KEY);

    RegistrySupplier<BlockEntityType<TombstoneBlock.Data>> TOMBSTONE = register("tombstone", TombstoneBlock.Data::new, List.of(
            BlocksMCA.GRAVELLING_HEADSTONE,
            BlocksMCA.UPRIGHT_HEADSTONE,
            BlocksMCA.SLANTED_HEADSTONE,
            BlocksMCA.CROSS_HEADSTONE,
            BlocksMCA.WALL_HEADSTONE
    ));

    static void bootstrap() {
        BLOCK_ENTITY_TYPES.register();
    }

    static <T extends BlockEntity> RegistrySupplier<BlockEntityType<T>> register(String name, BiFunction<BlockPos, BlockState, T> factory, List<RegistrySupplier<Block>> suppliers) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return BLOCK_ENTITY_TYPES.register(id, () -> BlockEntityType.Builder.create(
                factory::apply, suppliers.stream().map(RegistrySupplier::get).toArray(Block[]::new)
        ).build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, id.toString())));
    }
}
