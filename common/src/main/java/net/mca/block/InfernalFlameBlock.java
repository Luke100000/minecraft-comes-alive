package net.mca.block;

import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class InfernalFlameBlock extends BaseFireBlock {
    public InfernalFlameBlock(BlockBehaviour.Properties settings) {
        super(settings, 2.0F);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }
}
