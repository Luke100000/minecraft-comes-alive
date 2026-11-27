package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhistleScreenRenderTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void loadingScreenSubmitsTitleAndLoadingMessage() throws Exception {
        WhistleScreen screen = screen();

        assertSubmittedText(screen, Component.translatable("gui.whistle.title").getString(),
                Component.translatable("gui.loading").getString());
    }

    @Test
    void emptyFamilySubmitsTitleAndEmptyMessage() throws Exception {
        WhistleScreen screen = screen();
        setField(WhistleScreen.class, screen, "loadingAnimationTicks", -1);

        assertSubmittedText(screen, Component.translatable("gui.whistle.title").getString(),
                Component.translatable("gui.whistle.noFamily").getString());
    }

    @Test
    void populatedFamilySubmitsTitleAndSelectionCount() throws Exception {
        WhistleScreen screen = screen();
        setField(WhistleScreen.class, screen, "loadingAnimationTicks", -1);
        setField(WhistleScreen.class, screen, "keys", List.of("first", "second"));
        setField(WhistleScreen.class, screen, "selectedIndex", 1);

        assertSubmittedText(screen, Component.translatable("gui.whistle.title").getString(), "2 / 2");
    }

    @Test
    void familyTreeSearchSubmitsVisibleTitle() throws Exception {
        FamilyTreeSearchScreen screen = screen(FamilyTreeSearchScreen::new);
        setField(FamilyTreeSearchScreen.class, screen, "buttonPage",
                new ButtonWidget(0, 0, 48, 20, Component.literal("1/1"), button -> {}));

        assertSubmittedText(screen, Component.translatable("gui.title.family_tree").getString());
    }

    @Test
    void babyNamingSubmitsVisibleTitle() throws Exception {
        NameBabyScreen screen = screen(() -> new NameBabyScreen(null, ItemStack.EMPTY));

        assertSubmittedText(screen, Component.translatable("gui.nameBaby.title").getString());
    }

    private static void assertSubmittedText(Screen screen, String... expected) throws Exception {
        List<String> submitted = new ArrayList<>();
        Field submittedText = GuiTextRenderState.class.getDeclaredField("text");
        submittedText.setAccessible(true);
        GuiRenderState state = new GuiRenderState() {
            @Override
            public void addText(GuiTextRenderState text) {
                // The real extractor has already checked alpha. Stop before GPU-backed glyph preparation.
                try {
                    submitted.add(plainText((FormattedCharSequence) submittedText.get(text)));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        };
        GuiGraphicsExtractor graphics = allocate(GuiGraphicsExtractor.class);
        setField(GuiGraphicsExtractor.class, graphics, "pose", new Matrix3x2fStack(16));
        Field scissorStack = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
        var constructor = scissorStack.getType().getDeclaredConstructor(ScreenRectangle.class);
        constructor.setAccessible(true);
        setField(GuiGraphicsExtractor.class, graphics, "scissorStack",
                constructor.newInstance(new ScreenRectangle(0, 0, screen.width, screen.height)));
        setField(GuiGraphicsExtractor.class, graphics, "guiRenderState", state);

        screen.extractRenderState(graphics, 0, 0, 0);

        assertEquals(List.of(expected), submitted, "Screen text must survive vanilla text extraction");
    }

    private static WhistleScreen screen() throws Exception {
        return screen(WhistleScreen::new);
    }

    private static <T extends Screen> T screen(Supplier<T> factory) throws Exception {
        Minecraft previous = Minecraft.getInstance();
        Minecraft client = allocate(Minecraft.class);
        setField(Minecraft.class, client, "font", new Font(null) {
            @Override
            public int width(String text) {
                return text.length() * 6;
            }

            @Override
            public int width(FormattedCharSequence text) {
                return plainText(text).length() * 6;
            }
        });
        setField(Minecraft.class, null, "instance", client);
        try {
            T screen = factory.get();
            screen.width = 640;
            screen.height = 480;
            return screen;
        } finally {
            setField(Minecraft.class, null, "instance", previous);
        }
    }

    private static String plainText(FormattedCharSequence text) {
        StringBuilder result = new StringBuilder();
        text.accept((index, style, codepoint) -> {
            result.appendCodePoint(codepoint);
            return true;
        });
        return result.toString();
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
