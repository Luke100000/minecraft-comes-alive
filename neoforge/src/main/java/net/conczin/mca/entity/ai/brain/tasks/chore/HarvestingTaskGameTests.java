package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class HarvestingTaskGameTests {
    private HarvestingTaskGameTests() {
    }

    @GameTest(batch = "mca_harvesting_priority", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void matureCropIsTargetedBeforeEmptyFarmland(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos crop = start.east(3);
        BlockPos emptyFarmland = start.west(2).below();
        prepareFarmland(helper, crop.below());
        prepareFarmland(helper, emptyFarmland);
        helper.getLevel().setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE), 3);

        var cropState = helper.getLevel().getBlockState(crop);
        helper.assertTrue(cropState.getBlock() instanceof CropBlock,
                "prepared crop is not a CropBlock: " + cropState);
        CropBlock cropBlock = (CropBlock) cropState.getBlock();
        helper.assertTrue(cropBlock.isMaxAge(cropState),
                "prepared crop is not mature: " + cropState);
        helper.assertTrue(cropState.canSurvive(helper.getLevel(), crop),
                "prepared mature crop cannot survive at " + crop + ": " + cropState);

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS));
        HarvestingTask task = new HarvestingTask();
        long time = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);

        WalkTarget walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "harvesting did not publish a walk target");
        BlockPos actualTarget = walkTarget.getTarget().currentBlockPosition();
        helper.assertTrue(
                actualTarget.equals(crop),
                "harvesting target was " + actualTarget + " instead of mature crop " + crop
                        + "; empty farmland was " + emptyFarmland
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_seedless", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyFarmlandIsIgnoredWithoutSeeds(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFarmland(helper, start.east(2).below());

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        HarvestingTask task = new HarvestingTask();
        long time = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, time);

        helper.assertTrue(
                !task.canStillUse(helper.getLevel(), villager, time),
                "harvesting kept an empty-farmland target even though the villager had no seeds"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_planting", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyFarmlandIsTargetedWithSeeds(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos emptyFarmland = start.east(2).below();
        prepareFarmland(helper, emptyFarmland);

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS));
        HarvestingTask task = new HarvestingTask();
        long time = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);

        WalkTarget walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "harvesting did not publish a walk target for plantable farmland");
        helper.assertTrue(
                walkTarget.getTarget().currentBlockPosition().equals(emptyFarmland),
                "harvesting did not target empty farmland when seeds were available"
        );
        helper.succeed();
    }

    private static VillagerEntityMCA spawnHarvester(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Harvesting Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.tickCount = 20;
        villager.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.getVillagerBrain().assignJob(Chore.HARVEST, player);
        return villager;
    }

    private static void prepareFarmland(GameTestHelper helper, BlockPos farmland) {
        helper.getLevel().setBlock(farmland.below(), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(farmland, Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(farmland.above(), Blocks.AIR.defaultBlockState(), 3);
    }
}
