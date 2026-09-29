package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/** Test-only registration for a vanilla-style bed deliberately absent from #minecraft:beds. */
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class BedPoiCompatibilityGameTests {
    private static BedBlock untaggedBed;

    private BedPoiCompatibilityGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.BLOCK) {
            return;
        }

        event.register(Registries.BLOCK, MCA.locate("gametest_untagged_bed"), () -> {
            untaggedBed = new TestBedBlock(BlockBehaviour.Properties.of().strength(0.2F).noOcclusion());
            return untaggedBed;
        });
    }

    public static BedBlock untaggedBed() {
        if (untaggedBed == null) {
            throw new IllegalStateException("GameTest bed was not registered");
        }
        return untaggedBed;
    }

    public static BlockPos placeUntaggedBedPoi(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockPos head = foot.relative(facing);
        helper.getLevel().setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState footState = untaggedBed().defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
        helper.assertTrue(helper.getLevel().getPoiManager().existsAtPosition(PoiTypes.HOME, head),
                "MCA compatibility bridge did not register the untagged BedBlock as HOME");
        return head;
    }

    private static final class TestBedBlock extends BedBlock {
        private TestBedBlock(BlockBehaviour.Properties properties) {
            super(DyeColor.RED, properties);
        }

        @Override
        @Nullable
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return null;
        }
    }
}
