package mca.client.gui;

import java.util.*;

import mca.client.resources.Icon;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public abstract class AbstractDynamicScreen extends Screen {
    protected static final float iconScale = 1.5f;

    // Tracks which page we're on in the GUI for sending button events
    private String activeScreen = "main";

    private int mouseX;
    private int mouseY;

    private Set<Constraint> constraints = new HashSet<>();

    protected AbstractDynamicScreen(Text title) {
        super(title);
    }

    public String getActiveScreen() {
        return activeScreen;
    }

    public Set<Constraint> getConstraints() {
        return constraints;
    }

    public void setConstraints(Set<Constraint> constraints) {
        this.constraints = constraints;
        setLayout(activeScreen);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    protected abstract void buttonPressed(Button button);

    protected void disableButton(String id) {
        children().forEach(b -> {
            if (b instanceof ButtonEx) {
                if (((ButtonEx)b).getApiButton().identifier().equals(id)) {
                    ((ButtonEx)b).active = false;
                }
            }
        });
    }

    protected void enableAllButtons() {
        children().forEach(b -> {
            if (b instanceof ClickableWidget) {
                ((ClickableWidget)b).active = true;
            }
        });
    }

    protected void disableAllButtons() {
        this.children().forEach(b -> {
            if (b instanceof ClickableWidget) {
                if (b instanceof ButtonEx) {
                    if (!((ButtonEx)b).getApiButton().identifier().equals("gui.button.backarrow")) {
                        ((ClickableWidget)b).active = false;
                    }
                } else {
                    ((ClickableWidget)b).active = false;
                }
            }
        });
    }

    /**
     * Adds API buttons to the GUI screen provided.
     *
     * @param guiKey String key for the GUI's buttons
     */
    public void setLayout(String guiKey) {
        activeScreen = guiKey;

        clearChildren();
        MCAScreens.getInstance().getScreen(guiKey).ifPresent(buttons -> {
            for (Button b : buttons) {
                addDrawableChild(new ButtonEx(b, this));
            }
        });
    }

    protected void drawIcon(MatrixStack transform, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        this.drawTexture(transform, (int)(icon.x() / iconScale), (int)(icon.y() / iconScale), icon.u(), icon.v(), 16, 16);
    }

    protected void drawHoveringIconText(MatrixStack transform, Text text, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        renderTooltip(transform, text, icon.x() + 16, icon.y() + 20);
    }

    protected void drawHoveringIconText(MatrixStack transform, List<Text> text, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        renderTooltip(transform, text, icon.x() + 16, icon.y() + 20);
    }

    //checks if the mouse hovers over a specified button
    protected boolean hoveringOverIcon(String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        return hoveringOver(icon.x(), icon.y(), (int)(16 * iconScale), (int)(16 * iconScale));
    }

    //checks if the mouse hovers over a rectangle
    protected boolean hoveringOver(int x, int y, int w, int h) {
        return mouseX > x && mouseX < x + w && mouseY > y && mouseY < y + h;
    }

    private static class ButtonEx extends ButtonWidget {
        private final Button apiButton;

        public ButtonEx(Button apiButton, AbstractDynamicScreen screen) {
            super((int)(screen.width * Alignment.alignments.get(apiButton.align()).h + apiButton.x()),
                    (int)(screen.height * Alignment.alignments.get(apiButton.align()).v + apiButton.y()),
                    apiButton.width(),
                    apiButton.height(),
                    new TranslatableText(apiButton.identifier()),
                    a -> screen.buttonPressed(apiButton));
            this.apiButton = apiButton;

            // Remove the button if we specify it should not be present on constraint failure
            // Otherwise we just mark the button as disabled.
            if (!apiButton.isValidForConstraint(screen.getConstraints())) {
                if (apiButton.hideOnFail()) {
                    visible = false;
                }
                active = false;
            }
        }

        public Button getApiButton() {
            return apiButton;
        }
    }

    private enum Alignment {
        TOP_LEFT(0.0f, 0.0f),
        TOP(0.5f, 0.0f),
        TOP_RIGHT(1.0f, 0.0f),
        RIGHT(1.0f, 0.5f),
        BOTTOM_RIGHT(1.0f, 1.0f),
        BOTTOM(0.5f, 1.0f),
        BOTTOM_LEFT(0.0f, 1.0f),
        LEFT(0.0f, 0.5f),
        CENTER(0.5f, 0.5f);

        final float h;
        final float v;

        static final Map<String, Alignment> alignments = new HashMap<>();

        static {
            for (Alignment a : Alignment.values()) {
                alignments.put(a.name().toLowerCase(Locale.ENGLISH), a);
            }
        }

        Alignment(float h, float v) {
            this.h = h;
            this.v = v;
        }
    }
}
