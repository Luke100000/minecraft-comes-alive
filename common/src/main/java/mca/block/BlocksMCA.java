package mca.block;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.MCA;
import mca.TagsMCA;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.OreBlock;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.util.function.Supplier;

public interface BlocksMCA {

    DeferredRegister<Block> BLOCKS = DeferredRegister.create(MCA.MOD_ID, Registry.BLOCK_KEY);

    RegistrySupplier<Block> ROSE_GOLD_BLOCK = register("rose_gold_block", () -> new Block(Block.Settings.copy(Blocks.GOLD_BLOCK)));
    RegistrySupplier<Block> ROSE_GOLD_ORE = register("rose_gold_ore", () -> new OreBlock(Block.Settings.copy(Blocks.GOLD_ORE)));

    RegistrySupplier<Block> JEWELER_WORKBENCH = register("jeweler_workbench", () -> new JewelerWorkbench(Block.Settings.copy(Blocks.OAK_WOOD).nonOpaque()));
    RegistrySupplier<Block> INFERNAL_FLAME = register("infernal_flame", () -> new InfernalFlameBlock(Block.Settings.copy(Blocks.SOUL_FIRE)));

    RegistrySupplier<Block> GRAVELLING_HEADSTONE = register("gravelling_headstone", () -> new TombstoneBlock(Block.Settings.copy(Blocks.STONE).nonOpaque(), 100, 50, new Vec3d(0, -25, 40), -90.0f,true, TombstoneBlock.GRAVELLING_SHAPE));
    RegistrySupplier<Block> UPRIGHT_HEADSTONE = register("upright_headstone", () -> new TombstoneBlock(Block.Settings.copy(Blocks.STONE).nonOpaque(), 90, 50, new Vec3d(0, -55, 23),0.0f, true, TombstoneBlock.UPRIGHT_SHAPE));
    RegistrySupplier<Block> SLANTED_HEADSTONE = register("slanted_headstone", () -> new TombstoneBlock(Block.Settings.copy(Blocks.STONE).nonOpaque(), 100, 15, new Vec3d(0, -25, 10), -45.0f,true, TombstoneBlock.SLANTED_SHAPE));
    RegistrySupplier<Block> CROSS_HEADSTONE = register("cross_headstone", () -> new TombstoneBlock(Block.Settings.copy(Blocks.STONE).nonOpaque(), 80, 15, new Vec3d(0, -13, 15),-45.0f, true, TombstoneBlock.CROSS_SHAPE));
    RegistrySupplier<Block> WALL_HEADSTONE = register("wall_headstone", () -> new TombstoneBlock(Block.Settings.copy(Blocks.STONE).nonOpaque(), 100, 15, new Vec3d(0, -25, 40),0.0f, false, TombstoneBlock.WALL_SHAPE));

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
