package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

// FIXME: 1.20 loses the DrawableHelper attachment, determine if DrawContext can replace this
public class WidgetUtils {
    public static void drawRectangle(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        context.fill(x0 + 1, y0, x1, y0 + 1, color);
        context.fill(x1 - 1, y0 + 1, x1, y1, color);
        context.fill(x0, y1 - 1, x1 - 1, y1, color);
        context.fill(x0, y0, x0 + 1, y1 - 1, color);
    }

    public static void drawTexturedQuad(GuiGraphicsExtractor context, Identifier texture, float x0, float x1, float y0, float y1, float u0, float u1, float v0, float v1) {
        context.blit(texture, Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), u0, u1, v0, v1);
    }

    public static void drawTooltip(GuiGraphicsExtractor context, Font font, Component text, int x, int y) {
        drawTooltip(context, font, List.of(text), x, y);
    }

    public static void drawTooltip(GuiGraphicsExtractor context, Font font, List<Component> text, int x, int y) {
        List<FormattedCharSequence> lines = text.stream().map(Component::getVisualOrderText).toList();
        List<ClientTooltipComponent> components = lines.stream().map(ClientTooltipComponent::create).toList();
        context.tooltip(font, components, x, y, DefaultTooltipPositioner.INSTANCE, null);
    }

    /**
     * The same as the Inventory function but with negative Z
     */
    public static void drawBackgroundEntity(GuiGraphicsExtractor context, int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        InventoryScreen.extractEntityInInventoryFollowsMouse(
                context,
                x - size,
                y - size,
                x + size,
                y + size,
                size,
                0.0f,
                mouseX,
                mouseY,
                entity
        );
    }
}
