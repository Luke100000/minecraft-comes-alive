package net.mca.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mca.Config;
import net.mca.MCA;
import net.mca.MCAClient;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.DestinyMessage;
import net.mca.util.compat.ButtonWidget;
import net.mca.util.localization.FlowingText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DestinyScreen extends VillagerEditorScreen {
    private static final ResourceLocation LOGO_TEXTURE = new ResourceLocation("mca:textures/banner.png");
    private static final int DESTINY_COLUMNS = 3;
    private static final int DESTINY_ROWS = 3;
    private static final int DESTINY_LOCATIONS_PER_PAGE = DESTINY_COLUMNS * DESTINY_ROWS;
    private static final int DESTINY_BUTTON_GAP = 4;
    private static final int DESTINY_BUTTON_HORIZONTAL_PADDING = 16;
    private final LinkedList<Component> story = new LinkedList<>();
    private String location;
    private boolean teleported = false;
    private final boolean allowTeleportation;
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
    protected boolean shouldCloseAfterSkinExport() {
        return false;
    }

    @Override
    public void onClose() {
        if (!page.equals("general") && !page.equals("story")) {
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

    @Override
    public void renderBackground(GuiGraphics context) {
        assert Minecraft.getInstance().level != null;
        renderDirtBackground(context);
    }

    private void drawScaledText(GuiGraphics context, Component text, int x, int y, float scale) {
        final PoseStack matrices = context.pose();
        matrices.pushPose();
        matrices.scale(scale, scale, scale);
        context.drawCenteredString(font, text, (int) (x / scale), (int) (y / scale), 0xffffffff);
        matrices.popPose();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        final PoseStack matrices = context.pose();

        switch (page) {
            case "general" -> {
                drawScaledText(context, Component.translatable("gui.destiny.whoareyou"), width / 2, height / 2 - 24, 1.5f);
                matrices.pushPose();
                matrices.scale(0.25f, 0.25f, 0.25f);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                context.blit(LOGO_TEXTURE, width * 2 - 512, -40, 0, 0, 1024, 512, 1024, 512);
                matrices.popPose();
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
        return !page.equals("general") && !page.equals("destiny") && !page.equals("story") && super.shouldDrawEntity();
    }

    protected String getPath(String location) {
        String[] split = location.split(":");
        return split[split.length - 1];
    }

    private List<String> getDestinyLocations() {
        return Config.getServerConfig().destinySpawnLocations;
    }

    private MutableComponent getLocationName(String location) {
        return Component.translatableWithFallback("gui.destiny." + getPath(location), getFallbackLocationName(location));
    }

    private String getFallbackLocationName(String location) {
        return prettifyIdentifier(getPath(location));
    }

    private String getLocationModName(String location) {
        String[] idParts = location.split(":", 2);
        if (idParts.length == 2 && !idParts[0].equalsIgnoreCase("minecraft")) {
            return prettifyIdentifier(idParts[0]);
        }
        return null;
    }

    private String prettifyIdentifier(String identifier) {
        String[] words = identifier.split("[_\\-/]");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private void drawDestinyLocations(List<String> locations) {
        int pageCount = Math.max(1, (locations.size() + DESTINY_LOCATIONS_PER_PAGE - 1) / DESTINY_LOCATIONS_PER_PAGE);
        destinyPage = Math.max(0, Math.min(destinyPage, pageCount - 1));

        int start = destinyPage * DESTINY_LOCATIONS_PER_PAGE;
        int end = Math.min(start + DESTINY_LOCATIONS_PER_PAGE, locations.size());
        List<String> visibleLocations = locations.subList(start, end);
        int rows = (int)Math.ceil(visibleLocations.size() / (float)DESTINY_COLUMNS);
        float offsetY = Math.max(0, DESTINY_ROWS - rows) / 2.0f;

        for (int row = 0; row < rows; row++) {
            int rowStart = row * DESTINY_COLUMNS;
            int rowEnd = Math.min(rowStart + DESTINY_COLUMNS, visibleLocations.size());
            int entriesInRow = rowEnd - rowStart;
            int[] buttonWidths = new int[entriesInRow];
            MutableComponent[] names = new MutableComponent[entriesInRow];
            int rowWidth = DESTINY_BUTTON_GAP * Math.max(0, entriesInRow - 1);

            for (int column = 0; column < entriesInRow; column++) {
                MutableComponent name = getLocationName(visibleLocations.get(rowStart + column));
                names[column] = name;
                buttonWidths[column] = font.width(name) + DESTINY_BUTTON_HORIZONTAL_PADDING;
                rowWidth += buttonWidths[column];
            }

            int buttonX = width / 2 - rowWidth / 2;
            int buttonY = (int)(height / 2.0f + (row + offsetY) * 24 - 10);
            for (int column = 0; column < entriesInRow; column++) {
                String location = visibleLocations.get(rowStart + column);
                String modName = getLocationModName(location);
                ButtonWidget button = modName == null
                        ? new ButtonWidget(buttonX, buttonY, buttonWidths[column], 20, names[column], sender -> selectStory(location))
                        : new ButtonWidget(buttonX, buttonY, buttonWidths[column], 20, names[column], sender -> selectStory(location), Component.literal(modName));
                addRenderableWidget(button);
                buttonX += buttonWidths[column] + DESTINY_BUTTON_GAP;
            }
        }

        if (pageCount > 1) {
            int paginationY = height / 2 + 68;
            ButtonWidget previous = addRenderableWidget(new ButtonWidget(
                    width / 2 - 68, paginationY, 40, 20, Component.literal("<"),
                    sender -> {
                        destinyPage--;
                        setPage("destiny");
                    }
            ));
            previous.active = destinyPage > 0;

            ButtonWidget pageIndicator = addRenderableWidget(new ButtonWidget(
                    width / 2 - 24, paginationY, 48, 20,
                    Component.literal((destinyPage + 1) + "/" + pageCount), sender -> {
            }));
            pageIndicator.active = false;

            ButtonWidget next = addRenderableWidget(new ButtonWidget(
                    width / 2 + 28, paginationY, 40, 20, Component.literal(">"),
                    sender -> {
                        destinyPage++;
                        setPage("destiny");
                    }
            ));
            next.active = destinyPage + 1 < pageCount;
        }
    }

    @Override
    protected void setPage(String page) {
        List<String> destinyLocations = page.equals("destiny") ? getDestinyLocations() : List.of();
        if (page.equals("destiny") && !allowTeleportation) {
            NetworkHandler.sendToServer(new DestinyMessage(true));
            MCAClient.getDestinyManager().allowClosing();
            super.onClose();
            return;
        } else if (page.equals("destiny")) {
            //there is only one entry
            if (destinyLocations.size() == 1) {
                selectStory(destinyLocations.get(0));
                return;
            }
        }

        this.page = page;
        clearWidgets();
        switch (page) {
            case "general" -> {
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
                            NetworkHandler.sendToServer(new DestinyMessage(location));
                            MCAClient.getDestinyManager().allowClosing();
                            teleported = true;
                        }
                        if (story.size() > 1) {
                            story.remove(0);
                        } else {
                            NetworkHandler.sendToServer(new DestinyMessage(true));
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
