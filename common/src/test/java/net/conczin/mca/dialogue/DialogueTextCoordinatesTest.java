package net.conczin.mca.dialogue;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueTextCoordinatesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void structureCoordinatesAreTealUnderlinedAndCopyOnlyCoordinates() {
        Component component = DialogueEngine.styledStructureCoordinates("234,70,-980");
        assertEquals("234,70,-980", component.getString());
        Style style = component.getStyle();
        assertEquals(TextColor.fromRgb(0x237B80), style.getColor());
        assertTrue(style.isUnderlined());
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, style.getClickEvent().getAction());
        assertEquals("234 70 -980", style.getClickEvent().getValue());
        assertEquals(Component.translatable("gui.dialogue.copy_coordinates"),
                style.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT));
    }

    @Test
    void invalidResolverDataRemainsPlainAndNonClickable() {
        for (String invalid : new String[]{"", "123,67", "0,0,99999999999", "one,64,three"}) {
            Component component = DialogueEngine.styledStructureCoordinates(invalid);
            assertEquals(invalid, component.getString());
            assertNull(component.getStyle().getClickEvent());
            assertNull(component.getStyle().getHoverEvent());
        }
    }
}
