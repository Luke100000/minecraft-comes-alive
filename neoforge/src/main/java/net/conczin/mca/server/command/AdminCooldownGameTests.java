package net.conczin.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class AdminCooldownGameTests {
    private AdminCooldownGameTests() {
    }

    @GameTest(batch = "mca_admin_cooldown", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void buildingProcessingRateRequiresPositiveValueAndOperator(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack operator = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPermission(2);
        CommandSourceStack nonOperator = operator.withPermission(0);

        helper.assertTrue(!parses(dispatcher, operator, "mca-admin buildingProcessingRate 0"),
                "the admin command accepted a zero building cooldown");
        helper.assertTrue(!parses(dispatcher, operator, "mca-admin buildingProcessingRate -1"),
                "the admin command accepted a negative building cooldown");
        helper.assertTrue(parses(dispatcher, operator, "mca-admin buildingProcessingRate 1"),
                "the admin command rejected a one-tick building cooldown");
        helper.assertTrue(parses(dispatcher, operator, "mca-admin buildingProcessingRate 21"),
                "the admin command rejected the default building cooldown");
        helper.assertTrue(!parses(dispatcher, nonOperator, "mca-admin buildingProcessingRate 1"),
                "a non-operator could access the admin cooldown command");
        helper.succeed();
    }

    private static boolean parses(CommandDispatcher<CommandSourceStack> dispatcher,
                                  CommandSourceStack source, String command) {
        ParseResults<CommandSourceStack> result = dispatcher.parse(command, source);
        return !result.getReader().canRead() && result.getContext().getCommand() != null;
    }
}
