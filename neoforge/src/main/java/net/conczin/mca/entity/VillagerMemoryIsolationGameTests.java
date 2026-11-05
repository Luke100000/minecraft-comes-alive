package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillagerMemoryIsolationGameTests {
    private VillagerMemoryIsolationGameTests() {
    }

    @GameTest(batch = "mca_memory_isolation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void twoVillagersKeepIndependentPlayerMemoriesAndPacketSnapshots(GameTestHelper helper) {
        BlockPos firstPos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos secondPos = helper.absolutePos(new BlockPos(5, 1, 3));
        VillagerEntityMCA first = VillagerFactory.newVillager(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(firstPos))
                .spawn(EntitySpawnReason.COMMAND);
        VillagerEntityMCA second = VillagerFactory.newVillager(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(secondPos))
                .spawn(EntitySpawnReason.COMMAND);

        try {
            UUID playerId = UUID.randomUUID();
            helper.assertTrue(first.getVillagerBrain().getMemories().isEmpty(), "first villager did not start with empty memories");
            helper.assertTrue(second.getVillagerBrain().getMemories().isEmpty(), "second villager did not start with empty memories");

            // Clear registration-time updates so the dirty check covers this memory update.
            first.getEntityData().packDirty();
            Memories firstMemory = new Memories(first.getVillagerBrain(), helper.getLevel().getGameTime(), playerId);
            firstMemory.setHearts(42);
            helper.assertTrue(first.getEntityData().isDirty(), "memory update did not mark the tracked data dirty");
            helper.assertTrue(first.getVillagerBrain().getMemories().get(playerId).getHearts() == 42,
                    "first villager did not retain its player memories");
            helper.assertTrue(second.getVillagerBrain().getMemories().isEmpty(),
                    "first villager's memories leaked into the second villager");

            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> packetValues =
                    first.getEntityData().packDirty();
            helper.assertTrue(packetValues != null, "no tracked data was prepared for transmission");
            CompoundTag packetMemories = packetValues.stream()
                    .map(net.minecraft.network.syncher.SynchedEntityData.DataValue::value)
                    .filter(CompoundTag.class::isInstance)
                    .map(CompoundTag.class::cast)
                    .filter(tag -> tag.contains(playerId.toString()))
                    .findFirst().orElseThrow();

            Memories secondMemory = new Memories(second.getVillagerBrain(), helper.getLevel().getGameTime(), playerId);
            secondMemory.setHearts(7);
            firstMemory.setHearts(99);
            helper.assertTrue(first.getVillagerBrain().getMemories().get(playerId).getHearts() == 99,
                    "first villager did not retain the updated memory");
            helper.assertTrue(second.getVillagerBrain().getMemories().get(playerId).getHearts() == 7,
                    "second villager's memory changed with the first villager");
            helper.assertTrue(packetMemories.getCompound(playerId.toString()).orElseThrow()
                    .getInt("hearts").orElseThrow() == 42,
                    "the packet snapshot retained a mutable reference to live memories");
        } finally {
            first.discard();
            second.discard();
        }
        helper.succeed();
    }
}
