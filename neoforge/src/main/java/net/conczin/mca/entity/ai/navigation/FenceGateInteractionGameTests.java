package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FenceGateInteractionGameTests {
    private FenceGateInteractionGameTests() {
    }

    @GameTest(batch = "mca_fence_gate_config", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void fenceGateInteractionControlsPathingAndOpening(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 4));
        BlockPos gatePos = helper.absolutePos(new BlockPos(5, 2, 4));
        helper.getLevel().setBlock(gatePos, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);

        Config config = Config.getInstance();
        boolean original = config.villagersInteractWithFenceGates;
        try {
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            PathfindingContext context = new PathfindingContext(helper.getLevel(), villager);

            config.villagersInteractWithFenceGates = true;
            helper.assertTrue(PathingBlockInteraction.isOpenable(helper.getLevel().getBlockState(gatePos)),
                    "enabled fence-gate interaction did not make the gate openable");
            helper.assertTrue(evaluator.getPathType(context, gatePos.getX(), gatePos.getY(), gatePos.getZ()) == PathType.WALKABLE_DOOR,
                    "enabled fence-gate interaction did not make the closed gate pathable");

            config.villagersInteractWithFenceGates = false;
            helper.assertTrue(!PathingBlockInteraction.isOpenable(helper.getLevel().getBlockState(gatePos)),
                    "disabled fence-gate interaction still allowed the gate to be opened");
            helper.assertTrue(evaluator.getPathType(context, gatePos.getX(), gatePos.getY(), gatePos.getZ()) == PathType.FENCE,
                    "disabled fence-gate interaction did not restore vanilla closed-gate pathing");

            helper.succeed();
        } finally {
            config.villagersInteractWithFenceGates = original;
            villager.discard();
        }
    }
}
