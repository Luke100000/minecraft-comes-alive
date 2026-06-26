package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.immersive_library.*;
import net.conczin.mca.client.gui.immersive_library.responses.*;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.conczin.mca.client.gui.immersive_library.types.User;
import net.conczin.mca.client.gui.widget.*;
import net.conczin.mca.client.resources.*;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.AddCustomClothingMessage;
import net.conczin.mca.network.c2s.RemoveCustomClothingMessage;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.conczin.mca.client.gui.immersive_library.Api.request;

public class SkinLibraryScreen extends Screen implements SkinListUpdateListener {
    static final int CLOTHES_H = 7;
    static final int CLOTHES_V = 2;
    static final int CLOTHES_PER_PAGE = CLOTHES_H * CLOTHES_V + 1;
    private static final Identifier TEMPLATE_IDENTIFIER = MCA.locate("textures/skin_template.png");
    private static final Identifier EMPTY_IDENTIFIER = MCA.locate("skins/empty.png");
    private static final Identifier CANVAS_IDENTIFIER = MCA.locate("temp");
    private static final float CANVAS_SCALE = 2.35f;
    protected final VillagerEntityMCA villagerVisualization = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(Objects.requireNonNull(Minecraft.getInstance().level), EntitySpawnReason.LOAD));
    private final List<LiteContent> serverContent = new ArrayList<>();
    private final ColorSelector color = new ColorSelector();
    private final VillagerEditorScreen previousScreen;
    private final List<LiteContent> contents = new LinkedList<>();
    private String filteredString = "";
    private SortingMode sortingMode = SortingMode.RECOMMENDATIONS;
    private boolean filterInvalidSkins = true;
    private boolean moderatorMode = false;
    private boolean filterHair = false;
    private boolean filterClothing = false;
    private SubscriptionFilter subscriptionFilter = SubscriptionFilter.LIBRARY;
    private User currentUser;
    private int selectionPage;
    private LiteContent focusedContent;
    private LiteContent hoveredContent;
    private LiteContent deleteConfirmationContent;
    private LiteContent reportConfirmationContent;
    private Page page;
    private String lastFilteredString = "";
    private int lastLoadedPage = -1;
    private ButtonWidget pageWidget;
    private Workspace workspace;
    private int activeMouseButton;
    private int lastPixelMouseX;
    private int lastPixelMouseY;
    private float x0, x1, y0, y1;
    private boolean isPanning;
    private boolean hasPanned;
    private double lastMouseX;
    private double lastMouseY;
    private int timeSinceLastRebuild;
    private Component error;
    private boolean authenticated = false;
    private boolean awaitingAuthentication = false;
    private boolean isBrowserOpen = false;
    private boolean uploading = false;
    private Thread thread;
    private EditBox textFieldWidget;
    private boolean skipHairWarning;
    private List<LiteContent> libraryContents = new LinkedList<>();
    private CompoundTag basePreviewData;

    public SkinLibraryScreen() {
        this(null, null);
    }

    public SkinLibraryScreen(VillagerEditorScreen screen, VillagerEntityMCA villagerVisualization) {
        super(Component.translatable("gui.skin_library.title"));

        this.previousScreen = screen;

        if (this.previousScreen instanceof NeedleScreen) {
            filterHair = true;
        }
        if (this.previousScreen instanceof CombScreen) {
            filterClothing = true;
        }

        if (villagerVisualization != null) {
            this.villagerVisualization.load(TagValueInput.create(ProblemReporter.DISCARDING, this.villagerVisualization.registryAccess(), saveEntityData(villagerVisualization)));
        } else {
            assert Minecraft.getInstance().player != null;
            VillagerLike<?> villagerLike = MCAClient.getPlayerData(Minecraft.getInstance().player.getUUID()).orElse(null);
            if (villagerLike instanceof VillagerEntityMCA villager) {
                this.villagerVisualization.load(TagValueInput.create(ProblemReporter.DISCARDING, this.villagerVisualization.registryAccess(), saveEntityData(villager)));
            }
        }
        basePreviewData = saveEntityData(this.villagerVisualization);
    }

    private static CompoundTag saveEntityData(VillagerEntityMCA entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.save(output);
        return output.buildResult();
    }

    @Override
    public void onClose() {
        if (previousScreen == null) {
            super.onClose();
        } else {
            Minecraft.getInstance().setScreen(previousScreen);
        }
    }

    @Override
    protected void init() {
        super.init();

        refreshServerContent();

        thread = Thread.currentThread();

        if (page == null) {
            if (Auth.loadToken() == null) {
                setPage(Page.LOADING);
            } else {
                setPage(Page.LOGIN);
            }
            reloadDatabase();
        } else {
            refreshPage();
        }
    }

    private void reloadDatabase() {
        reloadDatabase(() -> {
            if (page == Page.LOADING) {
                setPage(Page.LIBRARY);
            }
        });
    }

    private void reloadDatabase(Runnable callback) {
        CompletableFuture.runAsync(() -> {
            // fetch user
            if (Auth.hasToken() && authenticated) {
                Response response = request(Api.HttpMethod.GET, UserResponse.class, "user/mca/me");
                if (response instanceof UserResponse(User user)) {
                    currentUser = user;
                    refreshContentList();
                } else {
                    setError(Component.translatable("gui.skin_library.list_fetch_failed"));
                }
            }
        }).thenRunAsync(callback);
    }

    private void loadPage() {
        loadPage(false);
    }

    private void loadPage(boolean force) {
        if (lastLoadedPage == selectionPage && lastFilteredString.equals(filteredString) && !force) {
            return;
        }
        lastFilteredString = filteredString;
        lastLoadedPage = selectionPage;

        CompletableFuture.runAsync(() -> {
            // fetch assets
            Response response = request(Api.HttpMethod.GET, ContentListResponse.class, "v2/content/mca", Map.of(
                    "whitelist", filteredString,
                    "blacklist", (filterInvalidSkins ? "invalid" : "") + (filterHair ? ",hair" : "") + (filterClothing ? ",clothing" : ""),
                    "order", sortingMode.order,
                    "descending", "true",
                    "offset", String.valueOf(selectionPage * CLOTHES_PER_PAGE),
                    "limit", String.valueOf(CLOTHES_PER_PAGE),
                    "moderator", String.valueOf(moderatorMode)
            ));

            if (response instanceof ContentListResponse(LiteContent[] contents1)) {
                libraryContents = new ArrayList<>(Arrays.asList(contents1));
                refreshContentList();
            } else {
                setError(Component.translatable("gui.skin_library.list_fetch_failed"));
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        final Matrix3x2fStack matrices = context.pose();

        hoveredContent = null;

        villagerVisualization.setAge(0);
        villagerVisualization.refreshDimensions();

        List<Component> tooltip = null;

        switch (page) {
            case LIBRARY -> {
                int i = 0;
                for (int y = 0; y < CLOTHES_V; y++) {
                    for (int x = 0; x < CLOTHES_H + y; x++) {
                        if (contents.size() > i) {
                            LiteContent c = contents.get(i);

                            setDummyTexture(villagerVisualization, c);

                            int cx = width / 2 + (int) ((x - CLOTHES_H / 2.0 + 0.5 - 0.5 * (y % 2)) * 55);
                            int cy = height / 2 + (int) ((y - CLOTHES_V / 2.0 + 0.5) * 80);

                            if (Math.abs(cx - mouseX) <= 15 && Math.abs(cy - mouseY - 10) <= 25) {
                                hoveredContent = c;
                                tooltip = getMetaDataText(c);
                            }

                            villagerVisualization.getGenetics().setGender(SkinCache.getMeta(c).map(SkinMeta::getGender).orElse(Gender.MALE).binary());
                            InventoryScreen.extractEntityInInventoryFollowsMouse(context, cx - 25, cy - 50, cx + 25, cy + 30, hoveredContent == c ? 30 : 28, 0, mouseX, mouseY, villagerVisualization);
                            i++;
                        } else {
                            break;
                        }
                    }
                }

                if (!authenticated && (subscriptionFilter == SubscriptionFilter.LIKED || subscriptionFilter == SubscriptionFilter.SUBMISSIONS)) {
                    drawTextBox(context, Component.translatable("gui.skin_library.like_locked"));
                }
            }
            case EDITOR_LOCKED -> {
                drawTextBox(context, Component.translatable("gui.skin_library.locked"));
            }
            case EDITOR_PREPARE -> {
                drawTextBox(context, Component.translatable("gui.skin_library.drop"));
            }
            case EDITOR_TYPE -> {
                drawTextBox(context, Component.translatable("gui.skin_library.prepare"));
            }
            case DELETE -> {
                drawTextBox(context, Component.translatable("gui.skin_library.delete_confirm"));
            }
            case REPORT -> {
                drawTextBox(context, Component.translatable("gui.skin_library.report_confirm"));
            }
            case EDITOR -> {
                if (workspace.isDirty()) {
                    workspace.backendTexture.upload();
                    Minecraft.getInstance().getTextureManager().register(CANVAS_IDENTIFIER, workspace.backendTexture);
                    workspace.setDirty(false);
                }

                //painting area
                int tw = 64;
                int th = 64;
                matrices.pushMatrix();
                matrices.translate(width / 2.0f - tw * CANVAS_SCALE / 2.0f, height / 2.0f - th * CANVAS_SCALE / 2.0f);
                matrices.scale(CANVAS_SCALE, CANVAS_SCALE);

                // Calculate the clamped vertex and UV coordinates
                float vx0 = Mth.clamp(0, x0, x1);
                float vx1 = Mth.clamp(1, x0, x1);
                float vy0 = Mth.clamp(0, y0, y1);
                float vy1 = Mth.clamp(1, y0, y1);

                float uvx0 = (vx0 - x0) / (x1 - x0);
                float uvx1 = (vx1 - x0) / (x1 - x0);
                float uvy0 = (vy0 - y0) / (y1 - y0);
                float uvy1 = (vy1 - y0) / (y1 - y0);

                //draw template
                WidgetUtils.drawTexturedQuad(context, TEMPLATE_IDENTIFIER, vx0 * 64, vx1 * 64, vy0 * 64, vy1 * 64, uvx0, uvx1, uvy0, uvy1, ARGB.white(0.25f));

                //draw canvas
                WidgetUtils.drawTexturedQuad(context, CANVAS_IDENTIFIER, vx0 * 64, vx1 * 64, vy0 * 64, vy1 * 64, uvx0, uvx1, uvy0, uvy1);

                //border
                WidgetUtils.drawRectangle(context, -1, -1, tw + 1, th + 1, 0xaaffffff);

                matrices.popMatrix();

                //dummy
                applyBasePreview(villagerVisualization);
                if (workspace.skinType == SkinType.CLOTHING) {
                    villagerVisualization.setClothes(CANVAS_IDENTIFIER);
                } else {
                    villagerVisualization.setHairStyle(HairStyle.singleLayer(CANVAS_IDENTIFIER.toString(), workspace.gender.binary(), 1.0F));
                    villagerVisualization.setClothes(EMPTY_IDENTIFIER);
                }

                int cx = width / 2 + 150;
                int cy = height / 2 - 10;

                villagerVisualization.getGenetics().setGender(workspace.gender.binary());
                WidgetUtils.drawBackgroundEntity(context, cx, cy, 50, mouseX, mouseY, villagerVisualization);

                if (workspace.skinType == SkinType.HAIR) {
                    context.centeredText(font, Component.translatable("gui.skin_library.hair_color"), width / 2 - 150, height / 2 - 40, 0xAAFFFFFF);
                }

                //hovered element
                int x = (int) getPixelX();
                int y = (int) getPixelY();
                if (x >= 0 && x < 64 && y >= 0 && y < 64) {
                    SkinLocations.Part part = SkinLocations.LOOKUP[x][y];
                    if (part != null) {
                        Component text = part.getTranslation();
                        int textWidth = font.width(text);
                        WidgetUtils.drawTooltip(context, font, text, width / 2 - textWidth / 2 - 12, height / 2 - 68);
                    }
                }
            }
            case LOGIN -> {
                // check user authentication
                if (!awaitingAuthentication) {
                    awaitingAuthentication = true;
                    CompletableFuture.runAsync(() -> {
                        try {
                            Response response = Auth.hasToken() ? request(Api.HttpMethod.GET, IsAuthResponse.class, "auth") : null;
                            if (response instanceof IsAuthResponse(boolean success)) {
                                if (success) {
                                    authenticated = true;
                                    clearError();
                                    reloadDatabase();

                                    //token accepted, save
                                    Auth.saveToken();

                                    setPage(Page.LIBRARY);
                                } else {
                                    //token rejected, delete file
                                    Auth.clearToken();
                                    if (!isBrowserOpen) {
                                        setPage(Page.LIBRARY);
                                        setError(Component.translatable("gui.skin_library.is_auth_failed"));
                                    }
                                }
                            } else {
                                setError(Component.translatable("gui.skin_library.is_auth_failed"));
                            }
                            Thread.sleep(2000);
                        } catch (Exception e) {
                            MCA.LOGGER.error(e);
                        }
                        awaitingAuthentication = false;
                    });
                }

                // auth hint
                if (isBrowserOpen) {
                    drawTextBox(context, Component.translatable("gui.skin_library.authenticating_browser"));
                } else if (error != null) {
                    drawTextBox(context, Component.translatable("gui.skin_library.authenticating"));
                } else {
                    drawTextBox(context, Component.translatable("gui.skin_library.authenticating").append(Component.literal(" " + ".".repeat((int) (System.currentTimeMillis() / 500 % 4)))));
                }
            }
            case DETAIL -> {
                //dummy
                setDummyTexture(villagerVisualization, focusedContent);

                int cx = width / 2;
                int cy = height / 2;

                villagerVisualization.getGenetics().setGender(SkinCache.getMeta(focusedContent).map(SkinMeta::getGender).orElse(Gender.MALE).binary());
                InventoryScreen.extractEntityInInventoryFollowsMouse(context, cx - 30, cy - 60, cx + 30, cy + 60, 60, 0, mouseX, mouseY, villagerVisualization);

                //metadata
                WidgetUtils.drawTooltip(context, font, getMetaDataText(focusedContent), width / 2 + 200, height / 2 - 50);
            }
            case LOADING -> {
                context.text(font, Component.translatable("gui.loading"), width / 2, height / 2, 0xFFFFFFFF);
            }
        }

        if (tooltip != null) {
            WidgetUtils.drawTooltip(context, font, tooltip, mouseX, mouseY);
        }

        if (error != null) {
            context.centeredText(font, error, width / 2, height / 2, 0xFFFF0000);
        }
    }

    private void setDummyTexture(VillagerEntityMCA preview, LiteContent content) {
        applyBasePreview(preview);
        if (content.hasTag("clothing")) {
            preview.setHair("");
            preview.setHairStyleId("");
            preview.clearLayeredHair();
            preview.setClothes(SkinCache.getTextureIdentifier(content));
        } else {
            preview.setHairStyle(HairStyle.singleLayer(SkinCache.getTextureIdentifier(content).toString(), preview.getGenetics().getGender(), 1.0F));
            preview.setClothes(EMPTY_IDENTIFIER);
        }
    }

    private void applyBasePreview(VillagerEntityMCA preview) {
        if (basePreviewData != null) {
            preview.load(TagValueInput.create(ProblemReporter.DISCARDING, preview.registryAccess(), basePreviewData));
        }
    }

    private List<Component> getMetaDataText(LiteContent content) {
        Optional<SkinMeta> meta = SkinCache.getMeta(content);
        if (meta.isEmpty()) {
            return List.of(Component.literal(content.title()));
        } else {
            List<Component> wrap = FlowingText.wrap(Component.literal(String.join(", ", content.tags())).withStyle(ChatFormatting.YELLOW), 160);
            ArrayList<Component> texts = new ArrayList<>(List.of(
                    Component.literal(content.title()),
                    Component.translatable("gui.skin_library.meta.by", content.username()).withStyle(ChatFormatting.ITALIC),
                    Component.translatable("gui.skin_library.meta.likes", content.likes()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.skin_library.gender", meta.get().getGender() == Gender.MALE ? Component.translatable("gui.villager_editor.masculine") : (meta.get().getGender() == Gender.FEMALE ? Component.translatable("gui.villager_editor.feminine") : Component.translatable("gui.villager_editor.neutral"))),
                    Component.translatable("gui.skin_library.profession", meta.get().getProfession() == null ? Component.translatable("entity.minecraft.villager") : Component.translatable("entity.minecraft.villager." + meta.get().getProfession())),
                    Component.translatable("gui.skin_library.temperature", Component.translatable("gui.skin_library.temperature." + (meta.get().getTemperature() + 2))),
                    Component.translatable("gui.skin_library.chance_val", (int) (meta.get().getChance() * 100)).withStyle(ChatFormatting.GRAY)
            ));
            texts.addAll(wrap);

            if (content.tags().contains("invalid")) {
                texts.add(Component.translatable("gui.skin_library.probably_not_valids").withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.RED));
            }

            return texts;
        }
    }

    private double getScreenScaleX() {
        assert minecraft != null;
        return (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
    }

    private double getScreenScaleY() {
        assert minecraft != null;
        return (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();
    }

    private double getCanvasX() {
        assert minecraft != null;
        double x = minecraft.mouseHandler.xpos() * getScreenScaleX();
        return (x - width / 2.0 + 32 * CANVAS_SCALE) / CANVAS_SCALE / 64.0;
    }

    private double getCanvasY() {
        assert minecraft != null;
        double y = minecraft.mouseHandler.ypos() * getScreenScaleY();
        return (y - height / 2.0 + 32 * CANVAS_SCALE) / CANVAS_SCALE / 64.0;
    }

    private float getPixelX() {
        double cx = getCanvasX();
        return (int) ((cx - x0) / (x1 - x0) * 64);
    }

    private float getPixelY() {
        double cy = getCanvasY();
        return (int) ((cy - y0) / (y1 - y0) * 64.0);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        mouseDeltaMoved(lastMouseX - mouseX, lastMouseY - mouseY);

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        super.mouseMoved(mouseX, mouseY);
    }

    protected void mouseDeltaMoved(double deltaX, double deltaY) {
        if (isPanning) {
            float ox = (float) (deltaX / 64 / CANVAS_SCALE);
            x0 -= ox;
            x1 -= ox;

            float oy = (float) (deltaY / 64 / CANVAS_SCALE);
            y0 -= oy;
            y1 -= oy;

            hasPanned = true;
        } else {
            hasPanned = false;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        // Pan
        if (keyCode == GLFW.GLFW_KEY_SPACE && (textFieldWidget == null || !textFieldWidget.isFocused())) {
            isPanning = true;
            return true;
        }

        if (page == Page.EDITOR && (textFieldWidget == null || !textFieldWidget.isFocused())) {
            // Reset viewport
            if (keyCode == GLFW.GLFW_KEY_R) {
                x0 = 0.0f;
                x1 = 1.0f;
                y0 = 0.0f;
                y1 = 1.0f;
                return true;
            }

            // Fill-delete
            if (keyCode == GLFW.GLFW_KEY_F) {
                workspace.fillDelete((int) getPixelX(), (int) getPixelY());
                return true;
            }

            // Undo
            if (keyCode == GLFW.GLFW_KEY_Y || keyCode == GLFW.GLFW_KEY_Z) {
                workspace.undo();
                return true;
            }

            // Color pick
            if (keyCode == GLFW.GLFW_KEY_P) {
                pickColor();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            isPanning = false;
        }

        return super.keyReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!isPanning && activeMouseButton >= 0 && page == Page.EDITOR) {
            int x = (int) getPixelX();
            int y = (int) getPixelY();
            ClientUtils.bethlehemLine(lastPixelMouseX, lastPixelMouseY, x, y, this::paint);
            lastPixelMouseX = x;
            lastPixelMouseY = y;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Page.EDITOR) {
            float zoom = (float) (scrollY * 0.2f) * (x1 - x0);

            float ox = getPixelX() / 64.0f;
            x0 = x0 - zoom * ox;
            x1 = x1 + zoom * (1 - ox);

            float oy = getPixelY() / 64.0f;
            y0 = y0 - zoom * oy;
            y1 = y1 + zoom * (1 - oy);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();

        timeSinceLastRebuild++;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (timeSinceLastRebuild < 2) {
            return false;
        }

        if (page == Page.EDITOR) {
            if (event.button() == 0 || event.button() == 1) {
                activeMouseButton = event.button();

                int x = (int) getPixelX();
                int y = (int) getPixelY();

                if (workspace.validPixel(x, y)) {
                    workspace.saveSnapshot(true);
                }

                if (!isPanning) {
                    paint(x, y);
                }

                lastPixelMouseX = x;
                lastPixelMouseY = y;
            } else if (event.button() == 2) {
                isPanning = true;
            }
        } else {
            if (hoveredContent != null) {
                if (previousScreen == null) {
                    focusedContent = hoveredContent;
                    setPage(Page.DETAIL);
                } else {
                    if (hoveredContent.hasTag("clothing")) {
                        var villager = previousScreen.getVillager();
                        villager.setClothes("immersive_library:" + hoveredContent.contentid());
                        previousScreen.markClothingSelected();
                        villager.setHair("");
                        villager.setHairStyleId("");
                        villager.clearLayeredHair();
                        returnToPreviousScreen();
                    } else if (hoveredContent.hasTag("hair")) {
                        previousScreen.applyLibraryHair("immersive_library:" + hoveredContent.contentid());
                        returnToPreviousScreen();
                    }
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void returnToPreviousScreen() {
        previousScreen.syncVillagerData();
        if (previousScreen instanceof NeedleScreen || previousScreen instanceof CombScreen) {
            previousScreen.onClose();
        } else {
            onClose();
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeMouseButton = -1;

        if (event.button() == 2) {
            isPanning = false;
            if (!hasPanned && page == Page.EDITOR) {
                pickColor();
            }
        }

        return super.mouseReleased(event);
    }

    private void drawTextBox(GuiGraphicsExtractor context, Component text) {
        List<Component> wrap = FlowingText.wrap(text, 220);
        int y = height / 2 - 20 - wrap.size() * 12;
        context.fill(width / 2 - 115, y - 5, width / 2 + 115, y + 12 * wrap.size(), 0x50000000);
        for (Component t : wrap) {
            context.centeredText(font, t, width / 2, y, 0xFFFFFFFF);
            y += 12;
        }
    }

    private void paint(int x, int y) {
        if (page == SkinLibraryScreen.Page.EDITOR && workspace.validPixel(x, y)) {
            if (activeMouseButton == 0) {
                workspace.currentImage.setPixel(x, y, color.getColor());
                workspace.setDirty(true);
            } else if (activeMouseButton == 1) {
                workspace.currentImage.setPixel(x, y, 0);
                workspace.setDirty(true);
            }
        }
    }

    private void pickColor() {
        int x = (int) getPixelX();
        int y = (int) getPixelY();
        if (workspace.validPixel(x, y)) {
            int pixel = workspace.currentImage.getPixel(x, y);
            color.setRGB(
                    ARGB.red(pixel) / 255.0,
                    ARGB.green(pixel) / 255.0,
                    ARGB.blue(pixel) / 255.0
            );
            if (workspace.skinType == SkinType.HAIR) color.setHSV(0, 0, color.brightness);
        }
    }

    private void rebuild() {
        clearWidgets();

        timeSinceLastRebuild = 0;

        // filters
        if (page == Page.LIBRARY || page == Page.EDITOR_LOCKED || page == Page.EDITOR_PREPARE || page == Page.EDITOR_TYPE) {
            List<Page> b = new LinkedList<>();
            b.add(Page.LIBRARY);
            b.add(Page.EDITOR_PREPARE);
            b.add(Page.HELP);

            if (authenticated) {
                b.add(Page.LOGOUT);
            } else {
                b.add(Page.LOGIN);
            }

            int x = page == Page.LIBRARY ? width / 2 - 20 : width / 2 - 110;
            int w = 220 / b.size();
            for (Page page : b) {
                addRenderableWidget(new ButtonWidget(x, height / 2 - 110, w, 20, Component.translatable("gui.skin_library.page." + page.name().toLowerCase(Locale.ROOT)), sender -> setPage(page))).active = page != this.page;
                x += w;
            }
        }

        switch (page) {
            case LIBRARY -> {
                //page
                addRenderableWidget(new ButtonWidget(width / 2 - 30 - 30, height / 2 + 80, 30, 20, Component.literal("<<"), sender -> {
                    setSelectionPage(selectionPage - 1);
                    refreshContentList();
                }));
                pageWidget = addRenderableWidget(new ButtonWidget(width / 2 - 30, height / 2 + 80, 60, 20, Component.literal(""), sender -> {
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 30, height / 2 + 80, 30, 20, Component.literal(">>"), sender -> {
                    setSelectionPage(selectionPage + 1);
                    refreshContentList();
                }));
                setSelectionPage(selectionPage);

                int iconX = width / 2 - 170;

                //sorting icons
                addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX, height / 2 + 82, 6 * 16, 3 * 16,
                        sortingMode == SortingMode.LIKES,
                        Component.translatable("gui.skin_library.sort_likes"),
                        v -> {
                            sortingMode = SortingMode.LIKES;
                            loadPage(true);
                        }));
                addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22, height / 2 + 82, 7 * 16, 3 * 16,
                        sortingMode == SortingMode.NEWEST,
                        Component.translatable("gui.skin_library.sort_newest"),
                        v -> {
                            sortingMode = SortingMode.NEWEST;
                            loadPage(true);
                        }));
                addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 2, height / 2 + 82, 14 * 16, 3 * 16,
                        sortingMode == SortingMode.RECOMMENDATIONS,
                        Component.translatable("gui.skin_library.sort_recommendations"),
                        v -> {
                            sortingMode = SortingMode.RECOMMENDATIONS;
                            loadPage(true);
                        }));

                iconX = width / 2 + 50;
                if (subscriptionFilter == SubscriptionFilter.LIBRARY) {
                    //filter
                    addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 2, height / 2 + 82, 9 * 16, 3 * 16,
                            filterInvalidSkins,
                            Component.translatable("gui.skin_library.filter_invalid"),
                            v -> {
                                filterInvalidSkins = !filterInvalidSkins;
                                loadPage(true);
                            }));

                    //filter clothing
                    addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 3, height / 2 + 82, 12 * 16, 3 * 16,
                            filterClothing,
                            Component.translatable("gui.skin_library.filter_clothing"),
                            v -> {
                                filterClothing = !filterClothing;
                                loadPage(true);
                            }));

                    //filter hair
                    addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 4, height / 2 + 82, 13 * 16, 3 * 16,
                            filterHair,
                            Component.translatable("gui.skin_library.filter_hair"),
                            v -> {
                                filterHair = !filterHair;
                                loadPage(true);
                            }));

                    //moderator search
                    if (isModerator()) {
                        addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 5, height / 2 + 82, 11 * 16, 3 * 16,
                                moderatorMode,
                                Component.translatable("gui.skin_library.filter_moderator"),
                                v -> {
                                    moderatorMode = !moderatorMode;
                                    loadPage(true);
                                }));
                    }
                }

                //search
                if (subscriptionFilter == SubscriptionFilter.LIBRARY) {
                    EditBox textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 200 + 65, height / 2 - 110 + 2, 110, 16,
                            Component.translatable("gui.skin_library.search")));
                    textFieldWidget.setMaxLength(64);
                    textFieldWidget.setValue(filteredString);
                    if (filteredString.isEmpty()) {
                        textFieldWidget.setSuggestion("Search");
                    }
                    textFieldWidget.setResponder(s -> {
                        filteredString = s;
                        refreshContentList();
                        textFieldWidget.setSuggestion(null);
                    });
                }

                //group
                addRenderableWidget(CycleButton.builder(SubscriptionFilter::getText, subscriptionFilter)
                        .withValues(SubscriptionFilter.values())
                        .displayOnlyValue()
                        .create(width / 2 - 200, height / 2 - 110, 60, 20, Component.literal(""), (button, filter) -> {
                            this.subscriptionFilter = filter;
                            refreshContentList();
                        }));

                //controls
                int i = 0;
                for (int y = 0; y < CLOTHES_V; y++) {
                    for (int x = 0; x < CLOTHES_H + y; x++) {
                        if (contents.size() > i) {
                            LiteContent c = contents.get(i);

                            int cx = width / 2 + (int) ((x - CLOTHES_H / 2.0 + 0.5 - 0.5 * (y % 2)) * 55);
                            int cy = height / 2 + 15 + (int) ((y - CLOTHES_V / 2.0 + 0.5) * 80);

                            drawControls(c, false, cx, cy);

                            //quick invalid toggle
                            if (isModerator()) {
                                addRenderableWidget(new ToggleableTooltipIconButtonWidget(cx + 16, cy - 48, 10 * 16, 3 * 16,
                                        false,
                                        Component.literal("Toggle invalid"),
                                        v -> setTag(c.contentid(), "invalid", !c.hasTag("invalid"))));
                            }

                            //sorting icons
                            if (c.tags().contains("invalid")) {
                                addRenderableWidget(new ToggleableTooltipIconButtonWidget(cx + 12, cy - 16, 9 * 16, 3 * 16,
                                        true,
                                        Component.translatable("gui.skin_library.probably_not_valids"),
                                        v -> {
                                        }));
                            }
                            i++;
                        } else {
                            break;
                        }
                    }
                }
            }
            case EDITOR_PREPARE -> {
                //URL
                EditBox textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 90, height / 2 - 18, 180, 16,
                        Component.literal("URL")));
                textFieldWidget.setMaxLength(1024);

                addRenderableWidget(new ButtonWidget(width / 2 - 50, height / 2 + 5, 100, 20, Component.translatable("gui.skin_library.load_image"), sender -> loadImage(textFieldWidget.getValue())));
            }
            case EDITOR_TYPE -> {
                addRenderableWidget(new ButtonWidget(width / 2 - 100, height / 2, 95, 20,
                        Component.translatable("gui.skin_library.prepare.hair"),
                        v -> {
                            workspace.skinType = SkinType.HAIR;
                            color.setHSV(0, 0, 0.5);
                            setPage(Page.EDITOR);
                        }));

                addRenderableWidget(new ButtonWidget(width / 2 + 5, height / 2, 95, 20,
                        Component.translatable("gui.skin_library.prepare.clothing"),
                        v -> {
                            workspace.skinType = SkinType.CLOTHING;
                            setPage(Page.EDITOR);
                        }));

                // help
                addRenderableWidget(new TooltipButtonWidget(width / 2 - 10, height / 2 + 30, 20, 20,
                        Component.literal("?"),
                        Component.translatable("gui.skin_library.help"),
                        v -> {
                            openHelp();
                        }));
            }
            case LOGIN -> {
                addRenderableWidget(new ButtonWidget(width / 2 - 50, height / 2 + 25, 100, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> {
                            setPage(Page.LIBRARY);
                        }));
            }
            case DETAIL -> {
                if (canModifyFocusedContent()) {
                    //tag name
                    EditBox tagNameWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 200, height / 2 - 100 + 2, 95, 16,
                            Component.literal("")));
                    tagNameWidget.setMaxLength(20);
                    tagNameWidget.setSuggestion("New Tag Name");
                    tagNameWidget.setResponder(v -> {
                        tagNameWidget.setSuggestion(null);
                    });

                    //add tag
                    addRenderableWidget(new TooltipButtonWidget(width / 2 - 100, height / 2 - 100, 40, 20, "gui.skin_library.add", sender -> {
                        String tag = tagNameWidget.getValue().trim().toLowerCase(Locale.ROOT);
                        if (!tag.isEmpty()) {
                            setTag(focusedContent.contentid(), tag, true);
                            tagNameWidget.setValue("");
                            rebuild();
                        }
                    }));
                }

                //controls
                drawControls(focusedContent, true, width / 2 + 130, height / 2 + 60);

                //close
                addRenderableWidget(new ButtonWidget(width / 2 - 40, height / 2 + 60, 80, 20,
                        Component.translatable("gui.skin_library.close"),
                        v -> {
                            setPage(Page.LIBRARY);
                        }));


                //tags
                int ty = height / 2 - 70;
                for (String tag : focusedContent.tags()) {
                    if (!tag.equals("clothing") && !tag.equals("hair") && !tag.equals("invalid") || isModerator()) {
                        int w = font.width(tag) + 10;
                        if (canModifyFocusedContent()) {
                            addRenderableWidget(new ButtonWidget(width / 2 - 200, ty, 20, 20,
                                    Component.literal("X"),
                                    v -> {
                                        setTag(focusedContent.contentid(), tag, false);
                                        rebuild();
                                    }));
                        }
                        addRenderableWidget(new ButtonWidget(width / 2 - 200 + 20, ty, w, 20,
                                Component.literal(tag),
                                v -> {
                                }));
                        ty += 20;
                    }
                }
            }
            case DELETE -> {
                addRenderableWidget(new ButtonWidget(width / 2 - 65, height / 2, 60, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> {
                            setPage(Page.DETAIL);
                        }));
                addRenderableWidget(new ButtonWidget(width / 2 + 5, height / 2, 60, 20,
                        Component.translatable("gui.skin_library.delete"),
                        v -> {
                            removeContent(deleteConfirmationContent.contentid());
                            setPage(Page.LIBRARY);
                        }));
            }
            case REPORT -> {
                addRenderableWidget(new TooltipButtonWidget(width / 2 - 105, height / 2, 100, 20,
                        Component.translatable("gui.skin_library.report_invalid"),
                        Component.translatable("gui.skin_library.report_invalid_tooltip"),
                        v -> {
                            reportContent(reportConfirmationContent.contentid(), "INVALID");
                            setPage(Page.DETAIL);
                        }));
                addRenderableWidget(new TooltipButtonWidget(width / 2 + 5, height / 2, 100, 20,
                        Component.translatable("gui.skin_library.report_default"),
                        Component.translatable("gui.skin_library.report_default_tooltip"),
                        v -> {
                            reportContent(reportConfirmationContent.contentid(), "DEFAULT");
                            setPage(Page.DETAIL);
                        }));

                addRenderableWidget(new ButtonWidget(width / 2 - 50, height / 2 + 22, 100, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> {
                            setPage(Page.DETAIL);
                        }));
            }
            case EDITOR -> {
                // name
                textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 60, height / 2 - 105, 120, 20,
                        Component.translatable("gui.skin_library.name")));
                textFieldWidget.setMaxLength(1024);
                textFieldWidget.setValue(workspace.title);
                if (workspace.title.isEmpty()) {
                    textFieldWidget.setSuggestion(workspace.title);
                }
                textFieldWidget.setFocused(false);
                textFieldWidget.setResponder(v -> {
                    workspace.title = v;
                    textFieldWidget.setSuggestion(null);
                });

                // help
                addRenderableWidget(new TooltipButtonWidget(width / 2 + 65, height / 2 - 105, 20, 20,
                        Component.literal("?"),
                        Component.translatable("gui.skin_library.tool_help"),
                        v -> {
                            openHelp();
                        }));

                //gender
                addRenderableWidget(CycleButton.builder(Gender::getText, workspace.gender)
                        .withValues(Gender.MALE, Gender.NEUTRAL, Gender.FEMALE)
                        .displayOnlyValue()
                        .create(width / 2 - 200, height / 2 - 80, 105, 20, Component.literal(""), (button, gender) -> {
                            this.workspace.gender = gender;
                        }));

                // temperature
                if (workspace.skinType == SkinType.CLOTHING) {
                    addRenderableWidget(new IntegerSliderWidget(width / 2 - 200, height / 2 - 60, 105, 20,
                            workspace.temperature,
                            -2, 2,
                            v -> {
                                workspace.temperature = v;
                            },
                            v -> Component.translatable("gui.skin_library.temperature." + (v + 2)),
                            () -> Component.translatable("gui.skin_library.temperature.tooltip")
                    ));
                }

                //profession
                if (workspace.skinType == SkinType.CLOTHING) {
                    int ox = 0;
                    int oy = 0;
                    List<ItemButtonWidget> widgets = new LinkedList<>();
                    for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
                        String professionPath = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath();
                        MutableComponent text = Component.translatable("entity.minecraft.villager." + professionPath);
                        ItemButtonWidget widget = addRenderableWidget(new ItemButtonWidget(width / 2 - 200 + ox * 21, height / 2 - 30 + oy * 21, 20, text,
                                ProfessionIcons.ICONS.getOrDefault(professionPath, Items.OAK_SAPLING.getDefaultInstance()),
                                v -> {
                                    workspace.profession = "none".equals(professionPath) ? null : professionPath;
                                    widgets.forEach(b -> b.active = true);
                                    v.active = false;
                                }));
                        widget.active = !Objects.equals(workspace.profession, "none".equals(professionPath) ? null : professionPath);
                        widgets.add(widget);
                        ox++;
                        if (ox >= 5) {
                            ox = 0;
                            oy++;
                        }
                    }
                }


                int y = height / 2 - 5;

                if (workspace.skinType == SkinType.CLOTHING) {
                    //hue
                    color.hueWidget = addRenderableWidget(new HorizontalColorPickerWidget(width / 2 + 100, y, 100, 15,
                            color.hue / 360.0,
                            MCA.locate("textures/colormap/hue.png"),
                            (vx, vy) -> {
                                color.setHSV(
                                        vx * 360,
                                        color.saturation,
                                        color.brightness
                                );
                            }));

                    //saturation
                    color.saturationWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 100, y + 20, 100, 15,
                            color.saturation,
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, 0.0, 1.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, 1.0, 1.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            (vx, vy) -> {
                                color.setHSV(
                                        color.hue,
                                        vx,
                                        color.brightness
                                );
                            }));
                }

                //brightness
                color.brightnessWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 100, y + 40, 100, 15,
                        color.brightness,
                        () -> {
                            double[] doubles = ClientUtils.HSV2RGB(color.hue, color.saturation, 0.0);
                            return new float[]{
                                    (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                            };
                        },
                        () -> {
                            double[] doubles = ClientUtils.HSV2RGB(color.hue, color.saturation, 1.0);
                            return new float[]{
                                    (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                            };
                        },
                        (vx, vy) -> {
                            color.setHSV(
                                    color.hue,
                                    color.saturation,
                                    vx
                            );
                        }));

                //fill tool strength
                addRenderableWidget(new IntegerSliderWidget(width / 2 + 100, y + 60, 100, 20,
                        workspace.fillToolThreshold,
                        0, 128,
                        v -> {
                            workspace.fillToolThreshold = v;
                        },
                        v -> Component.translatable("gui.skin_library.fillToolThreshold"),
                        () -> Component.translatable("gui.skin_library.fillToolThreshold.tooltip")
                ));

                //undo
                addRenderableWidget(new ButtonWidget(width / 2 + 100, y + 85, 100, 20,
                        Component.translatable("gui.skin_library.undo"),
                        v -> {
                            workspace.undo();
                        }));

                if (workspace.skinType == SkinType.HAIR) {
                    // make black and white
                    addRenderableWidget(new ButtonWidget(width / 2 + 100, y - 20, 100, 20,
                            Component.translatable("gui.skin_library.remove_saturation"),
                            v -> {
                                workspace.removeSaturation();
                            }));

                    //less contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 100, y, 50, 20,
                            Component.literal("C -"),
                            Component.translatable("gui.skin_library.less_contrast"),
                            v -> {
                                workspace.addContrast(-0.15f);
                            }));

                    //more contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 150, y, 50, 20,
                            Component.literal("C +"),
                            Component.translatable("gui.skin_library.more_contrast"),
                            v -> {
                                workspace.addContrast(0.15f);
                            }));

                    //less contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 100, y + 20, 50, 20,
                            Component.literal("B -"),
                            Component.translatable("gui.skin_library.less_brightness"),
                            v -> {
                                workspace.addBrightness(-8);
                            }));

                    //more contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 150, y + 20, 50, 20,
                            Component.literal("B +"),
                            Component.translatable("gui.skin_library.more_brightness"),
                            v -> {
                                workspace.addBrightness(8);
                            }));

                    //hair color
                    Genetics genetics = villagerVisualization.getGenetics();
                    addRenderableWidget(new ColorPickerWidget(width / 2 - 200, height / 2 - 30, 100, 100,
                            genetics.getGene(Genetics.PHEOMELANIN),
                            genetics.getGene(Genetics.EUMELANIN),
                            MCA.locate("textures/colormap/villager_hair.png"),
                            (vx, vy) -> {
                                genetics.setGene(Genetics.PHEOMELANIN, vx.floatValue());
                                genetics.setGene(Genetics.EUMELANIN, vy.floatValue());
                            }));
                }

                // cancel
                addRenderableWidget(new ButtonWidget(width / 2 - 80, height / 2 + 80, 75, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> {
                            setPage(Page.LIBRARY);
                        }));

                // publish
                addRenderableWidget(new ButtonWidget(width / 2 + 5, height / 2 + 80, 75, 20,
                        Component.translatable("gui.skin_library.publish"),
                        v -> {
                            publish();
                        }));
            }
        }
    }

    private void drawControls(LiteContent content, boolean advanced, int cx, int cy) {
        int w = advanced ? 20 : 16;
        List<TooltipButtonWidget> widgets = new LinkedList<>();

        // subscribe
        if (isOp() || Config.getServerConfig().allowEveryoneToAddContentGlobally) {
            widgets.add(new ToggleableTooltipIconButtonWidget(0, 0, 0, 3 * 16,
                    getServerContentById(content.contentid()).isPresent(),
                    Component.translatable("gui.skin_library.subscribe"),
                    v -> {
                        if (((ToggleableTooltipButtonWidget) v).toggle) {
                            Network.sendToServer(new RemoveCustomClothingMessage(content.hasTag("clothing") ? RemoveCustomClothingMessage.Type.CLOTHING : RemoveCustomClothingMessage.Type.HAIR, Identifier.fromNamespaceAndPath("immersive_library", String.valueOf(content.contentid()))));
                        } else {
                            toListEntry(content).ifPresent(e -> {
                                Network.sendToServer(AddCustomClothingMessage.fromEntry(e));
                            });
                        }
                        ((ToggleableTooltipButtonWidget) v).toggle = !((ToggleableTooltipButtonWidget) v).toggle;
                    }));
        }

        // like
        if (authenticated) {
            widgets.add(new ToggleableTooltipIconButtonWidget(0, 0, 16, 3 * 16,
                    isLiked(content),
                    Component.translatable("gui.skin_library.like"),
                    v -> {
                        ((ToggleableTooltipButtonWidget) v).toggle = !((ToggleableTooltipButtonWidget) v).toggle;
                        setLike(content.contentid(), ((ToggleableTooltipButtonWidget) v).toggle);
                    }));
        }

        // edit
        if (advanced && canModifyContent(content)) {
            widgets.add(new ToggleableTooltipIconButtonWidget(0, 0, 2 * 16, 3 * 16,
                    false,
                    Component.translatable("gui.skin_library.edit"),
                    v -> {

                        SkinCache.getImage(content).ifPresent(image -> {
                            SkinCache.getMeta(content).ifPresent(meta -> {
                                workspace = new Workspace(image, meta, content);
                                setPage(Page.EDITOR);
                                if (workspace.skinType == SkinType.HAIR) color.setHSV(0, 0, 0.5);
                            });
                        });
                    }));
        }

        // report
        if (advanced && authenticated) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 10 * 16, 3 * 16,
                    true,
                    Component.translatable("gui.skin_library.report"),
                    v -> {
                        reportConfirmationContent = content;
                        setPage(Page.REPORT);
                    }));
        }

        // delete
        if (advanced && canModifyContent(content)) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 3 * 16, 3 * 16,
                    true,
                    Component.translatable("gui.skin_library.delete"),
                    v -> {
                        deleteConfirmationContent = content;
                        setPage(Page.DELETE);
                    }));
        }

        // advanced
        if (!advanced) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 4 * 16, 4 * 16,
                    true,
                    Component.translatable("gui.skin_library.details"),
                    v -> {
                        if (isPanning && isModerator()) {
                            //admin tool
                            reportContent(content.contentid(), "DEFAULT");
                            refreshContentList();
                        } else {
                            focusedContent = content;
                            setPage(Page.DETAIL);
                        }
                    }));
        }

        // ban
        if (advanced && isModerator()) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 5 * 16, 3 * 16,
                    false,
                    Component.translatable("gui.skin_library.ban"),
                    v -> {
                        setBan(content.userid(), true);
                        refreshContentList();
                    }));

            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 11 * 16, 3 * 16,
                    false,
                    Component.translatable("gui.skin_library.unban"),
                    v -> {
                        setBan(content.userid(), false);
                        refreshContentList();
                    }));
        }

        // add the widgets
        int wx = cx - widgets.size() * w / 2;
        for (TooltipButtonWidget buttonWidget : widgets) {
            addRenderableWidget(buttonWidget);
            buttonWidget.setX(wx);
            buttonWidget.setY(cy);
            wx += w;
        }
    }

    private Optional<SkinListEntry> toListEntry(LiteContent content) {
        return SkinCache.getMeta(content).map(meta -> {
            if (content.hasTag("clothing")) {
                return new Clothing("immersive_library:" + content.contentid(), meta.getProfession(), meta.getTemperature(), false, meta.getGender());
            } else {
                return new Hair("immersive_library:" + content.contentid());
            }
        });
    }

    private boolean canModifyFocusedContent() {
        return canModifyContent(focusedContent);
    }

    private boolean canModifyContent(LiteContent content) {
        return currentUser != null && (currentUser.moderator() || currentUser.userid() == content.userid());
    }

    private boolean isModerator() {
        return currentUser != null && currentUser.moderator();
    }

    private boolean isLiked(LiteContent content) {
        return currentUser != null && (currentUser.likes().stream().anyMatch(c -> c.contentid() == content.contentid()));
    }

    public void setPage(Page page) {
        if (Thread.currentThread() != thread) {
            assert minecraft != null;
            minecraft.executeIfPossible(() -> setPage(page));
            return;
        }

        clearError();

        if ((page == Page.EDITOR_TYPE || page == Page.EDITOR_PREPARE) && !authenticated) {
            setPage(Page.EDITOR_LOCKED);
            return;
        }

        if (page == Page.HELP) {
            openHelp();
            return;
        }

        if (page == Page.LOGIN) {
            if (Auth.loadToken() == null) {
                isBrowserOpen = true;
                Auth.authenticate(getPlayerName());
            } else {
                isBrowserOpen = false;
            }
        }

        if (page == Page.LOGOUT) {
            authenticated = false;
            currentUser = null;
            Auth.clearToken();
            refreshPage();
            return;
        }

        this.page = page;

        if (page == Page.EDITOR) {
            x0 = 0.0f;
            x1 = 1.0f;
            y0 = 0.0f;
            y1 = 1.0f;
            uploading = false;
        }

        if (page == Page.LIBRARY) {
            refreshContentList();
        } else {
            rebuild();
        }
    }

    private void openHelp() {
        try {
            Util.getPlatform().openUri(URI.create("https://github.com/Luke100000/minecraft-comes-alive/wiki/Skin-Editor"));
        } catch (Exception e) {
            MCA.LOGGER.error(e);
        }
    }

    private void refreshContentList() {
        if (Thread.currentThread() != thread) {
            assert minecraft != null;
            minecraft.executeIfPossible(this::refreshContentList);
            return;
        }

        refreshServerContent();

        // fetch the list matching the current subscription filter
        List<LiteContent> newList;
        switch (subscriptionFilter) {
            case LIBRARY -> {
                loadPage();
                newList = libraryContents;
            }
            case GLOBAL -> {
                newList = serverContent;
            }
            case LIKED -> {
                newList = (currentUser != null ? currentUser.likes() : Collections.emptyList());
            }
            case SUBMISSIONS -> {
                newList = currentUser != null ? currentUser.submissions() : Collections.emptyList();
            }
            default -> throw new IllegalStateException("Unexpected value: " + subscriptionFilter);
        }

        // apply paging here
        if (subscriptionFilter != SubscriptionFilter.LIBRARY && !newList.isEmpty()) {
            if (selectionPage * CLOTHES_PER_PAGE >= newList.size()) {
                newList = new LinkedList<>();
            } else {
                newList = newList.subList(selectionPage * CLOTHES_PER_PAGE, Math.min(newList.size(), (selectionPage + 1) * CLOTHES_PER_PAGE));
            }
        }

        // add to visual list
        contents.clear();
        contents.addAll(newList);

        // last page reached, go one back
        if (contents.isEmpty() && selectionPage > 0) {
            selectionPage--;
            if (subscriptionFilter == SubscriptionFilter.LIBRARY) {
                loadPage();
            } else {
                refreshContentList();
            }
            return;
        }

        rebuild();

        setSelectionPage(selectionPage);
    }

    private String getPlayerName() {
        return Minecraft.getInstance().player == null ? "Unknown" : Minecraft.getInstance().player.getGameProfile().name();
    }

    private boolean isOp() {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private String encodeCurrentImage() throws IOException {
        Path tempPath = Files.createTempFile("mca-skin-library-upload-", ".png");
        try {
            workspace.currentImage.writeToFile(tempPath);
            return Base64.getEncoder().encodeToString(Files.readAllBytes(tempPath));
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void setSelectionPage(int p) {
        if (pageWidget != null) {
            selectionPage = Math.max(0, p);
            pageWidget.setMessage(Component.translatable("gui.villager_editor.page", selectionPage + 1));
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        Path path = paths.getFirst();
        loadImage(path.toString());
    }

    private void loadImage(String path) {
        try (InputStream stream = openImageStream(path)) {
            if (stream == null) {
                return;
            }

            NativeImage image = NativeImage.read(stream);

            if (image.getWidth() == 64 && image.getHeight() == 64) {
                if (SkinPorter.isSlimFormat(image)) {
                    SkinPorter.convertSlimToDefault(image);
                }
                workspace = new Workspace(image);
                setPage(Page.EDITOR_TYPE);
            } else if (image.getWidth() == 64 && image.getHeight() == 32) {
                // port legacy skins
                workspace = new Workspace(SkinPorter.portLegacySkin(image));
                setPage(Page.EDITOR_TYPE);
            } else {
                setError(Component.translatable("gui.skin_library.not_64"));
            }
        } catch (IOException e) {
            MCA.LOGGER.error(e);
        }
    }

    private InputStream openImageStream(String path) {
        try {
            return URI.create(path).toURL().openStream();
        } catch (Exception exception) {
            try {
                return new FileInputStream(path);
            } catch (Exception e) {
                MCA.LOGGER.error(e);
                return null;
            }
        }
    }

    private void publish() {
        if (workspace.title.equals("Unnamed Asset")) {
            setError(Component.translatable("gui.skin_library.choose_name"));
            return;
        }

        if (!Utils.verify(workspace.currentImage)) {
            setError(Component.translatable("gui.skin_library.read_the_help"));
            return;
        }

        if (workspace.skinType == SkinType.HAIR && !Utils.verifyHair(workspace.currentImage) && !skipHairWarning) {
            setError(Component.translatable("gui.skin_library.read_the_help_hair"));
            skipHairWarning = true;
            return;
        }
        skipHairWarning = false;

        if (!uploading) {
            uploading = true;
            CompletableFuture.runAsync(() -> {
                if (Auth.hasToken()) {
                    Response request = null;
                    try {
                        request = request(workspace.contentid == -1 ? Api.HttpMethod.POST : Api.HttpMethod.PUT, workspace.contentid == -1 ? ContentIdResponse.class : SuccessResponse.class, workspace.contentid == -1 ? "content/mca" : ("content/mca/" + workspace.contentid), Map.of(

                        ), Map.of(
                                "title", workspace.title,
                                "meta", workspace.toListEntry().toJson().toString(),
                                "data", encodeCurrentImage()
                        ));
                    } catch (IOException e) {
                        MCA.LOGGER.error(e);
                    }

                    if (request instanceof ContentIdResponse || request instanceof SuccessResponse) {
                        Response finalRequest = request;
                        reloadDatabase(() -> {
                            int contentid = finalRequest instanceof ContentIdResponse(
                                    int contentid1
                            ) ? contentid1 : workspace.contentid;

                            // default tags
                            setTag(contentid, workspace.skinType.name().toLowerCase(Locale.ROOT), true);
                            if (workspace.profession != null) {
                                setTag(contentid, workspace.profession.replace("mca.", ""), true);
                            }

                            // open detail page
                            getSubmittedContent(contentid).or(() -> getContentById(contentid)).ifPresent(content -> {
                                focusedContent = content;
                                setPage(Page.DETAIL);
                                uploading = false;
                            });

                            //also refresh our cache
                            SkinCache.enforceSync(contentid);
                        });
                    } else if (request instanceof ErrorResponse response) {
                        if (response.code() == 428) {
                            setError(Component.translatable("gui.skin_library.upload_duplicate"));
                        } else {
                            setError(Component.translatable("gui.skin_library.upload_failed"));
                        }
                        uploading = false;
                    }
                }
            });
        } else {
            setError(Component.translatable("gui.skin_library.already_uploading"));
        }
    }

    private Optional<LiteContent> getContentById(int contentid) {
        return Stream.concat(libraryContents.stream(), serverContent.stream()).filter(v -> v.contentid() == contentid).findAny();
    }

    private Optional<LiteContent> getServerContentById(int contentid) {
        return serverContent.stream().filter(v -> v.contentid() == contentid).findAny();
    }

    private Optional<LiteContent> getSubmittedContent(int contentid) {
        return currentUser == null ? Optional.empty() : currentUser.submissions().stream().filter(v -> v.contentid() == contentid).findAny();
    }

    private void setTag(int contentid, String tag, boolean add) {
        if (Auth.hasToken()) {
            request(add ? Api.HttpMethod.POST : Api.HttpMethod.DELETE, SuccessResponse.class, "tag/mca/" + contentid + "/" + tag);
            getContentById(contentid).ifPresent(c -> {
                if (add) {
                    c.tags().add(tag);
                } else {
                    c.tags().remove(tag);
                }
            });
            getSubmittedContent(contentid).ifPresent(c -> {
                if (add) {
                    c.tags().add(tag);
                } else {
                    c.tags().remove(tag);
                }
            });
        }
    }

    private void removeContent(int contentId) {
        if (Auth.hasToken()) {
            request(Api.HttpMethod.DELETE, SuccessResponse.class, "content/mca/" + contentId);
            removeContentLocally(contentId);
        }
    }

    private void removeContentLocally(int contentId) {
        libraryContents.removeIf(v -> v.contentid() == contentId);

        if (currentUser != null) {
            currentUser.likes().removeIf(v -> v.contentid() == contentId);
            currentUser.submissions().removeIf(v -> v.contentid() == contentId);
        }
    }

    private void reportContent(int contentId, String reason) {
        if (Auth.hasToken()) {
            request(Api.HttpMethod.POST, SuccessResponse.class, "report/mca/" + contentId + "/" + reason);

            if (reason.equals("DEFAULT")) {
                removeContentLocally(contentId);
            }

            setError(Component.translatable("gui.skin_library.reported"));
        }
    }

    private void setLike(int contentid, boolean add) {
        if (Auth.hasToken() && currentUser != null) {
            request(add ? Api.HttpMethod.POST : Api.HttpMethod.DELETE, SuccessResponse.class, "like/mca/" + contentid);

            if (add) {
                getContentById(contentid).ifPresent(currentUser.likes()::add);
            } else {
                currentUser.likes().removeIf(v -> v.contentid() == contentid);
            }
        }
    }

    private void setBan(int userid, boolean banned) {
        if (Auth.hasToken() && currentUser != null) {
            request(Api.HttpMethod.PUT, SuccessResponse.class, "user/" + userid, Map.of(
                    "banned", Boolean.toString(banned)
            ));
        }
    }

    public void refreshPage() {
        setPage(page);
    }

    public void clearError() {
        this.error = null;
    }

    public void setError(Component text) {
        this.error = text;
    }

    private <T> void addServerContent(Map<String, T> map, String type) {
        for (Map.Entry<String, T> entry : map.entrySet()) {
            if (entry.getKey().startsWith("immersive_library:")) {
                try {
                    int contentid = Integer.parseInt(entry.getKey().substring(18));
                    serverContent.add(getContentById(contentid).orElse(new LiteContent(
                            contentid, -1, "unknown", -1, Set.of(type), "unknown", -1
                    )));
                } catch (NumberFormatException ignored) {
                    //nop
                }
            }
        }
    }

    @Override
    public void skinListUpdatedCallback() {
        refreshServerContent();

        if (page == Page.LIBRARY) {
            refreshContentList();
        }
    }

    private void refreshServerContent() {
        serverContent.clear();
        addServerContent(ClientSkinCatalog.clothing(), "clothing");
        addServerContent(ClientSkinCatalog.hair(), "hair");
    }

    public enum Page {
        LIBRARY,
        EDITOR_LOCKED,
        EDITOR_PREPARE,
        EDITOR_TYPE,
        EDITOR,
        HELP,
        LOGIN,
        LOGOUT,
        DETAIL,
        DELETE,
        LOADING,
        REPORT
    }

    public enum SkinType {
        CLOTHING,
        HAIR
    }

    public enum SortingMode {
        LIKES("likes"),
        NEWEST("date"),
        RECOMMENDATIONS("recommendations"),
        REPORTS("reports");

        public final String order;

        SortingMode(String order) {
            this.order = order;
        }
    }

    public enum SubscriptionFilter {
        LIBRARY,
        GLOBAL,
        LIKED,
        SUBMISSIONS;

        public static Component getText(SubscriptionFilter t) {
            return Component.translatable("gui.skin_library.subscription_filter." + t.name().toLowerCase(Locale.ROOT));
        }
    }
}
