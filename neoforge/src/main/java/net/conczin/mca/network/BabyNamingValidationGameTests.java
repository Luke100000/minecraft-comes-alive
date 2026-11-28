package net.conczin.mca.network;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.network.c2s.BabyNamingVillagerMessage;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class BabyNamingValidationGameTests {
    private BabyNamingValidationGameTests() {
    }

    @GameTest(batch = "mca_baby_naming_validation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void malformedSlotsDoNotRenameOrThrow(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.getInventory().selected = 0;
        ItemStack baby = new ItemStack(ItemsMCA.BABY_BOY);
        player.getInventory().setItem(0, baby);
        ItemStack anotherBaby = new ItemStack(ItemsMCA.BABY_GIRL);
        player.getInventory().setItem(1, anotherBaby);

        for (int invalidSlot : new int[]{-1, 9, Integer.MAX_VALUE, 1}) {
            new BabyNamingVillagerMessage(invalidSlot, "Forged name").handle(player);
        }

        helper.assertTrue(!baby.has(DataComponents.CUSTOM_NAME) && !anotherBaby.has(DataComponents.CUSTOM_NAME),
                "malformed or unselected slot changed an inventory item");
        helper.succeed();
    }

    @GameTest(batch = "mca_baby_naming_validation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void selectedNonBabyCannotBeNamed(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.getInventory().selected = 0;
        ItemStack ordinaryItem = new ItemStack(Items.DIAMOND);
        player.getInventory().setItem(0, ordinaryItem);

        new BabyNamingVillagerMessage(0, "Forged name").handle(player);

        helper.assertTrue(!ordinaryItem.has(DataComponents.CUSTOM_NAME),
                "packet renamed an ordinary item in the selected slot");
        helper.succeed();
    }

    @GameTest(batch = "mca_baby_naming_validation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyAndExcessiveNamesAreRejected(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.getInventory().selected = 0;
        ItemStack baby = new ItemStack(ItemsMCA.BABY_BOY);
        player.getInventory().setItem(0, baby);

        for (String invalidName : new String[]{"", "   ", "a".repeat(33)}) {
            new BabyNamingVillagerMessage(0, invalidName).handle(player);
        }

        helper.assertTrue(!baby.has(DataComponents.CUSTOM_NAME),
                "packet accepted an empty or overlong baby name");
        helper.succeed();
    }

    @GameTest(batch = "mca_baby_naming_validation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void selectedBabyIsNamedAtHotbarEndsAndPreservesData(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (int selectedSlot : new int[]{0, 8}) {
            player.getInventory().selected = selectedSlot;
            ItemStack baby = new ItemStack(ItemsMCA.BABY_BOY);
            baby.set(DataComponentsMCA.BABY_AGE, 17);
            player.getInventory().setItem(selectedSlot, baby);

            new BabyNamingVillagerMessage(selectedSlot, "  Alex  ").handle(player);

            helper.assertTrue(baby.has(DataComponents.CUSTOM_NAME)
                            && "Alex".equals(baby.get(DataComponents.CUSTOM_NAME).getString()),
                    "valid selected baby name was not trimmed and applied");
            helper.assertTrue(baby.getOrDefault(DataComponentsMCA.BABY_AGE, 0) == 17,
                    "naming changed the baby's age component");
            new BabyNamingVillagerMessage(selectedSlot, "Robin").handle(player);
            helper.assertTrue("Robin".equals(baby.get(DataComponents.CUSTOM_NAME).getString()),
                    "naming unexpectedly rejected an already named baby");
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "baby-name-test"));
    }
}
