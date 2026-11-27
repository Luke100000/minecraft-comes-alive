package net.conczin.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class AdminCooldownGameTests {
    private AdminCooldownGameTests() {
    }

    @GameTest(batch = "mca_admin_cooldown", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void buildingProcessingRateRequiresPositiveValueAndOperator(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack operator = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPermission(LevelBasedPermissionSet.GAMEMASTER);
        CommandSourceStack moderator = operator.withPermission(LevelBasedPermissionSet.MODERATOR);

        helper.assertTrue(!parses(dispatcher, operator, "mca-admin buildingProcessingRate 0"),
                "the admin command accepted a zero building cooldown");
        helper.assertTrue(!parses(dispatcher, operator, "mca-admin buildingProcessingRate -1"),
                "the admin command accepted a negative building cooldown");
        helper.assertTrue(parses(dispatcher, operator, "mca-admin buildingProcessingRate 1"),
                "the admin command rejected a one-tick building cooldown");
        helper.assertTrue(parses(dispatcher, operator, "mca-admin buildingProcessingRate 21"),
                "the admin command rejected the default building cooldown");
        helper.assertTrue(!parses(dispatcher, moderator, "mca-admin buildingProcessingRate 1"),
                "a moderator could access the admin cooldown command");
        helper.succeed();
    }

    @GameTest(batch = "mca_admin_diagnose", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void diagnoseRequiresOperatorAndAcceptsVerboseFlag(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack operator = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPermission(LevelBasedPermissionSet.GAMEMASTER);
        helper.assertTrue(parses(dispatcher, operator, "mca-admin diagnose"), "operator cannot access diagnose");
        helper.assertTrue(parses(dispatcher, operator, "mca-admin diagnose true"), "verbose diagnose is unavailable");
        helper.assertTrue(!parses(dispatcher, operator.withPermission(LevelBasedPermissionSet.MODERATOR), "mca-admin diagnose"),
                "a moderator could access diagnose");
        helper.succeed();
    }

    private static boolean parses(CommandDispatcher<CommandSourceStack> dispatcher,
                                  CommandSourceStack source, String command) {
        ParseResults<CommandSourceStack> result = dispatcher.parse(command, source);
        return !result.getReader().canRead() && result.getContext().getCommand() != null;
    }
}
