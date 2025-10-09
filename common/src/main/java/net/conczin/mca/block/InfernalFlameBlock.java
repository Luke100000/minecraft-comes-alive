package net.conczin.mca.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class InfernalFlameBlock extends BaseFireBlock {
    public static final MapCodec<InfernalFlameBlock> CODEC = simpleCodec(InfernalFlameBlock::new);

    public InfernalFlameBlock(BlockBehaviour.Properties settings) {
        super(settings, 2.0F);
    }

    @Override
    protected MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }
}
