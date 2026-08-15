package net.conczin.mca.block;

import net.conczin.mca.MCA;
import net.conczin.mca.util.RegistryRef;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public interface BlockEntityTypesMCA {
    Map<ResourceLocation, RegistryRef<? extends BlockEntityType<?>>> BLOCK_ENTITY_TYPES = new LinkedHashMap<>();

    RegistryRef<BlockEntityType<TombstoneBlock.Data>> TOMBSTONE = register("tombstone", TombstoneBlock.Data::new, List.of(
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

    static <T extends BlockEntity> RegistryRef<BlockEntityType<T>> register(String name, BiFunction<BlockPos, BlockState, T> factory, List<RegistryRef<Block>> suppliers) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<BlockEntityType<T>> ref = RegistryRef.of(id, () -> BlockEntityType.Builder.of(
                factory::apply, suppliers.stream().map(RegistryRef::get).toArray(Block[]::new)
        ).build(Util.fetchChoiceType(References.BLOCK_ENTITY, id.toString())));
        BLOCK_ENTITY_TYPES.put(id, ref);
        return ref;
    }

    static void registerBlockEntityTypes(MCA.RegisterHelper<BlockEntityType<?>> helper) {
        BLOCK_ENTITY_TYPES.forEach((id, ref) -> helper.register(id, ref.get()));
    }
}
