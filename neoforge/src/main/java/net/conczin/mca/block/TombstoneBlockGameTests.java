package net.conczin.mca.block;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeViewBuilder;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class TombstoneBlockGameTests {
    private TombstoneBlockGameTests() {
    }

    @GameTest(batch = "mca_tombstone_drops", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void tombstoneDropsItselfWhenMined(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState state = BlocksMCA.CROSS_HEADSTONE.defaultBlockState();
        helper.getLevel().setBlock(grave, state, 3);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(grave);

        ItemStack normalPickaxe = new ItemStack(Items.IRON_PICKAXE);
        List<ItemStack> normalDrops = Block.getDrops(state, helper.getLevel(), grave, blockEntity, null, normalPickaxe);
        helper.assertTrue(normalDrops.stream().anyMatch(stack -> stack.is(Items.COBBLESTONE)),
                "mining a cross headstone without Silk Touch must use its normal loot table");

        ItemStack silkTouchPickaxe = new ItemStack(Items.IRON_PICKAXE);
        silkTouchPickaxe.enchant(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),
                1
        );
        List<ItemStack> silkTouchDrops = Block.getDrops(state, helper.getLevel(), grave, blockEntity, null, silkTouchPickaxe);
        helper.assertTrue(silkTouchDrops.stream().anyMatch(stack -> stack.is(ItemsMCA.CROSS_HEADSTONE)),
                "mining a cross headstone with Silk Touch must drop the cross headstone item");
        helper.succeed();
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedTombstoneRegistersExactGraveLocation(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(5, 1, 1), "Indexed Deceased");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);

        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        GlobalPos expected = GlobalPos.of(helper.getLevel().dimension(), grave);
        helper.assertTrue(GraveyardManager.get(helper.getLevel()).getOccupiedGrave(deceased.getUUID())
                        .filter(expected::equals).isPresent(),
                "occupied tombstone must register its deceased UUID at the exact global position");
        helper.succeed();
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void removingOccupiedTombstoneClearsExactGraveLocation(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(5, 1, 1), "Removed Deceased");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        helper.getLevel().setBlock(grave, Blocks.AIR.defaultBlockState(), 3);

        helper.assertTrue(GraveyardManager.get(helper.getLevel()).getOccupiedGrave(deceased.getUUID()).isEmpty(),
                "removing an occupied tombstone must clear its exact grave mapping");
        helper.succeed();
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void replacingOccupiedTombstoneMovesExactGraveLocation(GameTestHelper helper) {
        BlockPos oldGrave = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos newGrave = helper.absolutePos(new BlockPos(3, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Moved Deceased");
        helper.getLevel().setBlock(oldGrave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data oldData = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(oldGrave)).orElseThrow();
        oldData.setEntity(deceased);
        ItemStack preserved = new ItemStack(ItemsMCA.CROSS_HEADSTONE);
        oldData.writeToStack(preserved);

        helper.getLevel().setBlock(oldGrave, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(newGrave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(newGrave)).orElseThrow().readFromStack(preserved);

        GlobalPos expected = GlobalPos.of(helper.getLevel().dimension(), newGrave);
        helper.assertTrue(GraveyardManager.get(helper.getLevel()).getOccupiedGrave(deceased.getUUID())
                        .filter(expected::equals).isPresent(),
                "replacing the preserved occupied tombstone must move the exact grave mapping");
        helper.succeed();
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void staleOldLocationCleanupPreservesReplacementLocation(GameTestHelper helper) {
        BlockPos oldGrave = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos newGrave = helper.absolutePos(new BlockPos(3, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Duplicated Deceased");
        helper.getLevel().setBlock(oldGrave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data oldData = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(oldGrave)).orElseThrow();
        oldData.setEntity(deceased);
        ItemStack preserved = new ItemStack(ItemsMCA.CROSS_HEADSTONE);
        oldData.writeToStack(preserved);
        helper.getLevel().setBlock(newGrave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(newGrave)).orElseThrow().readFromStack(preserved);

        helper.getLevel().setBlock(oldGrave, Blocks.AIR.defaultBlockState(), 3);

        GlobalPos expected = GlobalPos.of(helper.getLevel().dimension(), newGrave);
        helper.assertTrue(GraveyardManager.get(helper.getLevel()).getOccupiedGrave(deceased.getUUID())
                        .filter(expected::equals).isPresent(),
                "stale old tombstone removal must not erase a newer replacement mapping");
        helper.succeed();
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 650)
    public static void resurrectionClearsExactGraveLocation(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Resurrected Deceased");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        data.startResurrecting(false);

        helper.runAfterDelay(520, () -> {
            helper.assertTrue(GraveyardManager.get(helper.getLevel()).getOccupiedGrave(deceased.getUUID()).isEmpty(),
                    "resurrection must clear the exact grave mapping");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedGraveIndexDoesNotLoadDistantChunk(GameTestHelper helper) {
        BlockPos distant = helper.absolutePos(new BlockPos(4_096, 1, 4_096));
        helper.assertTrue(!helper.getLevel().isLoaded(distant),
                "fixture requires the distant grave chunk to start unloaded");
        UUID deceasedId = UUID.randomUUID();
        GlobalPos grave = GlobalPos.of(helper.getLevel().dimension(), distant);
        GraveyardManager manager = GraveyardManager.get(helper.getLevel());

        manager.setOccupiedGrave(deceasedId, grave);
        helper.assertTrue(manager.getOccupiedGrave(deceasedId).filter(grave::equals).isPresent(),
                "exact grave lookup must return the indexed distant location");
        manager.clearOccupiedGrave(deceasedId, grave);

        helper.assertTrue(!helper.getLevel().isLoaded(distant),
                "grave index registration, lookup, and removal must not force-load distant chunks");
        helper.succeed();
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedGraveIndexIsSharedAcrossDimensions(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "fixture requires the Nether level");
        BlockPos gravePos = new BlockPos(12, 64, -31);
        VillagerEntityMCA deceased = VillagerFactory.newVillager(nether)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(gravePos.offset(3, 0, 0)))
                .withName("Cross-Dimension Deceased")
                .spawn(EntitySpawnReason.STRUCTURE);
        nether.setBlock(gravePos, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);

        TombstoneBlock.Data.of(nether.getBlockEntity(gravePos)).orElseThrow().setEntity(deceased);

        GlobalPos grave = GlobalPos.of(Level.NETHER, gravePos);
        helper.assertTrue(GraveyardManager.getGlobal(overworld).getOccupiedGrave(deceased.getUUID())
                        .filter(grave::equals).isPresent(),
                "exact grave ownership must be shared across dimensions");
        helper.succeed();
    }

    @GameTest(batch = "mca_tombstone_grave_index", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void spouseDeathRemainsReachableFromFamilyTreeWithExactGrave(GameTestHelper helper) {
        BlockPos deceasedPos = helper.absolutePos(new BlockPos(5, 2, 5));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlock(deceasedPos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(deceasedPos))
                .withName("Deceased Spouse")
                .spawn(EntitySpawnReason.STRUCTURE);
        VillagerEntityMCA spouse = spawnVillager(helper, new BlockPos(8, 2, 5), "Surviving Spouse");
        deceased.getRelationships().marry(spouse);
        spouse.getRelationships().marry(deceased);

        deceased.getRelationships().onDeath(helper.getLevel().damageSources().generic());

        GraveyardManager graves = GraveyardManager.getGlobal(helper.getLevel());
        GlobalPos grave = graves.getOccupiedGrave(deceased.getUUID()).orElseThrow();
        var view = FamilyTreeViewBuilder.build(
                FamilyTree.get(helper.getLevel()),
                spouse.getUUID(),
                0,
                0,
                graves::getOccupiedGrave
        ).orElseThrow();

        helper.assertTrue(view.nodes().containsKey(deceased.getUUID()),
                "widowed spouse must retain the deceased partner in the family tree");
        helper.assertTrue(view.graves().get(deceased.getUUID()).equals(grave),
                "deceased spouse in the family tree must expose the exact grave created by death handling");
        helper.succeed();
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos relativePos, String name) {
        BlockPos pos = helper.absolutePos(relativePos);
        return VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName(name)
                .spawn(EntitySpawnReason.STRUCTURE);
    }
}
