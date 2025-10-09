package net.mca.block;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.TagsMCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.function.Supplier;

public interface BlocksMCA {
    DeferredRegister<Block> BLOCKS = DeferredRegister.create(MCA.MOD_ID, Registries.BLOCK);

    RegistrySupplier<Block> ROSE_GOLD_BLOCK = register("rose_gold_block", () -> new Block(Block.Properties.copy(Blocks.GOLD_BLOCK)));

    RegistrySupplier<Block> JEWELER_WORKBENCH = register("jeweler_workbench", () -> new JewelerWorkbench(Block.Properties.copy(Blocks.OAK_WOOD).noOcclusion()));
    RegistrySupplier<Block> INFERNAL_FLAME = register("infernal_flame", () -> new InfernalFlameBlock(Block.Properties.copy(Blocks.SOUL_FIRE)));

    RegistrySupplier<Block> GRAVELLING_HEADSTONE = register("gravelling_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.STONE).noOcclusion(), 100, 50, new Vec3(0, -25, 40), -90.0f,true, TombstoneBlock.GRAVELLING_SHAPE));
    RegistrySupplier<Block> UPRIGHT_HEADSTONE = register("upright_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.STONE).noOcclusion(), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> SLANTED_HEADSTONE = register("slanted_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.STONE).noOcclusion(), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));
    RegistrySupplier<Block> CROSS_HEADSTONE = register("cross_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.STONE).noOcclusion(), 80, 15, new Vec3(0, -13, 15),-45.0f, true, TombstoneBlock.CROSS_SHAPE));
    RegistrySupplier<Block> WALL_HEADSTONE = register("wall_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.STONE).noOcclusion(), 100, 15, new Vec3(0, -25, 40),0.0f, false, TombstoneBlock.WALL_SHAPE));

    RegistrySupplier<Block> COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.COBBLESTONE).noOcclusion(), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.COBBLESTONE).noOcclusion(), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistrySupplier<Block> WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.OAK_WOOD).noOcclusion(), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.OAK_WOOD).noOcclusion(), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistrySupplier<Block> GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.DEEPSLATE).noOcclusion(), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.DEEPSLATE).noOcclusion(), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    RegistrySupplier<Block> DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.DEEPSLATE).noOcclusion(), 70, 30, new Vec3(0, -30, -8),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", () -> new TombstoneBlock(Block.Properties.copy(Blocks.DEEPSLATE).noOcclusion(), 90, 15, new Vec3(0, -12, 22), -72.5f,true, TombstoneBlock.SLANTED_SHAPE));

    static void bootstrap() {
        BLOCKS.register();
        TagsMCA.Blocks.bootstrap();
        BlockEntityTypesMCA.bootstrap();
    }

    static <T extends Block> RegistrySupplier<T> register(String name, Supplier<T> block) {
        ResourceLocation id = new ResourceLocation(MCA.MOD_ID, name);
        return BLOCKS.register(id, block);
    }
}
