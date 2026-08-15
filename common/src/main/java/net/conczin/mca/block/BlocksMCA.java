package net.conczin.mca.block;

import net.conczin.mca.MCA;
import net.conczin.mca.TagsMCA;
import net.conczin.mca.util.RegistryRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public interface BlocksMCA {
    Map<ResourceLocation, RegistryRef<? extends Block>> BLOCKS = new LinkedHashMap<>();

    RegistryRef<Block> ROSE_GOLD_BLOCK = register("rose_gold_block", () -> new Block(BlockBehaviour.Properties.copy(Blocks.GOLD_BLOCK)));

    RegistryRef<Block> JEWELER_WORKBENCH = register("jeweler_workbench", () -> new JewelerWorkbench(BlockBehaviour.Properties.copy(Blocks.OAK_WOOD).noOcclusion()));
    RegistryRef<Block> INFERNAL_FLAME = register("infernal_flame", () -> new InfernalFlameBlock(BlockBehaviour.Properties.copy(Blocks.SOUL_FIRE)));

    static BlockBehaviour.Properties headstoneSettings(Block base) {
        return BlockBehaviour.Properties.copy(base).noOcclusion().strength(3.0F, 1200.0F);
    }

    RegistryRef<Block> GRAVELLING_HEADSTONE = register("gravelling_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 100, 50, new Vec3(0, -25, 40), -90.0f,true, TombstoneBlock.GRAVELLING_SHAPE));
    RegistryRef<Block> UPRIGHT_HEADSTONE = register("upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistryRef<Block> SLANTED_HEADSTONE = register("slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));
    RegistryRef<Block> CROSS_HEADSTONE = register("cross_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 80, 15, new Vec3(0, -13, 15),-45.0f, true, TombstoneBlock.CROSS_SHAPE));
    RegistryRef<Block> WALL_HEADSTONE = register("wall_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 100, 15, new Vec3(0, -25, 40),0.0f, false, TombstoneBlock.WALL_SHAPE));

    RegistryRef<Block> COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.COBBLESTONE), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistryRef<Block> COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.COBBLESTONE), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistryRef<Block> WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.OAK_WOOD), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistryRef<Block> WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.OAK_WOOD), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistryRef<Block> GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistryRef<Block> GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistryRef<Block> DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistryRef<Block> DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    static void bootstrap() {
        TagsMCA.Blocks.bootstrap();
    }

    static <T extends Block> RegistryRef<T> register(String name, Supplier<T> block) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<T> ref = RegistryRef.of(id, block);
        BLOCKS.put(id, ref);
        return ref;
    }

    static void registerBlocks(MCA.RegisterHelper<Block> helper) {
        BLOCKS.forEach((id, ref) -> helper.register(id, ref.get()));
    }
}
