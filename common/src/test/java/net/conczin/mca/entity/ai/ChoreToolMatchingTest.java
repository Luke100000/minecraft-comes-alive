package net.conczin.mca.entity.ai;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ChoreToolMatchingTest {
    private static Method matchesTool;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            matchesTool = Chore.class.getMethod("matchesTool", ItemStack.class);
        } catch (NoSuchMethodException e) {
            fail("Chore.matchesTool(ItemStack) should centralize chore tool matching");
        }
    }

    @Test
    void choreMatchersRecognizeTheirVanillaTools() {
        assertTrue(matches(Chore.CHOP, new ItemStack(Items.IRON_AXE)));
        assertTrue(matches(Chore.HARVEST, new ItemStack(Items.IRON_HOE)));
        assertTrue(matches(Chore.HUNT, new ItemStack(Items.IRON_SWORD)));
        assertTrue(matches(Chore.FISH, new ItemStack(Items.FISHING_ROD)));
    }

    @Test
    void prospectingStaysInactiveUntilImplemented() {
        assertFalse(matches(Chore.PROSPECT, new ItemStack(Items.IRON_PICKAXE)));
    }

    private static boolean matches(Chore chore, ItemStack stack) {
        try {
            return (boolean) matchesTool.invoke(chore, stack);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }
}
