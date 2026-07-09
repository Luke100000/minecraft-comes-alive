package net.mca.block;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.TagsMCA;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public interface BlocksMCA {
    DeferredRegister<Block> BLOCKS = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.BLOCK);

    RegistrySupplier<Block> ROSE_GOLD_BLOCK = register("rose_gold_block", () -> new Block(Block.Settings.copy(Blocks.GOLD_BLOCK)));

    RegistrySupplier<Block> JEWELER_WORKBENCH = register("jeweler_workbench", () -> new JewelerWorkbench(Block.Settings.copy(Blocks.OAK_WOOD).nonOpaque()));
    RegistrySupplier<Block> INFERNAL_FLAME = register("infernal_flame", () -> new InfernalFlameBlock(Block.Settings.copy(Blocks.SOUL_FIRE)));

    static Block.Settings headstoneSettings(Block base) {
        return Block.Settings.copy(base).nonOpaque().strength(3.0F, 1200.0F);
    }

    RegistrySupplier<Block> GRAVELLING_HEADSTONE = register("gravelling_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 100, 50, new Vec3d(0, -25, 40), -90.0f,true, TombstoneBlock.GRAVELLING_SHAPE));
    RegistrySupplier<Block> UPRIGHT_HEADSTONE = register("upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 70, 30, new Vec3d(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> SLANTED_HEADSTONE = register("slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 90, 15, new Vec3d(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));
    RegistrySupplier<Block> CROSS_HEADSTONE = register("cross_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 80, 15, new Vec3d(0, -13, 15),-45.0f, true, TombstoneBlock.CROSS_SHAPE));
    RegistrySupplier<Block> WALL_HEADSTONE = register("wall_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.STONE), 100, 15, new Vec3d(0, -25, 40),0.0f, false, TombstoneBlock.WALL_SHAPE));

    RegistrySupplier<Block> COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.COBBLESTONE), 70, 30, new Vec3d(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.COBBLESTONE), 90, 15, new Vec3d(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistrySupplier<Block> WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.OAK_WOOD), 70, 30, new Vec3d(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.OAK_WOOD), 90, 15, new Vec3d(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistrySupplier<Block> GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 70, 30, new Vec3d(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 90, 15, new Vec3d(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistrySupplier<Block> DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 70, 30, new Vec3d(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", () -> new TombstoneBlock(headstoneSettings(Blocks.DEEPSLATE), 90, 15, new Vec3d(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    static void bootstrap() {
        BLOCKS.register();
        TagsMCA.Blocks.bootstrap();
        BlockEntityTypesMCA.bootstrap();
    }

    static <T extends Block> RegistrySupplier<T> register(String name, Supplier<T> block) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return BLOCKS.register(id, block);
    }
}
