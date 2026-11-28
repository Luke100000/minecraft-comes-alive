package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.poi.ExtendPoiTypesEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

/** Test-only registration for a vanilla-style bed deliberately absent from #minecraft:beds. */
@EventBusSubscriber(modid = MCA.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BedPoiCompatibilityGameTests {
    private static BedBlock untaggedBed;
    private static BedBlock registeredHomeBed;
    private static BedBlock registeredMeetingBed;

    private BedPoiCompatibilityGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.BLOCK || !Boolean.getBoolean("mca.gametest.enabled")) {
            return;
        }

        event.register(Registries.BLOCK, MCA.locate("gametest_untagged_bed"), () -> {
            untaggedBed = new TestBedBlock(BlockBehaviour.Properties.of().strength(0.2F).noOcclusion());
            return untaggedBed;
        });
        event.register(Registries.BLOCK, MCA.locate("gametest_registered_home_bed"), () -> {
            registeredHomeBed = new TestBedBlock(BlockBehaviour.Properties.of().strength(0.2F).noOcclusion());
            return registeredHomeBed;
        });
        event.register(Registries.BLOCK, MCA.locate("gametest_registered_meeting_bed"), () -> {
            registeredMeetingBed = new TestBedBlock(BlockBehaviour.Properties.of().strength(0.2F).noOcclusion());
            return registeredMeetingBed;
        });
    }

    // Simulate a mod whose own POI extension runs after MCA's original listener.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerBedPois(ExtendPoiTypesEvent event) {
        if (registeredHomeBed == null) {
            return;
        }
        event.addStatesToPoi(PoiTypes.HOME, headStates(registeredHomeBed));
        event.addStatesToPoi(PoiTypes.MEETING, headStates(registeredMeetingBed));
    }

    private static Set<BlockState> headStates(BedBlock bed) {
        return bed.getStateDefinition().getPossibleStates().stream()
                .filter(state -> state.getValue(BedBlock.PART) == BedPart.HEAD)
                .collect(Collectors.toSet());
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void lateModBedPoiRegistrationIsPreserved(GameTestHelper helper) {
        for (BlockState state : headStates(registeredHomeBed)) {
            helper.assertTrue(PoiTypes.forState(state).orElseThrow().is(PoiTypes.HOME),
                    "The mod's HOME registration was not preserved");
        }
        for (BlockState state : headStates(registeredMeetingBed)) {
            helper.assertTrue(PoiTypes.forState(state).orElseThrow().is(PoiTypes.MEETING),
                    "The mod's non-HOME registration was not preserved");
        }
        for (BlockState state : headStates(untaggedBed())) {
            helper.assertTrue(PoiTypes.forState(state).orElseThrow().is(PoiTypes.HOME),
                    "An unregistered modded bed was not registered as HOME");
        }
        helper.succeed();
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
