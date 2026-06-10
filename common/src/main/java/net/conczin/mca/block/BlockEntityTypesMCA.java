package net.conczin.mca.block;

import net.conczin.mca.MCA;
import net.conczin.mca.registry.BlocksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntityTypesMCA {
    public static BlockEntityType<TombstoneBlock.Data> TOMBSTONE;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerBlockEntityTypes(TriFunction register) {
        TOMBSTONE = register.apply(
                MCA.locate("tombstone"),
                TombstoneBlock.Data::new,
                new Block[]{
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
                }
        );
    }

    public interface TriFunction<E extends BlockEntity> {
        @SuppressWarnings("rawtypes")
        BlockEntityType<E> apply(Identifier name, BlockEntitySupplier constructor, Block[] block);
    }

    public interface BlockEntitySupplier<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
