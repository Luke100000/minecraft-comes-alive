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

    Block ROSE_GOLD_BLOCK = register("rose_gold_block", new Block(baseProps("rose_gold_block", Blocks.GOLD_BLOCK)));

    Block INFERNAL_FLAME = register("infernal_flame", new InfernalFlameBlock(baseProps("infernal_flame", Blocks.SOUL_FIRE)));

    Block GRAVELLING_HEADSTONE = register("gravelling_headstone", new TombstoneBlock(tombstoneProps("gravelling_headstone", Blocks.STONE), 100, 50, new Vec3(0, -25, 40), -90.0f, true, TombstoneBlock.GRAVELLING_SHAPE));
    Block UPRIGHT_HEADSTONE = register("upright_headstone", new TombstoneBlock(tombstoneProps("upright_headstone", Blocks.STONE), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block SLANTED_HEADSTONE = register("slanted_headstone", new TombstoneBlock(tombstoneProps("slanted_headstone", Blocks.STONE), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));
    Block CROSS_HEADSTONE = register("cross_headstone", new TombstoneBlock(tombstoneProps("cross_headstone", Blocks.STONE), 80, 15, new Vec3(0, -13, 15), -45.0f, true, TombstoneBlock.CROSS_SHAPE));
    Block WALL_HEADSTONE = register("wall_headstone", new TombstoneBlock(tombstoneProps("wall_headstone", Blocks.STONE), 100, 15, new Vec3(0, -25, 40), 0.0f, false, TombstoneBlock.WALL_SHAPE));

    Block COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", new TombstoneBlock(tombstoneProps("cobblestone_upright_headstone", Blocks.COBBLESTONE), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", new TombstoneBlock(tombstoneProps("cobblestone_slanted_headstone", Blocks.COBBLESTONE), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    Block WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", new TombstoneBlock(tombstoneProps("wooden_upright_headstone", Blocks.OAK_WOOD), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", new TombstoneBlock(tombstoneProps("wooden_slanted_headstone", Blocks.OAK_WOOD), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    Block GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", new TombstoneBlock(tombstoneProps("golden_upright_headstone", Blocks.DEEPSLATE), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", new TombstoneBlock(tombstoneProps("golden_slanted_headstone", Blocks.DEEPSLATE), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    Block DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", new TombstoneBlock(tombstoneProps("deepslate_upright_headstone", Blocks.DEEPSLATE), 70, 30, new Vec3(0, -30, -8), 0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    Block DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", new TombstoneBlock(tombstoneProps("deepslate_slanted_headstone", Blocks.DEEPSLATE), 90, 15, new Vec3(0, -12, 22), -72.5f, true, TombstoneBlock.SLANTED_SHAPE));

    static Block register(String name, Block block) {
        BLOCKS.put(name, block);
        return block;
    }

    static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, MCA.locate(name));
    }

    static Block.Properties baseProps(String name, Block block) {
        return Block.Properties.ofFullCopy(block).setId(blockKey(name));
    }

    static Block.Properties tombstoneProps(String name, Block block) {
        return baseProps(name, block).noOcclusion().explosionResistance(9.0F);
    }

    static void registerBlocks(MCA.RegisterHelper<Block> helper) {
        BLOCKS.forEach((name, block) -> helper.register(MCA.locate(name), block));
    }
}
