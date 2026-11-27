package net.conczin.mca.client.gui;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.DestinyMessage;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DestinyScreen extends VillagerEditorScreen {
    private static final Identifier LOGO_TEXTURE = MCA.locate("textures/banner.png");
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
    static final int DESTINY_COLUMNS = 3;
    static final int DESTINY_ROWS = 3;
    static final int DESTINY_BUTTON_GAP = 4;
    static final int DESTINY_BUTTON_HORIZONTAL_PADDING = 16;
    private static final int DESTINY_DIMENSION_SELECTOR_MAX_WIDTH = 400;
    static final int DESTINY_DIMENSION_SELECTOR_MARGIN = 14;
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
    public boolean showsActiveEffects() {
        return true;
    }

    @Override
    protected boolean shouldCloseAfterSkinExport() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
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

    private List<DestinyDestination> getDestinyDestinations() {
        if (USE_TEMPORARY_DESTINY_UI_MOCKS) {
            return TEMPORARY_DESTINY_UI_MOCKS;
        }
        return MCAClient.getDestinyManager().getDestinations();
    }

    private MutableComponent getLocationName(String location) {
        return Component.translatableWithFallback("gui.destiny." + getPath(location), prettifyIdentifier(getPath(location)));
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
        StringBuilder name = new StringBuilder();
        for (String word : identifier.split("[_\\-/]")) {
            if (!word.isEmpty()) {
                if (!name.isEmpty()) name.append(' ');
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return name.toString();
    }

    private void drawDestinyLocations(List<DestinyDestination> destinations) {
        List<DestinyDestination> globalDestinations = getGlobalDestinations(destinations);
        Map<ResourceKey<Level>, List<DestinyDestination>> byDimension = groupDestinationsByDimension(destinations);

        if (!byDimension.isEmpty()) {
            List<ResourceKey<Level>> dimensions = List.copyOf(byDimension.keySet());
            ensureSelectedDimension(dimensions);
            boolean showDimensionSelector = byDimension.size() > 1;
            if (showDimensionSelector) {
                drawDimensionSelector(dimensions);
            }

            List<DestinyDestination> dimensionDestinations = byDimension.get(selectedDestinyDimension);
            List<List<DestinationButtonSpec>> allRows = wrapDestinationRows(
                    dimensionDestinations,
                    DESTINY_COLUMNS,
                    width,
                    font,
                    this::getLocationName
            );
            int pageCount = destinationPageCount(allRows.size());
            destinyPage = Math.max(0, Math.min(destinyPage, pageCount - 1));

            drawDestinationGrid(
                    destinationRowsForPage(allRows, destinyPage),
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
        Identifier id = dimension.identifier();
        return Component.translatableWithFallback(
                "dimension." + id.getNamespace() + "." + id.getPath(),
                prettifyIdentifier(id.getPath())
        );
    }

    private void drawDimensionSelector(List<ResourceKey<Level>> dimensions) {
        int selectorWidth = Math.min(DESTINY_DIMENSION_SELECTOR_MAX_WIDTH, width)
                - DESTINY_DIMENSION_SELECTOR_MARGIN * 2;
        int buttonWidth = Mth.roundToward(selectorWidth / dimensions.size(), 2);
        int buttonX = Mth.roundToward((width - selectorWidth) / 2, 2);
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

    record DestinationButtonSpec(DestinyDestination destination, MutableComponent label, int width) {
    }

    static List<List<DestinationButtonSpec>> wrapDestinationRows(
            List<DestinyDestination> destinations,
            int maxColumns,
            int screenWidth,
            Font font,
            Function<String, MutableComponent> locationNameResolver
    ) {
        int maxRowWidth = Math.max(
                DESTINY_BUTTON_HORIZONTAL_PADDING + 12,
                screenWidth - DESTINY_DIMENSION_SELECTOR_MARGIN * 2
        );
        List<List<DestinationButtonSpec>> rows = new ArrayList<>();
        List<DestinationButtonSpec> currentRow = new ArrayList<>();
        int currentRowWidth = 0;

        for (DestinyDestination entry : destinations) {
            MutableComponent name = locationNameResolver.apply(entry.location());
            int buttonWidth = Math.min(maxRowWidth, font.width(name) + DESTINY_BUTTON_HORIZONTAL_PADDING);
            int maxLabelWidth = Math.max(0, buttonWidth - DESTINY_BUTTON_HORIZONTAL_PADDING);
            if (font.width(name) > maxLabelWidth) {
                String ellipsis = "...";
                String truncated = font.plainSubstrByWidth(
                        name.getString(),
                        Math.max(0, maxLabelWidth - font.width(ellipsis))
                ) + ellipsis;
                name = Component.literal(truncated);
            }

            int nextWidth = currentRow.isEmpty()
                    ? buttonWidth
                    : currentRowWidth + DESTINY_BUTTON_GAP + buttonWidth;
            if (!currentRow.isEmpty() && (currentRow.size() >= maxColumns || nextWidth > maxRowWidth)) {
                rows.add(List.copyOf(currentRow));
                currentRow.clear();
                currentRowWidth = 0;
            }
            currentRow.add(new DestinationButtonSpec(entry, name, buttonWidth));
            currentRowWidth = currentRow.size() == 1
                    ? buttonWidth
                    : currentRowWidth + DESTINY_BUTTON_GAP + buttonWidth;
        }
        if (!currentRow.isEmpty()) {
            rows.add(List.copyOf(currentRow));
        }
        return List.copyOf(rows);
    }

    static int destinationPageCount(int totalWrappedRows) {
        return Math.max(1, (totalWrappedRows + DESTINY_ROWS - 1) / DESTINY_ROWS);
    }

    static List<List<DestinationButtonSpec>> destinationRowsForPage(
            List<List<DestinationButtonSpec>> allRows,
            int page
    ) {
        if (allRows.isEmpty()) {
            return List.of();
        }
        int clampedPage = Math.max(0, Math.min(page, destinationPageCount(allRows.size()) - 1));
        int startRow = clampedPage * DESTINY_ROWS;
        int endRow = Math.min(startRow + DESTINY_ROWS, allRows.size());
        return allRows.subList(startRow, endRow);
    }

    private void drawDestinationRow(List<DestinyDestination> destinations, int buttonY) {
        List<List<DestinationButtonSpec>> rows = wrapDestinationRows(
                destinations,
                Integer.MAX_VALUE,
                width,
                font,
                this::getLocationName
        );
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            placeDestinationRow(rows.get(rowIndex), buttonY + rowIndex * 24);
        }
    }

    private void drawDestinationGrid(List<List<DestinationButtonSpec>> rows, int firstRowY) {
        float offsetY = Math.max(0, DESTINY_ROWS - rows.size()) / 2.0f;

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            int buttonY = (int) (firstRowY + (rowIndex + offsetY) * 24);
            placeDestinationRow(rows.get(rowIndex), buttonY);
        }
    }

    private void placeDestinationRow(List<DestinationButtonSpec> row, int buttonY) {
        int rowWidth = DESTINY_BUTTON_GAP * Math.max(0, row.size() - 1);
        for (DestinationButtonSpec spec : row) {
            rowWidth += spec.width();
        }
        int buttonX = width / 2 - rowWidth / 2;
        for (DestinationButtonSpec spec : row) {
            addDestinationButton(spec.destination(), buttonX, buttonY, spec.width(), spec.label());
            buttonX += spec.width() + DESTINY_BUTTON_GAP;
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
        if (page.equals("general") && (this.page == null || this.page.equals("loading"))) {
            page = "intro";
        }
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



