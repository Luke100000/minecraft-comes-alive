package net.conczin.mca.registry;

import net.conczin.mca.MCA;
import net.conczin.mca.block.InfernalFlameBlock;
import net.conczin.mca.block.TombstoneBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public interface BlocksMCA {
    Map<String, Block> BLOCKS = new HashMap<>();

    Block ROSE_GOLD_BLOCK = register("rose_gold_block", new Block(properties("rose_gold_block", Block.Properties.ofFullCopy(Blocks.GOLD_BLOCK))));

    Block INFERNAL_FLAME = register("infernal_flame", new InfernalFlameBlock(properties("infernal_flame", Block.Properties.ofFullCopy(Blocks.SOUL_FIRE))));

    Block GRAVELLING_HEADSTONE = register("gravelling_headstone", new TombstoneBlock(properties("gravelling_headstone", Block.Properties.ofFullCopy(Blocks.STONE).noOcclusion()), 100, 50, new Vec3(0, -25, 40), -90.0f, true, TombstoneBlock.GRAVELLING_SHAPE));
    Block UPRIGHT_HEADSTONE = register("upright_headstone", new TombstoneBlock(properties("upright_headstone", Block.Properties.ofFullCopy(Blocks.STONE).noOcclusion()), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block SLANTED_HEADSTONE = register("slanted_headstone", new TombstoneBlock(properties("slanted_headstone", Block.Properties.ofFullCopy(Blocks.STONE).noOcclusion()), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));
    Block CROSS_HEADSTONE = register("cross_headstone", new TombstoneBlock(properties("cross_headstone", Block.Properties.ofFullCopy(Blocks.STONE).noOcclusion()), 80, 15, new Vec3(0, -13, 15), -45.0f, true, TombstoneBlock.CROSS_SHAPE));
    Block WALL_HEADSTONE = register("wall_headstone", new TombstoneBlock(properties("wall_headstone", Block.Properties.ofFullCopy(Blocks.STONE).noOcclusion()), 100, 15, new Vec3(0, -25, 40), 0.0f, false, TombstoneBlock.WALL_SHAPE));

    Block COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", new TombstoneBlock(properties("cobblestone_upright_headstone", Block.Properties.ofFullCopy(Blocks.COBBLESTONE).noOcclusion()), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", new TombstoneBlock(properties("cobblestone_slanted_headstone", Block.Properties.ofFullCopy(Blocks.COBBLESTONE).noOcclusion()), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    Block WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", new TombstoneBlock(properties("wooden_upright_headstone", Block.Properties.ofFullCopy(Blocks.OAK_WOOD).noOcclusion()), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", new TombstoneBlock(properties("wooden_slanted_headstone", Block.Properties.ofFullCopy(Blocks.OAK_WOOD).noOcclusion()), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    Block GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", new TombstoneBlock(properties("golden_upright_headstone", Block.Properties.ofFullCopy(Blocks.DEEPSLATE).noOcclusion()), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", new TombstoneBlock(properties("golden_slanted_headstone", Block.Properties.ofFullCopy(Blocks.DEEPSLATE).noOcclusion()), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    Block DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", new TombstoneBlock(properties("deepslate_upright_headstone", Block.Properties.ofFullCopy(Blocks.DEEPSLATE).noOcclusion()), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", new TombstoneBlock(properties("deepslate_slanted_headstone", Block.Properties.ofFullCopy(Blocks.DEEPSLATE).noOcclusion()), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    static Block register(String name, Block block) {
        BLOCKS.put(name, block);
        return block;
    }

    static Block.Properties properties(String name, Block.Properties properties) {
        return properties.setId(ResourceKey.create(Registries.BLOCK, MCA.locate(name)));
    }

    static void registerBlocks(MCA.RegisterHelper<Block> helper) {
        BLOCKS.forEach((name, block) -> helper.register(MCA.locate(name), block));
    }
}
