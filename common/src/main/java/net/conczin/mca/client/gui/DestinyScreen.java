package net.conczin.mca.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.DestinyMessage;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class DestinyScreen extends VillagerEditorScreen {
    private static final ResourceLocation LOGO_TEXTURE = MCA.locate("textures/banner.png");
    private static final boolean USE_TEMPORARY_DESTINY_UI_MOCKS = false;
    private static final ResourceKey<Level> MOCK_SKY_ISLANDS_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            MCA.locate("sky_islands")
    );
    private static final List<DestinyDestination> TEMPORARY_DESTINY_UI_MOCKS = List.of(
            new DestinyDestination("somewhere", Optional.empty()),
            new DestinyDestination("minecraft:village_plains", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("minecraft:village_desert", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("minecraft:village_savanna", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("minecraft:village_snowy", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("minecraft:village_taiga", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("ctov:village_plains", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("ctov:village_desert", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("towns_and_towers:village_mediterranean", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("better_villages:forest_village", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("mock:cliffside_village", Optional.of(Level.OVERWORLD)),
            new DestinyDestination("mock:nether_village", Optional.of(Level.NETHER)),
            new DestinyDestination("mock:crimson_colony", Optional.of(Level.NETHER)),
            new DestinyDestination("mock:warped_settlement", Optional.of(Level.NETHER)),
            new DestinyDestination("mock:basalt_town", Optional.of(Level.NETHER)),
            new DestinyDestination("mock:end_village", Optional.of(Level.END)),
            new DestinyDestination("mock:chorus_settlement", Optional.of(Level.END)),
            new DestinyDestination("mock:void_outpost", Optional.of(Level.END)),
            new DestinyDestination("mock:sky_harbor", Optional.of(MOCK_SKY_ISLANDS_DIMENSION)),
            new DestinyDestination("mock:cloud_village", Optional.of(MOCK_SKY_ISLANDS_DIMENSION)),
            new DestinyDestination("mock:floating_ruins", Optional.of(MOCK_SKY_ISLANDS_DIMENSION))
    );
    private static final int DESTINY_COLUMNS = 3;
    private static final int DESTINY_ROWS = 3;
    private static final int DESTINY_LOCATIONS_PER_PAGE = DESTINY_COLUMNS * DESTINY_ROWS;
    private static final int DESTINY_BUTTON_GAP = 4;
    private static final int DESTINY_BUTTON_HORIZONTAL_PADDING = 16;
    private static final int DESTINY_DIMENSION_SELECTOR_MAX_WIDTH = 400;
    private final LinkedList<Component> story = new LinkedList<>();
    private final boolean allowTeleportation;
    private DestinyDestination destination;
    private ResourceKey<Level> selectedDestinyDimension;
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

    private void drawScaledText(GuiGraphics context, Component text, int x, int y, float scale) {
        final PoseStack matrices = context.pose();
        matrices.pushPose();
        matrices.scale(scale, scale, scale);
        context.drawCenteredString(font, text, (int) (x / scale), (int) (y / scale), 0xffffffff);
        matrices.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
        this.renderPanorama(context, partialTick);
        this.renderBlurredBackground(partialTick);
        this.renderMenuBackground(context);
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

    private List<DestinyDestination> getDestinyDestinations() {
        if (USE_TEMPORARY_DESTINY_UI_MOCKS) {
            return TEMPORARY_DESTINY_UI_MOCKS;
        }
        return MCAClient.getDestinyManager().getDestinations();
    }

    private MutableComponent getLocationName(String location) {
        return Component.translatableWithFallback("gui.destiny." + getPath(location), getFallbackLocationName(location));
    }

    private String getFallbackLocationName(String location) {
        return prettifyIdentifier(getPath(location));
    }

    private String getLocationModName(String location) {
        String selector = location.startsWith("#") ? location.substring(1) : location;
        String[] idParts = selector.split(":", 2);
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
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private void drawDestinyLocations(List<DestinyDestination> destinations) {
        List<DestinyDestination> globalDestinations = getGlobalDestinations(destinations);
        Map<ResourceKey<Level>, List<DestinyDestination>> byDimension = groupDestinationsByDimension(destinations);

        if (!byDimension.isEmpty()) {
            ensureSelectedDimension(List.copyOf(byDimension.keySet()));
            boolean showDimensionSelector = byDimension.size() > 1;
            if (showDimensionSelector) {
                drawDimensionSelector(List.copyOf(byDimension.keySet()));
            }

            List<DestinyDestination> dimensionDestinations = byDimension.get(selectedDestinyDimension);
            int pageCount = Math.max(
                    1,
                    (dimensionDestinations.size() + DESTINY_LOCATIONS_PER_PAGE - 1) / DESTINY_LOCATIONS_PER_PAGE
            );
            destinyPage = Math.max(0, Math.min(destinyPage, pageCount - 1));

            int start = destinyPage * DESTINY_LOCATIONS_PER_PAGE;
            int end = Math.min(start + DESTINY_LOCATIONS_PER_PAGE, dimensionDestinations.size());
            drawDestinationGrid(
                    dimensionDestinations.subList(start, end),
                    showDimensionSelector ? height / 2 + 2 : height / 2 - 10
            );

            if (pageCount > 1) {
                drawDestinyPagination(pageCount, showDimensionSelector ? height / 2 + 76 : height / 2 + 68);
            }
        }

        if (!globalDestinations.isEmpty()) {
            int buttonY = byDimension.isEmpty()
                    ? height / 2 + 4
                    : height / 2 + (byDimension.size() > 1 ? 100 : 92);
            drawDestinationRow(globalDestinations, buttonY);
        }
    }

    private List<DestinyDestination> getGlobalDestinations(List<DestinyDestination> destinations) {
        return destinations.stream()
                .filter(destination -> destination.dimension().isEmpty())
                .toList();
    }

    private Map<ResourceKey<Level>, List<DestinyDestination>> groupDestinationsByDimension(
            List<DestinyDestination> destinations
    ) {
        return destinations.stream()
                .filter(destination -> destination.dimension().isPresent())
                .collect(Collectors.groupingBy(
                        destination -> destination.dimension().orElseThrow(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private void ensureSelectedDimension(List<ResourceKey<Level>> dimensions) {
        if (selectedDestinyDimension != null && dimensions.contains(selectedDestinyDimension)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> currentDimension = minecraft.level == null ? null : minecraft.level.dimension();
        if (currentDimension != null && dimensions.contains(currentDimension)) {
            selectedDestinyDimension = currentDimension;
        } else if (dimensions.contains(Level.OVERWORLD)) {
            selectedDestinyDimension = Level.OVERWORLD;
        } else {
            selectedDestinyDimension = dimensions.getFirst();
        }
        destinyPage = 0;
    }

    private Component getDimensionName(ResourceKey<Level> dimension) {
        ResourceLocation id = dimension.location();
        return Component.translatableWithFallback(
                "dimension." + id.getNamespace() + "." + id.getPath(),
                prettifyIdentifier(id.getPath())
        );
    }

    private void drawDimensionSelector(List<ResourceKey<Level>> dimensions) {
        int selectorWidth = Math.min(DESTINY_DIMENSION_SELECTOR_MAX_WIDTH, width) - 28;
        int buttonWidth = selectorWidth / dimensions.size();
        int buttonX = (width - buttonWidth * dimensions.size()) / 2;
        int buttonY = height / 2 - 28;

        for (ResourceKey<Level> dimension : dimensions) {
            ButtonWidget button = addRenderableWidget(new ButtonWidget(
                    buttonX,
                    buttonY,
                    buttonWidth,
                    20,
                    getDimensionName(dimension),
                    sender -> selectDimension(dimension)
            ));
            button.active = !dimension.equals(selectedDestinyDimension);
            buttonX += buttonWidth;
        }
    }

    private void selectDimension(ResourceKey<Level> dimension) {
        if (dimension.equals(selectedDestinyDimension)) {
            return;
        }
        selectedDestinyDimension = dimension;
        destinyPage = 0;
        setPage("destiny");
    }

    private void drawDestinationRow(List<DestinyDestination> destinations, int buttonY) {
        int[] buttonWidths = new int[destinations.size()];
        MutableComponent[] names = new MutableComponent[destinations.size()];
        int rowWidth = DESTINY_BUTTON_GAP * Math.max(0, destinations.size() - 1);

        for (int i = 0; i < destinations.size(); i++) {
            MutableComponent name = getLocationName(destinations.get(i).location());
            names[i] = name;
            buttonWidths[i] = font.width(name) + DESTINY_BUTTON_HORIZONTAL_PADDING;
            rowWidth += buttonWidths[i];
        }

        int buttonX = width / 2 - rowWidth / 2;
        for (int i = 0; i < destinations.size(); i++) {
            addDestinationButton(destinations.get(i), buttonX, buttonY, buttonWidths[i], names[i]);
            buttonX += buttonWidths[i] + DESTINY_BUTTON_GAP;
        }
    }

    private void drawDestinationGrid(List<DestinyDestination> visibleDestinations, int firstRowY) {
        int rows = (int) Math.ceil(visibleDestinations.size() / (float) DESTINY_COLUMNS);
        float offsetY = Math.max(0, DESTINY_ROWS - rows) / 2.0f;

        for (int row = 0; row < rows; row++) {
            int rowStart = row * DESTINY_COLUMNS;
            int rowEnd = Math.min(rowStart + DESTINY_COLUMNS, visibleDestinations.size());
            int entriesInRow = rowEnd - rowStart;
            int[] buttonWidths = new int[entriesInRow];
            MutableComponent[] names = new MutableComponent[entriesInRow];
            int rowWidth = DESTINY_BUTTON_GAP * Math.max(0, entriesInRow - 1);

            for (int column = 0; column < entriesInRow; column++) {
                MutableComponent name = getLocationName(visibleDestinations.get(rowStart + column).location());
                names[column] = name;
                buttonWidths[column] = font.width(name) + DESTINY_BUTTON_HORIZONTAL_PADDING;
                rowWidth += buttonWidths[column];
            }

            int buttonX = width / 2 - rowWidth / 2;
            int buttonY = (int) (firstRowY + (row + offsetY) * 24);
            for (int column = 0; column < entriesInRow; column++) {
                DestinyDestination destination = visibleDestinations.get(rowStart + column);
                addDestinationButton(destination, buttonX, buttonY, buttonWidths[column], names[column]);
                buttonX += buttonWidths[column] + DESTINY_BUTTON_GAP;
            }
        }
    }

    private void addDestinationButton(
            DestinyDestination destination,
            int buttonX,
            int buttonY,
            int buttonWidth,
            MutableComponent name
    ) {
        String modName = getLocationModName(destination.location());
        ButtonWidget button = modName == null
                ? new ButtonWidget(buttonX, buttonY, buttonWidth, 20, name, sender -> selectStory(destination))
                : new ButtonWidget(buttonX, buttonY, buttonWidth, 20, name, sender -> selectStory(destination), Component.literal(modName));
        addRenderableWidget(button);
    }

    private void drawDestinyPagination(int pageCount, int paginationY) {
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

    @Override
    protected void setPage(String page) {
        List<DestinyDestination> destinations = page.equals("destiny") ? getDestinyDestinations() : List.of();
        if (page.equals("destiny") && !allowTeleportation) {
            Network.sendToServer(DestinyMessage.close());
            MCAClient.getDestinyManager().allowClosing();
            super.onClose();
            return;
        } else if (page.equals("destiny")) {
            if (destinations.size() == 1) {
                selectStory(destinations.getFirst());
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
            case "destiny" -> drawDestinyLocations(destinations);
            case "story" ->
                    addRenderableWidget(new ButtonWidget(width / 2 - 48, height / 2 + 32, 96, 20, Component.translatable("gui.destiny.next"), sender -> {
                        //we teleport early here to avoid initial flickering
                        if (!teleported) {
                            Network.sendToServer(DestinyMessage.select(destination));
                            MCAClient.getDestinyManager().allowClosing();
                            teleported = true;
                        }
                        if (story.size() > 1) {
                            story.removeFirst();
                        } else {
                            Network.sendToServer(DestinyMessage.close());
                            super.onClose();
                        }
                    }));
            default -> super.setPage(page);
        }
    }

    private void selectStory(DestinyDestination destination) {
        String location = destination.location();
        story.clear();
        story.add(Component.translatable("destiny.story.reason"));
        Map<String, String> map = Config.getServerConfig().destinyLocationsToTranslationMap;
        story.add(Component.translatable(map.getOrDefault(location, map.getOrDefault("default", "missing_default"))));
        story.add(Component.translatableWithFallback("destiny.story." + getPath(location), getLocationName(location).getString()));
        this.destination = destination;
        setPage("story");
    }
}
