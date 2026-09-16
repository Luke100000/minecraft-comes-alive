package net.conczin.mca.block;

import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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
}
