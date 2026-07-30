package net.conczin.mca.client.gui;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.DestinyMessage;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DestinyScreen extends VillagerEditorScreen {
    private static final Identifier LOGO_TEXTURE = MCA.locate("textures/banner.png");
    private static final int DESTINY_COLUMNS = 3;
    private static final int DESTINY_ROWS = 3;
    private static final int DESTINY_LOCATIONS_PER_PAGE = DESTINY_COLUMNS * DESTINY_ROWS;
    private static final int DESTINY_BUTTON_GAP = 4;
    private static final int DESTINY_BUTTON_HORIZONTAL_PADDING = 16;
    private final LinkedList<Component> story = new LinkedList<>();
    private final boolean allowTeleportation;
    private String location;
    private boolean teleported = false;
    private ButtonWidget acceptWidget;
    private int destinyPage;

    public DestinyScreen(UUID playerUUID, boolean allowTeleportation) {
        super(playerUUID, playerUUID);

        this.allowTeleportation = allowTeleportation;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean showsActiveEffects() {
        return true;
    }

    @Override
    protected boolean shouldCloseAfterSkinExport() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (page.equals("presets") || page.equals("skin")) {
                setPage("general");
                return true;
            } else if (page.equals("clothing")) {
                setPage("clothing_style");
                return true;
            } else if (page.equals("hair")) {
                setPage("hair_style");
                return true;
            } else if (isLayeredHairPage()) {
                setPage("hair_advanced");
                return true;
            } else if (page.equals("hair_advanced")) {
                setPage("hair_style");
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (!page.equals("intro") && !page.equals("story")) {
            setPage("destiny");
        }
    }

    @Override
    protected String[] getPages() {
        LinkedList<String> pages = new LinkedList<>();
        pages.add("general");
        if (Config.getServerConfig().allowBodyCustomizationInDestiny) {
            pages.add("body");
        }
        if (Config.getServerConfig().allowTraitCustomizationInDestiny) {
            pages.add("traits");
        }
        return pages.toArray(new String[]{});
    }

    private void drawScaledText(GuiGraphicsExtractor context, Component text, int x, int y, float scale) {
        final Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        context.centeredText(font, text, (int) (x / scale), (int) (y / scale), 0xffffffff);
        matrices.popMatrix();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        this.extractPanorama(context, partialTick);
        this.extractBlurredBackground(context);
        this.extractMenuBackground(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        final Matrix3x2fStack matrices = context.pose();

        switch (page) {
            case "intro" -> {
                drawScaledText(context, Component.translatable("gui.destiny.whoareyou"), width / 2, height / 2 - 24, 1.5f);
                matrices.pushMatrix();
                matrices.scale(0.25f, 0.25f);
                context.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, width * 2 - 512, -40, 0, 0, 1024, 512, 1024, 512);
                matrices.popMatrix();
            }
            case "destiny" ->
                    drawScaledText(context, Component.translatable("gui.destiny.journey"), width / 2, height / 2 - 48, 1.5f);
            case "story" -> {
                List<Component> text = FlowingText.wrap(story.getFirst(), 256);
                int y = (int) (height / 2.0 - 20 - 7.5f * text.size());
                for (Component t : text) {
                    drawScaledText(context, t, width / 2, y, 1.25f);
                    y += 15;
                }
            }
        }
    }

    @Override
    protected boolean shouldDrawEntity() {
        return !page.equals("intro") && !page.equals("destiny") && !page.equals("story") && super.shouldDrawEntity();
    }

    protected String getPath(String location) {
        String[] split = location.split(":");
        return split[split.length - 1];
    }

    private List<String> getDestinyLocations() {
        return Config.getServerConfig().destinySpawnLocations;
    }

    private MutableComponent getLocationName(String location) {
        return Component.translatableWithFallback("gui.destiny." + getPath(location), prettifyIdentifier(getPath(location)));
    }

    private String getLocationModName(String location) {
        String[] idParts = location.split(":", 2);
        return idParts.length == 2 && !idParts[0].equalsIgnoreCase("minecraft") ? prettifyIdentifier(idParts[0]) : null;
    }

    private String prettifyIdentifier(String identifier) {
        StringBuilder name = new StringBuilder();
        for (String word : identifier.split("[_\\-/]")) {
            if (!word.isEmpty()) {
                if (!name.isEmpty()) name.append(' ');
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return name.toString();
    }

    private void drawDestinyLocations(List<String> locations) {
        int pageCount = Math.max(1, (locations.size() + DESTINY_LOCATIONS_PER_PAGE - 1) / DESTINY_LOCATIONS_PER_PAGE);
        destinyPage = Math.clamp(destinyPage, 0, pageCount - 1);
        List<String> visibleLocations = locations.subList(destinyPage * DESTINY_LOCATIONS_PER_PAGE,
                Math.min((destinyPage + 1) * DESTINY_LOCATIONS_PER_PAGE, locations.size()));
        int rows = (int) Math.ceil(visibleLocations.size() / (float) DESTINY_COLUMNS);
        float offsetY = Math.max(0, DESTINY_ROWS - rows) / 2.0f;
        for (int row = 0; row < rows; row++) {
            int rowStart = row * DESTINY_COLUMNS;
            int entries = Math.min(DESTINY_COLUMNS, visibleLocations.size() - rowStart);
            int[] widths = new int[entries];
            MutableComponent[] names = new MutableComponent[entries];
            int rowWidth = DESTINY_BUTTON_GAP * Math.max(0, entries - 1);
            for (int column = 0; column < entries; column++) {
                names[column] = getLocationName(visibleLocations.get(rowStart + column));
                widths[column] = font.width(names[column]) + DESTINY_BUTTON_HORIZONTAL_PADDING;
                rowWidth += widths[column];
            }
            int buttonX = width / 2 - rowWidth / 2;
            int buttonY = (int) (height / 2.0f + (row + offsetY) * 24 - 10);
            for (int column = 0; column < entries; column++) {
                String destination = visibleLocations.get(rowStart + column);
                String modName = getLocationModName(destination);
                addRenderableWidget(modName == null
                        ? new ButtonWidget(buttonX, buttonY, widths[column], 20, names[column], sender -> selectStory(destination))
                        : new ButtonWidget(buttonX, buttonY, widths[column], 20, names[column], sender -> selectStory(destination), Component.literal(modName)));
                buttonX += widths[column] + DESTINY_BUTTON_GAP;
            }
        }
        if (pageCount > 1) {
            int y = height / 2 + 68;
            ButtonWidget previous = addRenderableWidget(new ButtonWidget(width / 2 - 68, y, 40, 20, Component.literal("<"), sender -> { destinyPage--; setPage("destiny"); }));
            previous.active = destinyPage > 0;
            ButtonWidget indicator = addRenderableWidget(new ButtonWidget(width / 2 - 24, y, 48, 20, Component.literal((destinyPage + 1) + "/" + pageCount), sender -> {}));
            indicator.active = false;
            ButtonWidget next = addRenderableWidget(new ButtonWidget(width / 2 + 28, y, 40, 20, Component.literal(">"), sender -> { destinyPage++; setPage("destiny"); }));
            next.active = destinyPage + 1 < pageCount;
        }
    }

    @Override
    protected void setPage(String page) {
        List<String> destinyLocations = page.equals("destiny") ? getDestinyLocations() : List.of();
        if (page.equals("general") && (this.page == null || this.page.equals("loading"))) {
            page = "intro";
        }

        if (page.equals("destiny") && !allowTeleportation) {
            Network.sendToServer(new DestinyMessage("", true));
            MCAClient.getDestinyManager().allowClosing();
            super.onClose();
            return;
        } else if (page.equals("destiny")) {
            //there is only one entry
            if (destinyLocations.size() == 1) {
                selectStory(destinyLocations.getFirst());
                return;
            }
        }

        this.page = page;
        clearWidgets();
        switch (page) {
            case "intro" -> {
                drawName(width / 2 - DATA_WIDTH / 2, height / 2, name -> {
                    this.updateName(name);
                    if (acceptWidget != null) {
                        acceptWidget.active = !MCA.isBlankString(name);
                    }
                });
                drawGender(width / 2 - DATA_WIDTH / 2, height / 2 + 24);

                addModelSelectionWidgets(width / 2 - DATA_WIDTH / 2, height / 2 + 24 + 22);

                acceptWidget = addRenderableWidget(new ButtonWidget(width / 2 - 32, height / 2 + 60 + 22, 64, 20, Component.translatable("gui.button.accept"), sender -> {
                    if (Config.getServerConfig().allowBodyCustomizationInDestiny) {
                        setPage("body");
                    } else if (Config.getServerConfig().allowTraitCustomizationInDestiny) {
                        setPage("traits");
                    } else {
                        setPage("destiny");
                    }
                }));
            }
            case "destiny" -> drawDestinyLocations(destinyLocations);
            case "story" ->
                    addRenderableWidget(new ButtonWidget(width / 2 - 48, height / 2 + 32, 96, 20, Component.translatable("gui.destiny.next"), sender -> {
                        //we teleport early here to avoid initial flickering
                        if (!teleported) {
                            Network.sendToServer(new DestinyMessage(location, false));
                            MCAClient.getDestinyManager().allowClosing();
                            teleported = true;
                        }
                        if (story.size() > 1) {
                            story.removeFirst();
                        } else {
                            Network.sendToServer(new DestinyMessage("", true));
                            super.onClose();
                        }
                    }));
            default -> super.setPage(page);
        }
    }

    private void selectStory(String location) {
        story.clear();
        story.add(Component.translatable("destiny.story.reason"));
        Map<String, String> map = Config.getServerConfig().destinyLocationsToTranslationMap;
        story.add(Component.translatable(map.getOrDefault(location, map.getOrDefault("default", "missing_default"))));
        story.add(Component.translatableWithFallback("destiny.story." + getPath(location), getLocationName(location).getString()));
        this.location = location;
        setPage("story");
    }
}



