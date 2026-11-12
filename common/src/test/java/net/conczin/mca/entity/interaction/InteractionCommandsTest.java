package net.conczin.mca.entity.interaction;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionCommandsTest {
    @Test
    void duplicateCommandsCannotReplaceHandlers() {
        ResourceLocation addon = ResourceLocation.parse("command_test:duplicate_registry_entry");
        InteractionCommands.register(addon, context -> true,
                context -> VillagerCommandHandler.DialogueCommandResult.accepted(false));
        assertThrows(IllegalArgumentException.class,
                () -> InteractionCommands.register(addon, context -> true,
                        context -> VillagerCommandHandler.DialogueCommandResult.accepted(false)));
        assertThrows(IllegalArgumentException.class,
                () -> InteractionCommands.register(ResourceLocation.parse("mca:armor"), context -> true,
                        context -> VillagerCommandHandler.DialogueCommandResult.accepted(false)));
    }

    @Test
    void builtinAliasesAndAddonNamespacesStayDistinct() {
        assertEquals(ResourceLocation.parse("mca:divorce_confirm"),
                InteractionCommands.resolve("divorceConfirm").orElseThrow());
        assertEquals(ResourceLocation.parse("mca:profession.guard"),
                InteractionCommands.resolve("profession.guard").orElseThrow());
        assertEquals(ResourceLocation.parse("mca:location"),
                InteractionCommands.resolve("location.minecraft:village").orElseThrow(),
                "a legacy location argument may contain a structure namespace");
        ResourceLocation first = ResourceLocation.parse("one:inspect.part");
        ResourceLocation second = ResourceLocation.parse("two:inspect.part");
        InteractionCommands.register(first, context -> true,
                context -> VillagerCommandHandler.DialogueCommandResult.accepted(false));
        InteractionCommands.register(second, context -> true,
                context -> VillagerCommandHandler.DialogueCommandResult.accepted(false));
        assertEquals(first, InteractionCommands.resolve("one:inspect.part").orElseThrow());
        assertEquals(second, InteractionCommands.resolve("two:inspect.part").orElseThrow());
        assertFalse(first.equals(second));
    }

    @Test
    void unknownCommandsRejectWithoutManufacturingAliases() {
        assertTrue(InteractionCommands.resolve("unknown_command").isEmpty());
        assertTrue(InteractionCommands.resolve("unknown:inspect").isEmpty());
        assertTrue(InteractionCommands.resolve("myaddon:inspect.part.extra").isEmpty());
        assertFalse(InteractionCommands.canExecute(null));
        assertFalse(InteractionCommands.execute(null).accepted());
    }
}
