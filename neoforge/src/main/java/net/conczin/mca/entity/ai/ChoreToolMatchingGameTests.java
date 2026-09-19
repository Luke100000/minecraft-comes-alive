package net.conczin.mca.entity.ai;

import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ChoreToolMatchingGameTests {
    private ChoreToolMatchingGameTests() {
    }

    @GameTest(batch = "mca_chore_tool_matching", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void choreMatchersRecognizeTheirVanillaTools(GameTestHelper helper) {
        helper.assertTrue(Chore.CHOP.matchesTool(new ItemStack(Items.IRON_AXE)),
                "chopping did not recognize a vanilla axe");
        helper.assertTrue(Chore.HARVEST.matchesTool(new ItemStack(Items.IRON_HOE)),
                "harvesting did not recognize a vanilla hoe");
        helper.assertTrue(Chore.HUNT.matchesTool(new ItemStack(Items.IRON_SWORD)),
                "hunting did not recognize a vanilla sword");
        helper.assertTrue(Chore.FISH.matchesTool(new ItemStack(Items.FISHING_ROD)),
                "fishing did not recognize a vanilla fishing rod");
        helper.assertTrue(!Chore.PROSPECT.matchesTool(new ItemStack(Items.IRON_PICKAXE)),
                "prospecting unexpectedly accepted a tool before it is implemented");
        helper.succeed();
    }
}
