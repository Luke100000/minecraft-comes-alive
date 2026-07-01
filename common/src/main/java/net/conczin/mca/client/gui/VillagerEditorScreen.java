package net.conczin.mca.client.gui;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.widget.*;
import net.conczin.mca.client.resources.*;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.GetVillagerRequest;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.conczin.mca.network.c2s.VillagerNameRequest;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.FaceList;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class VillagerEditorScreen extends Screen implements SkinListUpdateListener {
    protected static final int DATA_WIDTH = 175;

    private static final int TRAITS_PER_PAGE = 8;
    private static final int LAYERED_HAIR_PER_PAGE = 6;

    private static final float MIN_PREVIEW_ZOOM = 0.7F;
    private static final float MAX_PREVIEW_ZOOM = 1.4F;
    private static final Identifier PREVIEW_MOUSE_FOLLOW_TEXTURE = MCA.locate("textures/gui/preview_mouse_follow.png");

    private static final int VOICE_PREVIEW_BUTTON_WIDTH = 22;
    private static final int STEVE_PROPORTIONS_BUTTON_WIDTH = 22;
    private static final float STEVE_RAW_WIDTH_SCALE = 1.0F;
    private static final float STEVE_RAW_HEIGHT_SCALE = 0.9F;

    @NotNull
    protected final VillagerEntityMCA villager = PreviewEntities.villager();
    @NotNull
    protected final VillagerEntityMCA villagerVisualization = PreviewEntities.villager();

    final UUID villagerUUID;
    final UUID playerUUID;
    final boolean allowPlayerModel;
    final boolean allowVillagerModel;
    final int CLOTHES_H = 8;
    final int CLOTHES_V = 2;
    final int CLOTHES_PER_PAGE = CLOTHES_H * CLOTHES_V + 1;
    private final ColorSelector color = new ColorSelector();
    protected String page;
    protected CompoundTag villagerData;
    ButtonWidget widgetMasculine;
    ButtonWidget widgetFeminine;
    private int villagerBreedingAge;
    private int traitPage = 0;
    private EditBox villagerNameField;
    private boolean hsvColoredSkin;
    private boolean hsvColoredHair;
    private int eyeColorTarget = 0; // 0 = Hair, 1 = Left/Both Eye, 2 = Right Eye
    private boolean hsvColoredEyes = false;
    private boolean hsvColoredEyesLeft = false;
    private int clothingPage;
    private int clothingPageCount;
    private ButtonWidget pageButtonWidget;
    private List<String> filteredClothing = new LinkedList<>();
    private List<String> filteredHairStyles = new LinkedList<>();
    private List<String> filteredBodySkins = new LinkedList<>();
    private List<String> filteredLayeredHair = new LinkedList<>();
    private Gender filterGender = Gender.NEUTRAL;
    private String searchString = "";
    private int hoveredClothingId;
    private ButtonWidget villagerSkinWidget;
    private ButtonWidget playerSkinWidget;
    private ButtonWidget vanillaSkinWidget;
    private ButtonWidget doneWidget;
    private ButtonWidget presetsButton;
    private ButtonWidget exportSkinButton;
    private ButtonWidget genderButtonFemale;
    private ButtonWidget genderButtonMale;
    private boolean restoreHudHidden;
    private float previewRotation;
    private float previewZoom = 1.0F;
    private boolean draggingPreview;
    private long lastFrameTime = -1L;
    private boolean rotatePreviewLeft;
    private boolean rotatePreviewRight;
    private boolean previewFollowsMouse = true;

    private static final int PRESETS_PER_PAGE = 4;
    private final File presetsDir = new File(Minecraft.getInstance().gameDirectory, "config/mca/presets");
    private final List<String> presetNames = new ArrayList<>();
    private String selectedPreset = null;
    private String presetsReturnPage = "general";
    private int currentPage = 0;
    private int maxPage = 0;
    private EditBox nameField;
    private ButtonWidget useButton;
    private ButtonWidget deleteButton;
    private CompoundTag presetsBackupNbt = null;
    private boolean hasVisualChange = false;

    public VillagerEditorScreen(UUID villagerUUID, UUID playerUUID, boolean allowPlayerModel, boolean allowVillagerModel) {
        super(Component.translatable("gui.VillagerEditorScreen.title"));
        this.villagerUUID = villagerUUID;
        this.playerUUID = playerUUID;
        this.allowPlayerModel = allowPlayerModel;
        this.allowVillagerModel = allowVillagerModel;

        requestVillagerData();
        setPage(Objects.requireNonNullElse(page, "loading"));
    }

    public VillagerEditorScreen(UUID villagerUUID, UUID playerUUID) {
        this(villagerUUID, playerUUID, MCAClient.isPlayerRendererAllowed(), MCAClient.isVillagerRendererAllowed());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void added() {
        super.added();
        Minecraft minecraft = Minecraft.getInstance();
        // 26.2 keeps HUD visibility on Hud instead of Options, so preserve the current state here.
        restoreHudHidden = minecraft.gui.hud.isHidden();
        if (!restoreHudHidden) {
            minecraft.gui.hud.toggle();
        }
    }

    @Override
    public void removed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.hud.isHidden() != restoreHudHidden) {
            minecraft.gui.hud.toggle();
        }
        super.removed();
    }

    @Override
    public boolean showsActiveEffects() {
        return true;
    }

    @Override
    public void init() {
        setPage(page);
    }

    private int doubleGeneSliders(int y, Genetics.GeneType... genes) {
        boolean right = false;
        for (Genetics.GeneType g : genes) {
            int x = width / 2 + (right ? DATA_WIDTH / 2 : 0);
            int widgetWidth = DATA_WIDTH / 2;
            if (g == Genetics.VOICE_TONE) {
                addVoicePreviewButton(x, y);
                addGeneSlider(x + VOICE_PREVIEW_BUTTON_WIDTH + 2, y, widgetWidth - VOICE_PREVIEW_BUTTON_WIDTH - 2, g);
            } else {
                addGeneSlider(x, y, widgetWidth, g);
            }
            if (right) {
                y += 20;
            }
            right = !right;
        }
        return y + 4 + (right ? 20 : 0);
    }

    private void addGeneSlider(int x, int y, int widgetWidth, Genetics.GeneType gene) {
        Genetics genetics = villager.getGenetics();
        addRenderableWidget(new GeneSliderWidget(x, y, widgetWidth, 20, Component.translatable(gene.getTranslationKey()), genetics.getGene(gene), b -> genetics.setGene(gene, b.floatValue())));
    }

    private void addVoicePreviewButton(int x, int y) {
        ButtonWidget previewButton = addRenderableWidget(new ButtonWidget(
                x,
                y,
                VOICE_PREVIEW_BUTTON_WIDTH,
                20,
                Component.literal(">"),
                b -> SpeechManager.INSTANCE.playPreview(villager),
                Component.translatable("gui.villager_editor.preview_voice.tooltip")
        ));
        previewButton.active = SpeechManager.INSTANCE.canPreviewVoiceTone();
    }

    private void addSteveProportionsButton(int x, int y) {
        addRenderableWidget(new TooltipButtonWidget(
                x,
                y,
                STEVE_PROPORTIONS_BUTTON_WIDTH,
                20,
                Component.literal("V"),
                Component.translatable("gui.villager_editor.steve_proportions.tooltip"),
                b -> {
                    setSteveProportions();
                    init();
                }
        ));
    }

    private void setSteveProportions() {
        Genetics genetics = villager.getGenetics();
        genetics.setGene(Genetics.SIZE, getSteveProportionsMarker(Genetics.SIZE));
        genetics.setGene(Genetics.WIDTH, getSteveProportionsMarker(Genetics.WIDTH));
        villager.refreshDimensions();
    }

    private float getSteveProportionsMarker(Genetics.GeneType gene) {
        if (gene == Genetics.SIZE) {
            return geneValueForRawScale(STEVE_RAW_HEIGHT_SCALE, getNonGeneticVerticalScaleFactor());
        }
        if (gene == Genetics.WIDTH) {
            return geneValueForRawScale(STEVE_RAW_WIDTH_SCALE, getNonGeneticHorizontalScaleFactor());
        }
        return Float.NaN;
    }

    private float geneValueForRawScale(float targetRawScale, float nonGeneticScaleFactor) {
        return Mth.clamp(2.0F * (targetRawScale / nonGeneticScaleFactor - 0.75F), 0.0F, 1.0F);
    }

    private float getNonGeneticVerticalScaleFactor() {
        return villager.getTraits().getVerticalScaleFactor()
               * villager.getVillagerDimensions().getHeight()
               * villager.getGenetics().getGender().getScaleFactor();
    }

    private float getNonGeneticHorizontalScaleFactor() {
        return villager.getTraits().getHorizontalScaleFactor()
               * villager.getVillagerDimensions().getWidth()
               * villager.getGenetics().getGender().getHorizontalScaleFactor();
    }

    private int integerChanger(int y, IntConsumer onClick, Supplier<Component> content) {
        int bw = 22;
        ButtonWidget current = addRenderableWidget(new ButtonWidget(width / 2 + bw * 2, y, DATA_WIDTH - bw * 4, 20, content.get(), b -> {
        }));
        addRenderableWidget(new ButtonWidget(width / 2, y, bw, 20, Component.literal("-5"), b -> {
            onClick.accept(-5);
            current.setMessage(content.get());
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + bw, y, bw, 20, Component.literal("-50"), b -> {
            onClick.accept(-50);
            current.setMessage(content.get());
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - bw * 2, y, bw, 20, Component.literal("+50"), b -> {
            onClick.accept(50);
            current.setMessage(content.get());
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - bw, y, bw, 20, Component.literal("+5"), b -> {
            onClick.accept(5);
            current.setMessage(content.get());
        }));
        return y + 22;
    }

    protected void setPage(String page) {
        String prevPage = this.page;
        if (page.equals("presets") && prevPage != null && !prevPage.equals("presets") && !prevPage.equals("loading")) {
            presetsReturnPage = prevPage;
        }
        if (this.page != null && this.page.equals("presets") && presetsBackupNbt != null && !page.equals("presets")) {
            if (villagerData != null) {
                villager.load(TagValueInput.create(ProblemReporter.DISCARDING, villager.registryAccess(), villagerData));
            }
            villager.readNbtForConversion(presetsBackupNbt);
            villager.refreshDimensions();
            presetsBackupNbt = null;
        }

        this.page = page;

        clearWidgets();

        if (page.equals("loading")) {
            return;
        }

        if (page.equals("presets")) {
            if (presetsBackupNbt == null) {
                presetsBackupNbt = villager.toNbtForConversion();
            }
            if (prevPage == null || !prevPage.equals("presets")) {
                hasVisualChange = false;
                selectedPreset = null;
            }
            refreshPresets();
        }

        //page selection
        if (shouldShowPageSelection()) {
            String[] pages = getPages();
            int w = DATA_WIDTH * 2 / pages.length;
            int x = (int) (width / 2.0 - pages.length / 2.0 * w);
            for (String p : pages) {
                addRenderableWidget(new ButtonWidget(x, height / 2 - 105, w, 20, Component.translatable("gui.villager_editor.page." + p), sender -> setPage(p))).active = !isMainPageSelected(p);
                x += w;
            }

            //close
            doneWidget = addRenderableWidget(new ButtonWidget(width / 2 - DATA_WIDTH + 20, height / 2 + 98, DATA_WIDTH - 40, 20, Component.translatable("gui.done"), sender -> {
                syncVillagerData();
                onClose();
            }));

            boolean isPresetsPage = page.equals("presets");
            boolean isTraitsPage = page.equals("traits");
            int presetsX = width / 2 - DATA_WIDTH + 10;

            presetsButton = addRenderableWidget(new ButtonWidget(
                    presetsX,
                    height / 2 - 80,
                    75,
                    20,
                    Component.translatable("gui.mca.presets"),
                    b -> setPage("presets")
            ));
            presetsButton.active = !isPresetsPage;

            exportSkinButton = addRenderableWidget(new ButtonWidget(
                    width / 2 - DATA_WIDTH + 90,
                    height / 2 - 80,
                    75,
                    20,
                    isTraitsPage
                            ? Component.translatable("gui.mca.trait_shaders",
                            Component.translatable(Config.getInstance().enablePlayerShaders
                                                   ? "gui.mca.trait_shaders.on" : "gui.mca.trait_shaders.off")
                            .withStyle(Config.getInstance().enablePlayerShaders ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                            : Component.translatable(isPresetsPage ? "gui.mca.export_skin" : "gui.mca.quick_export"),
                    b -> {
                        if (page.equals("traits")) {
                            Config.getInstance().enablePlayerShaders = !Config.getInstance().enablePlayerShaders;
                            b.setMessage(Component.translatable("gui.mca.trait_shaders",
                                    Component.translatable(Config.getInstance().enablePlayerShaders
                                                    ? "gui.mca.trait_shaders.on" : "gui.mca.trait_shaders.off")
                                            .withStyle(Config.getInstance().enablePlayerShaders ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
                        } else {
                            exportSkinFromCurrentPage();
                        }
                    }
            ));
        }

        addPreviewRotationWidgets();

        int y = height / 2 - 80;
        int margin = 40;
        Genetics genetics = villager.getGenetics();
        EditBox textFieldWidget;

        switch (page) {
            case "general" -> {
                //name
                drawName(width / 2, y);
                y += 20;

                //gender
                drawGender(width / 2, y);
                y += 22;

                if (villagerUUID.equals(playerUUID)) {
                    addModelSelectionWidgets(width / 2, y);
                    y += 22;
                }

                y = doubleGeneSliders(y, Genetics.VOICE_TONE, Genetics.VOICE);

                //age
                if (!villagerUUID.equals(playerUUID)) {
                    addRenderableWidget(new GeneSliderWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.villager_editor.age"), 1.0 + villagerBreedingAge / (double) AgeState.getMaxAge(), b -> {
                        villagerBreedingAge = -(int) ((1.0 - b) * AgeState.getMaxAge()) + 1;
                        villager.setAge(villagerBreedingAge);
                        villager.refreshDimensions();
                    }));
                    y += 28;
                }

                if (!(this instanceof DestinyScreen)) {
                    //relations
                    for (String who : new String[]{"Father", "Mother", "Spouse"}) {
                        Component relationLabel = Component.translatable("gui.villager_editor.relation." + who.toLowerCase(Locale.ROOT));
                        int relationLabelWidth = DATA_WIDTH / 2 - 2;
                        int relationTextWidth = font.width(relationLabel);
                        addRenderableWidget(new StringWidget(width / 2 + relationLabelWidth - relationTextWidth - 4, y, relationTextWidth, 18, relationLabel, font));
                        textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 18, relationLabel));
                        textFieldWidget.setMaxLength(64);
                        textFieldWidget.setValue(villagerData.getStringOr("FamilyTree" + who + "Name", ""));
                        textFieldWidget.setResponder(name -> villagerData.putString("FamilyTreeNew" + who + "Name", name));
                        y += 20;
                    }

                    //UUID
                    y += 4;
                    textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2, y, DATA_WIDTH, 18, Component.literal("UUID")));
                    textFieldWidget.setMaxLength(64);
                    textFieldWidget.setValue(villagerUUID.toString());
                }
            }
            case "body" -> {
                addCharacterSubpageTabs(y, "body");
                y += 24;

                y = addSkinSelectionWidgets(y);
                y += 4;

                //genes
                if (!Config.getServerConfig().allowPlayerSizeAdjustment && villagerUUID.equals(playerUUID)) {
                    genetics.setGene(Genetics.SIZE, 0.80f);
                    genetics.setGene(Genetics.WIDTH, 0.80f);
                } else {
                    addSteveProportionsButton(width / 2, y);
                    int buttonWidth = (DATA_WIDTH - STEVE_PROPORTIONS_BUTTON_WIDTH) / 2 - 2;
                    addGeneSlider(width / 2 + STEVE_PROPORTIONS_BUTTON_WIDTH + 2, y, buttonWidth, Genetics.SIZE);
                    addGeneSlider(width / 2 + STEVE_PROPORTIONS_BUTTON_WIDTH + 4 + buttonWidth, y, buttonWidth, Genetics.WIDTH);
                    y += 24;
                }

                addGeneSlider(width / 2, y, DATA_WIDTH / 2, Genetics.BREAST);
                addRenderableWidget(new TooltipButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20,
                        Component.translatable("gui.villager_editor.skin_color_selection", Component.translatable(hsvColoredSkin ? "gui.villager_editor.color_mode_rgb" : "gui.villager_editor.color_mode_natural")),
                        Component.translatable("gui.villager_editor.skin_color_mode.tooltip"),
                        b -> {
                            hsvColoredSkin = !hsvColoredSkin;
                            if (hsvColoredSkin) {
                                int skinDye = villager.getSkinDye();
                                if (skinDye == 0xFF000000) {
                                    int naturalSkinColor = ColorPalette.SKIN.getColor(
                                            genetics.getGene(Genetics.MELANIN),
                                            genetics.getGene(Genetics.HEMOGLOBIN),
                                            villager.getInfectionProgress()
                                    );
                                    color.setRGB(
                                            ARGB.red(naturalSkinColor) / 255.0,
                                            ARGB.green(naturalSkinColor) / 255.0,
                                            ARGB.blue(naturalSkinColor) / 255.0
                                    );
                                } else {
                                    color.setRGB(
                                            ARGB.red(skinDye) / 255.0,
                                            ARGB.green(skinDye) / 255.0,
                                            ARGB.blue(skinDye) / 255.0
                                    );
                                }
                                refreshSkinColor();
                            } else {
                                villager.clearSkinDye();
                            }
                            init();
                        }));
                y += 24;

                //skin color
                if (hsvColoredSkin) {
                    loadDyeIntoColorSelector(villager.getSkinDye());
                    addRgbColorSliders(y + 8, this::refreshSkinColor);
                } else {
                    int pickerSize = fitColorPickerSize(y + 8, DATA_WIDTH - 20);
                    int pickerX = width / 2 + (DATA_WIDTH - pickerSize) / 2;
                    addRenderableWidget(new ColorPickerWidget(pickerX, y + 8, pickerSize, pickerSize,
                            genetics.getGene(Genetics.HEMOGLOBIN),
                            genetics.getGene(Genetics.MELANIN),
                            MCA.locate("textures/colormap/villager_skin.png"),
                            (vx, vy) -> {
                                villager.clearSkinDye();
                                genetics.setGene(Genetics.HEMOGLOBIN, vx.floatValue());
                                genetics.setGene(Genetics.MELANIN, vy.floatValue());
                            }));
                }
            }
            case "clothing_style" -> {
                addCharacterSubpageTabs(y, "clothing_style");
                y += 24;

                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.randClothing"), b -> {
                    sendCommand("clothing");
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.selectClothing"), b -> {
                    setPage("clothing");
                }));
                y += 22;

                addCycleCommandRow(y, "clothing", getClothingText());
            }
            case "hair_style", "head" -> {
                addCharacterSubpageTabs(y, "hair_style");
                y += 24;

                addRenderableWidget(new TooltipButtonWidget(width / 2, y, DATA_WIDTH, 20,
                        Component.translatable(hsvColoredHair ? "gui.villager_editor.hair_hsv" : "gui.villager_editor.hair_genetic"),
                        Component.translatable("gui.villager_editor.hair_mode.tooltip"),
                        b -> {
                            hsvColoredHair = !hsvColoredHair;
                            if (!hsvColoredHair) {
                                villager.clearHairDye();
                            }
                            init();
                        }));
                y += 22;

                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.randHair"), b -> {
                    sendCommand("hair");
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.selectHair"), b -> {
                    setPage("hair");
                }));
                y += 22;

                addCycleCommandRow(y, "hair", getHairStyleText());
                y += 22;

                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.villager_editor.advancedHair"), b -> {
                    setPage("hair_advanced");
                }));
                y += 22;

                if (hsvColoredHair) {
                    addRgbColorSliders(y, this::refreshHairColor);
                } else {
                    y += 8;
                    int pickerSize = fitColorPickerSize(y, DATA_WIDTH - 20);
                    int pickerX = width / 2 + (DATA_WIDTH - pickerSize) / 2;
                    addRenderableWidget(new ColorPickerWidget(pickerX, y, pickerSize, pickerSize,
                            genetics.getGene(Genetics.PHEOMELANIN),
                            genetics.getGene(Genetics.EUMELANIN),
                            MCA.locate("textures/colormap/villager_hair.png"),
                            (vx, vy) -> {
                                villager.clearHairDye();
                                genetics.setGene(Genetics.PHEOMELANIN, vx.floatValue());
                                genetics.setGene(Genetics.EUMELANIN, vy.floatValue());
                            }));
                }
            }
            case "eyes" -> {
                addCharacterSubpageTabs(y, "eyes");
                y += 24;

                boolean hasHetero = villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
                int maxTarget = hasHetero ? 2 : 1;
                if (eyeColorTarget <= 0 || eyeColorTarget > maxTarget) {
                    eyeColorTarget = 1;
                }

                y = geneChanger(y, Genetics.FACE, getFaceCount());

                if (hasHetero) {
                    addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20,
                            Component.translatable(eyeColorTarget == 1 ? "gui.villager_editor.customize_eyes_left" : "gui.villager_editor.customize_eyes_right"),
                            b -> {
                                eyeColorTarget = eyeColorTarget == 1 ? 2 : 1;
                                loadDyeIntoColorSelector(eyeColorTarget == 1 ? villager.getEyeDye() : villager.getEyeLeftDye());
                                init();
                            }));
                } else {
                    addRenderableWidget(new TooltipButtonWidget(width / 2, y, DATA_WIDTH / 2, 20,
                            Component.translatable("gui.villager_editor.customize_eyes"),
                            Component.translatable("gui.villager_editor.heterochromia_required.tooltip"),
                            b -> {
                            })).active = false;
                }

                boolean activeHsv = eyeColorTarget == 1 ? hsvColoredEyes : hsvColoredEyesLeft;
                addRenderableWidget(new TooltipButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20,
                        Component.translatable(activeHsv ? "gui.villager_editor.eye_hsv" : "gui.villager_editor.eye_genetic"),
                        Component.translatable("gui.villager_editor.eye_mode.tooltip"),
                        b -> {
                            if (eyeColorTarget == 1) {
                                hsvColoredEyes = !hsvColoredEyes;
                                if (hsvColoredEyes) {
                                    color.setHSV(0.0, 0.0, 1.0);
                                    refreshEyeColor();
                                } else {
                                    villager.clearEyeDye();
                                }
                            } else {
                                hsvColoredEyesLeft = !hsvColoredEyesLeft;
                                if (hsvColoredEyesLeft) {
                                    color.setHSV(0.0, 0.0, 1.0);
                                    refreshEyeLeftColor();
                                } else {
                                    villager.clearEyeLeftDye();
                                }
                            }
                            init();
                        }));
                y += 22;

                if (activeHsv) {
                    addRgbColorSliders(y, () -> {
                        if (eyeColorTarget == 1) {
                            refreshEyeColor();
                        } else {
                            refreshEyeLeftColor();
                        }
                    });
                }
            }
            case "personality" -> {
                //personality
                List<ButtonWidget> personalityButtons = new LinkedList<>();
                int row = 0;
                final int BUTTONS_PER_ROW = 2;
                for (Personality p : Personality.values()) {
                    if (p != Personality.UNASSIGNED) {
                        if (row == BUTTONS_PER_ROW) {
                            row = 0;
                            y += 19;
                        }
                        ButtonWidget widget = addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / BUTTONS_PER_ROW * row, y, DATA_WIDTH / BUTTONS_PER_ROW, 20, p.getName(), b -> {
                            villager.getVillagerBrain().setPersonality(p);
                            personalityButtons.forEach(v -> v.active = true);
                            b.active = false;
                        }));
                        widget.active = p != villager.getVillagerBrain().getPersonality();
                        personalityButtons.add(widget);
                        row++;
                    }
                }
            }
            case "traits" -> {
                //traits
                addRenderableWidget(new ButtonWidget(width / 2, y, 32, 20, Component.literal("<"), b -> setTraitPage(traitPage - 1)));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - 32, y, 32, 20, Component.literal(">"), b -> setTraitPage(traitPage + 1)));
                addRenderableWidget(new ButtonWidget(width / 2 + 32, y, DATA_WIDTH - 32 * 2, 20, Component.translatable("gui.villager_editor.page", traitPage + 1), b -> traitPage++));
                y += 22;
                Traits.Trait[] traits = getValidTraits();
                for (int i = 0; i < TRAITS_PER_PAGE; i++) {
                    int index = i + traitPage * TRAITS_PER_PAGE;
                    if (index < traits.length) {
                        Traits.Trait t = traits[index];
                        MutableComponent name = t.getName().copy().withStyle(villager.getTraits().hasTrait(t) ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                        addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20, name, b -> {
                            if (villager.getTraits().hasTrait(t)) {
                                villager.getTraits().removeTrait(t);
                            } else {
                                villager.getTraits().addTrait(t);
                            }
                            b.setMessage(t.getName().copy().withStyle(villager.getTraits().hasTrait(t) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                        }));
                        y += 20;
                    } else {
                        break;
                    }
                }
            }
            case "debug" -> {
                //profession
                boolean right = false;
                List<ButtonWidget> professionButtons = new LinkedList<>();
                for (VillagerProfession p : new VillagerProfession[]{
                        BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.NONE),
                        ProfessionsMCA.GUARD,
                        ProfessionsMCA.ARCHER,
                        ProfessionsMCA.OUTLAW,
                        ProfessionsMCA.ADVENTURER,
                        ProfessionsMCA.CULTIST,
                }) {
                    ButtonWidget widget = addRenderableWidget(new ButtonWidget(width / 2 + (right ? DATA_WIDTH / 2 : 0), y, DATA_WIDTH / 2, 20, p.name(), b -> {
                        CompoundTag compound = new CompoundTag();
                        compound.putString("profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString());
                        syncVillagerData();
                        Network.sendToServer(new VillagerEditorSyncRequest("profession", villagerUUID, compound));
                        requestVillagerData();
                        professionButtons.forEach(button -> button.active = true);
                        b.active = false;
                    }));
                    professionButtons.add(widget);
                    widget.active = villager.getProfession() != p;
                    if (right) {
                        y += 20;
                    }
                    right = !right;
                }
                y += 4;

                //infection
                addRenderableWidget(new GeneSliderWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.villager_editor.infection"), villager.getInfectionProgress(), b -> {
                    villager.setInfected(b > 0);
                    villager.setInfectionProgress(b.floatValue());
                }));
                y += 22;

                //hearts
                assert minecraft.player != null;
                Memories player = villager.getVillagerBrain().getMemoriesForPlayer(minecraft.player);
                y = integerChanger(y, player::modHearts, () -> Component.translatable("gui.blueprint.reputation", player.getHearts()));

                //mood
                integerChanger(y, v -> villager.getVillagerBrain().modifyMoodValue(v), () -> Component.translatable("gui.interact.label.mood", villager.getVillagerBrain().getMoodValue()));
            }
            case "hair_advanced" -> {
                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.villager_editor.randLayeredHair"), b -> {
                    sendCommand("layered_hair");
                }));
                y += 24;

                for (LayeredHair.Category category : LayeredHair.Category.values()) {
                    addLayeredHairCyclerRow(y, category);
                    y += 22;
                }

                y += 4;
                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.button.back"), b -> {
                    setPage(this instanceof CombScreen ? "hair" : "hair_style");
                }));
            }
            case "clothing", "hair", "skin", "hair_base", "hair_bangs", "hair_back", "hair_front", "hair_extra" -> {
                filterGender = villager.getGenetics().getGender();
                searchString = "";

                //search
                textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 - DATA_WIDTH / 2, height / 2 - 100, DATA_WIDTH, 18,
                        Component.translatable("gui.villager_editor.search")));
                textFieldWidget.setMaxLength(64);
                textFieldWidget.setResponder(v -> {
                    searchString = v;
                    filter();
                });
                if (page.equals("hair") && this instanceof CombScreen) {
                    addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2 + 5, height / 2 - 100, 80, 20, Component.translatable("gui.villager_editor.advancedHair"), b -> {
                        setPage("hair_advanced");
                    }));
                }
                y = height / 2 + 78;
                pageButtonWidget = addRenderableWidget(new ButtonWidget(width / 2 - 30, y, 60, 20, Component.literal(""), b -> {
                }));
                addRenderableWidget(new ButtonWidget(width / 2 - 32 - 28, y, 28, 20, Component.literal("<<"), b -> {
                    clothingPage = Math.max(0, clothingPage - 1);
                    updateClothingPageWidget();
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 32, y, 28, 20, Component.literal(">>"), b -> {
                    clothingPage = Math.min(clothingPageCount - 1, clothingPage + 1);
                    updateClothingPageWidget();
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 32 + 32, y, 64, 20, Component.translatable("gui.button.done"), b -> {
                    if (page.equals("clothing") || page.equals("skin")) {
                        setPage(page.equals("skin") ? "body" : "clothing_style");
                    } else if (isLayeredHairPage()) {
                        setPage("hair_advanced");
                    } else {
                        setPage("hair_style");
                    }
                }));
                if (page.equals("clothing") || page.equals("hair")) {
                    addRenderableWidget(new ButtonWidget(width / 2 + 128, y, 64, 20, Component.translatable("gui.button.library"), b -> {
                        Minecraft.getInstance().gui.setScreen(new SkinLibraryScreen(this, villagerVisualization));
                    }));
                }
                widgetMasculine = addRenderableWidget(new ButtonWidget(width / 2 - 32 - 96 - 64, y, 64, 20, Component.translatable("gui.villager_editor.masculine"), b -> {
                    filterGender = Gender.MALE;
                    filter();
                    widgetMasculine.active = false;
                    widgetFeminine.active = true;
                }));
                widgetMasculine.active = filterGender != Gender.MALE;
                widgetFeminine = addRenderableWidget(new ButtonWidget(width / 2 - 32 - 96 - 64 + 64, y, 64, 20, Component.translatable("gui.villager_editor.feminine"), b -> {
                    filterGender = Gender.FEMALE;
                    filter();
                    widgetMasculine.active = true;
                    widgetFeminine.active = false;
                }));
                widgetFeminine.active = filterGender != Gender.FEMALE;
                filter();
            }
            case "presets" -> {
                int startIdx = currentPage * PRESETS_PER_PAGE;
                int count = Math.min(PRESETS_PER_PAGE, presetNames.size() - startIdx);
                int startY = height / 2 - 100;
                int yVal = startY + 20;

                for (int i = 0; i < count; i++) {
                    String name = presetNames.get(startIdx + i);
                    Component btnText = Component.literal(name).withStyle(name.equals(selectedPreset) ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                    addRenderableWidget(new ButtonWidget(width / 2, yVal, DATA_WIDTH, 20, btnText, b -> selectPreset(name.equals(this.selectedPreset) ? null : name)));
                    yVal += 20;
                }

                yVal += 2;
                // Align the shared header buttons with the pagination row when the list is empty
                if (presetNames.isEmpty()) {
                    presetsButton.setY(yVal);
                    exportSkinButton.setY(yVal);
                }
                addRenderableWidget(new ButtonWidget(width / 2, yVal, 28, 20, Component.literal("<<"), b -> {
                    currentPage = Math.max(0, currentPage - 1);
                    setPage("presets");
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - 28, yVal, 28, 20, Component.literal(">>"), b -> {
                    currentPage = Math.min(maxPage, currentPage + 1);
                    setPage("presets");
                }));
                yVal += 25;

                nameField = addRenderableWidget(new EditBox(this.font, width / 2, yVal + 1, DATA_WIDTH - 82, 18, Component.translatable("gui.mca.presets.name_field")));
                nameField.setMaxLength(32);
                if (selectedPreset != null) {
                    nameField.setValue(selectedPreset);
                }

                ButtonWidget presetActionButton = addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - 80, yVal, 80, 20,
                        Component.empty(), b -> performPresetAction()));
                updatePresetActionButton(presetActionButton);

                nameField.setResponder(val -> updatePresetActionButton(presetActionButton));
                yVal += 23;

                useButton = addRenderableWidget(new ButtonWidget(width / 2, yVal, 87, 20, Component.translatable("gui.mca.presets.use"), b -> usePreset()));
                useButton.active = selectedPreset != null;

                deleteButton = addRenderableWidget(new ButtonWidget(width / 2 + 88, yVal, 87, 20, Component.translatable("gui.mca.presets.delete"), b -> deletePreset()));
                deleteButton.active = selectedPreset != null;
                yVal += 24;

                // Back Button
                addRenderableWidget(new ButtonWidget(width / 2, yVal, DATA_WIDTH, 20, Component.translatable("gui.back"), b -> setPage(presetsReturnPage)));
            }
        }
    }

    private void addPreviewRotationWidgets() {
        boolean selectionPage = isSelectionPage();
        int centerX = selectionPage ? width / 2 : width / 2 - DATA_WIDTH / 2;
        if (selectionPage) {
            addPreviewControlRow(centerX, height / 2 - 76, 22, 14, 0);
        } else {
            addPreviewControlRow(centerX, height / 2 + 75, 28, 20, 2);
        }
    }

    private void finishSkinExport(boolean fromPresets) {
        if (shouldCloseAfterSkinExport()) {
            onClose();
        } else if (fromPresets) {
            setPage(presetsReturnPage);
        }
    }

    private void exportSkinFromCurrentPage() {
        boolean fromPresets = page.equals("presets");
        VillagerEntityMCA exportTarget = fromPresets && selectedPreset != null ? villagerVisualization : villager;
        String exportName = fromPresets ? selectedPreset : null;
        if (SkinExporter.export(exportTarget, exportName)) {
            finishSkinExport(fromPresets);
        }
    }

    protected boolean shouldCloseAfterSkinExport() {
        return true;
    }

    private void addPreviewControlRow(int centerX, int y, int buttonWidth, int buttonHeight, int gap) {
        int step = buttonWidth + gap;
        int rowWidth = 5 * buttonWidth + 4 * gap;
        int x = centerX - rowWidth / 2;

        addRenderableWidget(new ButtonWidget(x, y, buttonWidth, buttonHeight, Component.literal("-"), b -> zoomPreview(-0.1F)));
        addRenderableWidget(new ButtonWidget(x + step, y, buttonWidth, buttonHeight, Component.literal("<"), b -> rotatePreview(22.5F)));
        addRenderableWidget(new ToggleableTextureButtonWidget(x + step * 2, y, buttonWidth, buttonHeight,
                PREVIEW_MOUSE_FOLLOW_TEXTURE,
                previewFollowsMouse,
                Component.translatable("gui.villager_editor.preview_mouse_follow.tooltip"),
                b -> {
                    previewFollowsMouse = !previewFollowsMouse;
                    setPage(page);
                }));
        addRenderableWidget(new ButtonWidget(x + step * 3, y, buttonWidth, buttonHeight, Component.literal(">"), b -> rotatePreview(-22.5F)));
        addRenderableWidget(new ButtonWidget(x + step * 4, y, buttonWidth, buttonHeight, Component.literal("+"), b -> zoomPreview(0.1F)));
        addPreviewControlRowExtraButtons(x + rowWidth + gap, y, buttonWidth, buttonHeight, gap);
    }

    private void addPreviewControlRowExtraButtons(int x, int y, int buttonWidth, int buttonHeight, int gap) {
        if (!page.equals("clothing")) {
            return;
        }

        addRenderableWidget(new ClothingLockButtonWidget(
                x + 8,
                y + (buttonHeight - ClothingLockButtonWidget.SIZE) / 2,
                villager.isClothingLocked(),
                Component.translatable("gui.villager_editor.clothing_lock.tooltip"),
                b -> {
                    villager.setClothingLocked(!villager.isClothingLocked());
                    syncVillagerData();
                    setPage(page);
                }
        ));
    }

    private void refreshHairColor() {
        if (villager.getHairDye() == 0) {
            color.setHSV(0.0, 0.5, 0.5);
        }
        villager.setHairDye(getSelectedDye());
    }

    private void refreshSkinColor() {
        if (villager.getSkinDye() == 0xFF000000) {
            color.setHSV(0.0, 0.5, 0.5);
        }
        villager.setSkinDye(getSelectedDye());
    }

    private void refreshEyeColor() {
        if (villager.getEyeDye() == 0) {
            color.setHSV(0.0, 0.0, 1.0);
        }
        villager.setEyeDye(getSelectedDye());
    }

    private int addRgbColorSliders(int y, Runnable onChange) {
        color.hueWidget = addRenderableWidget(new HorizontalColorPickerWidget(width / 2 + 20, y, DATA_WIDTH - 40, 15,
                color.hue / 360.0,
                MCA.locate("textures/colormap/hue.png"),
                (vx, vy) -> {
                    color.setHSV(vx * 360, color.saturation, color.brightness);
                    onChange.run();
                }));

        color.saturationWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 20, y + 20, DATA_WIDTH - 40, 15,
                color.saturation,
                () -> {
                    double[] rgb = ClientUtils.HSV2RGB(color.hue, 0.0, 1.0);
                    return new float[]{(float) rgb[0], (float) rgb[1], (float) rgb[2], 1.0f};
                },
                () -> {
                    double[] rgb = ClientUtils.HSV2RGB(color.hue, 1.0, 1.0);
                    return new float[]{(float) rgb[0], (float) rgb[1], (float) rgb[2], 1.0f};
                },
                (vx, vy) -> {
                    color.setHSV(color.hue, vx, color.brightness);
                    onChange.run();
                }));

        color.brightnessWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 20, y + 40, DATA_WIDTH - 40, 15,
                color.brightness,
                () -> {
                    double[] rgb = ClientUtils.HSV2RGB(color.hue, color.saturation, 0.0);
                    return new float[]{(float) rgb[0], (float) rgb[1], (float) rgb[2], 1.0f};
                },
                () -> {
                    double[] rgb = ClientUtils.HSV2RGB(color.hue, color.saturation, 1.0);
                    return new float[]{(float) rgb[0], (float) rgb[1], (float) rgb[2], 1.0f};
                },
                (vx, vy) -> {
                    color.setHSV(color.hue, color.saturation, vx);
                    onChange.run();
                }));

        return y + 65;
    }

    private void loadDyeIntoColorSelector(int dye) {
        color.setRGB(
                ARGB.red(dye) / 255.0,
                ARGB.green(dye) / 255.0,
                ARGB.blue(dye) / 255.0
        );
    }

    private int getSelectedDye() {
        return ARGB.colorFromFloat(
                1.0f,
                nonZeroColorChannel(color.red),
                nonZeroColorChannel(color.green),
                nonZeroColorChannel(color.blue)
        );
    }

    private static float nonZeroColorChannel(double value) {
        return Math.max(1.0f / 255.0f, (float) value);
    }

    private void refreshEyeLeftColor() {
        if (villager.getEyeLeftDye() == 0) {
            color.setHSV(0.0, 0.0, 1.0);
        }
        villager.setEyeLeftDye(getSelectedDye());
    }

    private int geneChanger(int y, Genetics.GeneType gene, int maxCount) {
        int bw = 22;
        Genetics genetics = villager.getGenetics();
        float val = genetics.getGene(gene);
        int currentIndex = (int) Math.clamp(val * maxCount, 0, maxCount - 1);

        addRenderableWidget(new ButtonWidget(width / 2, y, bw, 20, Component.literal("<"), b -> {
            int prevIndex = (currentIndex - 1 + maxCount) % maxCount;
            genetics.setGene(gene, (prevIndex + 0.5f) / (float) maxCount);
            init();
        }));

        addRenderableWidget(new ButtonWidget(width / 2 + bw, y, DATA_WIDTH - bw * 2, 20,
                Component.literal(Component.translatable(gene.getTranslationKey()).getString() + ": " + (currentIndex + 1)),
                b -> {
                    int nextIndex = (currentIndex + 1) % maxCount;
                    genetics.setGene(gene, (nextIndex + 0.5f) / (float) maxCount);
                    init();
                }));

        addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - bw, y, bw, 20, Component.literal(">"), b -> {
            int nextIndex = (currentIndex + 1) % maxCount;
            genetics.setGene(gene, (nextIndex + 0.5f) / (float) maxCount);
            init();
        }));

        return y + 22;
    }

    private int getFaceCount() {
        FaceList faceList = FaceList.getInstance();
        if (faceList == null) {
            throw new IllegalStateException("Face textures are not loaded yet");
        }
        return faceList.count("normal");
    }

    private int fitColorPickerSize(int y, int preferredSize) {
        return Math.clamp(preferredSize, 48, height - y - 8);
    }

    private void addCharacterSubpageTabs(int y, String selectedPage) {
        int tabWidth = DATA_WIDTH / 4;
        addCharacterSubpageTab(width / 2, y, tabWidth, "body", selectedPage);
        addCharacterSubpageTab(width / 2 + tabWidth, y, tabWidth, "clothing_style", selectedPage);
        addCharacterSubpageTab(width / 2 + tabWidth * 2, y, tabWidth, "hair_style", selectedPage);
        addCharacterSubpageTab(width / 2 + tabWidth * 3, y, DATA_WIDTH - tabWidth * 3, "eyes", selectedPage);
    }

    private void addCharacterSubpageTab(int x, int y, int width, String page, String selectedPage) {
        ButtonWidget button = addRenderableWidget(new ButtonWidget(x, y, width, 20,
                Component.translatable("gui.villager_editor.subpage." + page),
                b -> setPage(page)));
        button.active = !page.equals(selectedPage);
    }

    private void addCycleCommandRow(int y, String command, Component label) {
        addRenderableWidget(new ButtonWidget(width / 2, y, 25, 20, Component.literal("<"), b -> {
            CompoundTag compound = new CompoundTag();
            compound.putInt("offset", -1);
            sendCommand(command, compound);
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + 25, y, DATA_WIDTH - 50, 20, label, b -> {
        })).active = false;
        addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - 25, y, 25, 20, Component.literal(">"), b -> {
            CompoundTag compound = new CompoundTag();
            compound.putInt("offset", 1);
            sendCommand(command, compound);
        }));
    }

    private void addLayeredHairCyclerRow(int y, LayeredHair.Category category) {
        int arrowWidth = 20;
        int selectWidth = 45;
        int labelWidth = DATA_WIDTH - arrowWidth * 2 - selectWidth;
        addRenderableWidget(new ButtonWidget(width / 2, y, arrowWidth, 20, Component.literal("<"), b -> cycleLayeredHair(category, -1)));
        addRenderableWidget(new ButtonWidget(width / 2 + arrowWidth, y, labelWidth, 20, getLayeredHairText(category), b -> {
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + arrowWidth + labelWidth, y, arrowWidth, 20, Component.literal(">"), b -> cycleLayeredHair(category, 1)));
        addRenderableWidget(new ButtonWidget(width / 2 + arrowWidth * 2 + labelWidth, y, selectWidth, 20, Component.translatable("gui.villager_editor.select"), b -> {
            setPage("hair_" + category.getId());
        }));
    }

    private int addSkinSelectionWidgets(int y) {
        ClientSkinCatalog.sync();
        addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.randSkin"), b -> {
            sendCommand("skin");
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.selectSkin"), b -> {
            setPage("skin");
        }));
        y += 22;

        if (ClientSkinCatalog.bodySkins().isEmpty()) {
            addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.loading"), b -> {
            })).active = false;
            return y + 24;
        }

        addRenderableWidget(new ButtonWidget(width / 2, y, 25, 20, Component.literal("<"), b -> {
            CompoundTag compound = new CompoundTag();
            compound.putInt("offset", -1);
            sendCommand("skin", compound);
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + 25, y, DATA_WIDTH - 50, 20, getSkinIndexText(), b -> {
        })).active = false;
        addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - 25, y, 25, 20, Component.literal(">"), b -> {
            CompoundTag compound = new CompoundTag();
            compound.putInt("offset", 1);
            sendCommand("skin", compound);
        }));
        return y + 24;
    }

    private Component getClothingText() {
        ClientSkinCatalog.sync();
        ClothingList clothingList = ClothingList.getInstance();
        Collection<Clothing> available = ClientSkinCatalog.clothing().values();
        List<Clothing> options = clothingList.getEditorOptions(villager.getGenetics().getGender(), available);
        List<String> clothes = options.stream().map(Clothing::getIdentifier).toList();
        return getSelectionIndexText("gui.villager_editor.clothing_index", clothes, villager.getClothes());
    }

    private Component getHairStyleText() {
        ClientSkinCatalog.sync();
        List<String> styles = getIdsForCurrentGender(ClientSkinCatalog.hairStyles().values());
        return getSelectionIndexText("gui.villager_editor.hair_index", styles, findCurrentHairStyle(styles).orElse(""));
    }

    private Optional<String> findCurrentHairStyle(List<String> styleIds) {
        String currentStyleId = villager.getHairStyleId();
        if (!MCA.isBlankString(currentStyleId) && styleIds.contains(currentStyleId)) {
            return Optional.of(currentStyleId);
        }
        for (String styleId : styleIds) {
            HairStyle style = ClientSkinCatalog.hairStyles().get(styleId);
            if (style != null && styleMatchesCurrentHair(style)) {
                return Optional.of(styleId);
            }
        }
        return Optional.empty();
    }

    private boolean styleMatchesCurrentHair(HairStyle style) {
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            if (!Objects.equals(style.layer(category), getCurrentLayeredHair(category))) {
                return false;
            }
        }
        return true;
    }

    private String getCurrentLayeredHair(LayeredHair.Category category) {
        return villager.getLayeredHair(category);
    }

    private Component getSelectionIndexText(String key, List<String> values, String selected) {
        int index = values.indexOf(selected);
        int displayTotal = values.size();
        int displayIndex = values.isEmpty() ? 0 : index < 0 ? 1 : index + 1;
        return Component.translatable(key, displayIndex, displayTotal);
    }

    private Component getSkinIndexText() {
        List<String> skins = getBodySkinIdsForCurrentGender();
        return getSelectionIndexText("gui.villager_editor.skin_index", skins, villager.getSkin());
    }

    private List<String> getBodySkinIdsForCurrentGender() {
        return getIdsForCurrentGender(ClientSkinCatalog.bodySkins().values());
    }

    private List<String> getIdsForCurrentGender(Collection<? extends SkinListEntry> entries) {
        return entries.stream()
                .filter(this::matchesCurrentGender)
                .map(SkinListEntry::getIdentifier)
                .distinct()
                .sorted(SkinListEntry::compareIdentifiers)
                .toList();
    }

    private boolean matchesCurrentGender(SkinListEntry entry) {
        Gender gender = villager.getGenetics().getGender();
        return entry.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || entry.getGender() == gender;
    }

    private List<String> getLayeredHairIdsForCurrentGender(LayeredHair.Category category) {
        Gender gender = villager.getGenetics().getGender();
        List<String> layers = ClientSkinCatalog.layeredHair().values().stream()
                .filter(hair -> hair.getCategory() == category)
                .filter(hair -> hair.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || hair.getGender() == gender)
                .map(SkinListEntry::getIdentifier)
                .distinct()
                .sorted(SkinListEntry::compareIdentifiers)
                .toList();
        if (category.isRequired()) {
            return layers;
        }

        List<String> optionalLayers = new ArrayList<>(layers.size() + 1);
        optionalLayers.add("");
        optionalLayers.addAll(layers);
        return optionalLayers;
    }

    private void cycleLayeredHair(LayeredHair.Category category, int offset) {
        List<String> layers = getLayeredHairIdsForCurrentGender(category);
        if (layers.isEmpty()) {
            return;
        }

        int index = layers.indexOf(villager.getLayeredHair(category));
        int next = index < 0 ? (offset < 0 ? layers.size() - 1 : 0) : Math.floorMod(index + offset, layers.size());
        villager.setHair("");
        villager.setLayeredHair(category, layers.get(next));
        eventCallback("hair_" + category.getId());
        setPage(page);
    }

    private Component getLayeredHairText(LayeredHair.Category category) {
        String selected = villager.getLayeredHair(category);
        Component layerName = getLayerDisplayName(category);
        if (MCA.isBlankString(selected)) {
            return Component.translatable("gui.villager_editor.hair_layer_value", layerName, Component.translatable("gui.villager_editor.none"));
        }
        List<String> displayLayers = getLayeredHairIdsForCurrentGender(category).stream()
                .filter(layer -> !MCA.isBlankString(layer))
                .toList();
        int index = displayLayers.indexOf(selected);
        int displayIndex = index < 0 ? 1 : index + 1;
        return Component.translatable("gui.villager_editor.hair_layer_index", layerName, displayIndex, Math.max(1, displayLayers.size()));
    }

    private Component getLayerDisplayName(LayeredHair.Category category) {
        return Component.translatable("gui.villager_editor.hair_layer_display." + category.getId());
    }

    private Traits.Trait[] getValidTraits() {
        return (Traits.Trait.values().stream()).filter(e -> {
            if (villagerUUID.equals(playerUUID)) {
                return (Config.getInstance().bypassTraitRestrictions || e.isUsableOnPlayer()) && e.isEnabled();
            }
            return e.isEnabled();
        }).toList().toArray(Traits.Trait[]::new);
    }

    private void updateClothingPageWidget() {
        if (pageButtonWidget != null) {
            pageButtonWidget.setMessage(Component.literal(String.format("%d / %d", clothingPage + 1, clothingPageCount)));
        }
    }

    private void filter() {
        if (Objects.equals(page, "clothing")) {
            filteredClothing = filter(ClientSkinCatalog.clothing());
        } else if (Objects.equals(page, "hair")) {
            filteredHairStyles = filter(ClientSkinCatalog.hairStyles());
        } else if (Objects.equals(page, "skin")) {
            filteredBodySkins = filter(ClientSkinCatalog.bodySkins());
        } else if (isLayeredHairPage()) {
            LayeredHair.Category category = getLayeredHairCategory();
            filteredLayeredHair = filter(ClientSkinCatalog.layeredHair().entrySet().stream()
                    .filter(entry -> entry.getValue().getCategory() == category)
                    .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll));
        }
    }

    private <T extends SkinListEntry> List<String> filter(Map<String, T> map) {
        List<String> filtered = map.entrySet().stream()
                .filter(v -> filterGender == v.getValue().getGender() || v.getValue().getGender() == Gender.NEUTRAL)
                .filter(v -> {
                    if (v.getValue() instanceof Clothing c) {
                        return !c.exclude;
                    } else {
                        return true;
                    }
                })
                .filter(v -> MCA.isBlankString(searchString) || v.getKey().contains(searchString))
                .map(v -> v.getValue().getIdentifier())
                .distinct()
                .sorted(SkinListEntry::compareIdentifiers)
                .toList();

        clothingPageCount = Math.max(1, (int) Math.ceil(filtered.size() / ((float) getSelectionItemsPerPage())));
        clothingPage = Math.clamp(clothingPage, 0, clothingPageCount - 1);

        updateClothingPageWidget();

        return filtered;
    }

    private int getSelectionItemsPerPage() {
        return isLayeredHairPage() ? LAYERED_HAIR_PER_PAGE : CLOTHES_PER_PAGE;
    }

    protected String[] getPages() {
        if (villagerUUID.equals(playerUUID)) {
            return new String[]{"general", "body", "traits"};
        } else {
            return new String[]{"general", "body", "personality", "traits", "debug"};
        }
    }

    private boolean isSelectionPage() {
        return page.equals("clothing") || page.equals("hair") || page.equals("skin") || isLayeredHairPage();
    }

    protected boolean isLayeredHairPage() {
        return page.startsWith("hair_") && LayeredHair.Category.byNameOrNull(page.substring("hair_".length())) != null;
    }

    private LayeredHair.Category getLayeredHairCategory() {
        return LayeredHair.Category.byName(page.substring("hair_".length()));
    }

    private List<String> getFilteredSelection() {
        return switch (page) {
            case "clothing" -> filteredClothing;
            case "hair" -> filteredHairStyles;
            case "skin" -> filteredBodySkins;
            default -> filteredLayeredHair;
        };
    }

    protected void drawName(int x, int y) {
        drawName(x, y, name -> {
            this.updateName(name);
            if (doneWidget != null) {
                doneWidget.active = !MCA.isBlankString(name);
            }
        });
    }

    protected void drawName(int x, int y, Consumer<String> onChanged) {
        villagerNameField = addRenderableWidget(new EditBox(this.font, x, y, DATA_WIDTH / 3 * 2, 18, Component.translatable("structure_block.structure_name")));
        villagerNameField.setMaxLength(32);
        villagerNameField.setValue(getName().getString());
        villagerNameField.setResponder(onChanged);
        addRenderableWidget(new ButtonWidget(x + DATA_WIDTH / 3 * 2 + 1, y - 1, DATA_WIDTH / 3 - 2, 20, Component.translatable("gui.button.random"), b ->
                Network.sendToServer(new VillagerNameRequest(villager.getGenetics().getGender()))
        ));
    }

    public Component getName() {
        Component villagerName = null;
        boolean isPlayer = villagerUUID.equals(playerUUID);
        if (isPlayer) {
            assert minecraft.player != null;
            villagerName = minecraft.player.getCustomName();
        } else if (villager.hasCustomName()) {
            villagerName = villager.getCustomName();
        }

        if (villagerName == null || MCA.isBlankString(villagerName.getString())) {
            // Failsafe-conditions for non-present custom names
            if (isPlayer) {
                assert minecraft.player != null;
                villagerName = minecraft.player.getName();
            } else {
                villagerName = villager.getName();
            }

            if (MCA.isBlankString(villagerName.getString())) {
                Network.sendToServer(new VillagerNameRequest(villager.getGenetics().getGender()));
            } else {
                updateName(villagerName.getString());
            }
        }

        return villagerName;
    }

    public void updateName(String name) {
        if (!MCA.isBlankString(name)) {
            Component newName = Component.nullToEmpty(name);
            boolean isPlayer = villagerUUID.equals(playerUUID);
            if (isPlayer) {
                assert minecraft.player != null;
                final Component realName = minecraft.player.getName();
                if (realName.getString().equals(name)) {
                    // Remove Custom name if it is the same as our actual name
                    newName = null;
                }
                minecraft.player.setCustomName(newName);
                minecraft.player.setCustomNameVisible(newName != null);
            }
            villager.setCustomName(newName);
        }
    }

    void drawGender(int x, int y) {
        genderButtonFemale = new ButtonWidget(x, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.feminine"), sender -> {
            updateGender(Gender.FEMALE);
            genderButtonFemale.active = false;
            genderButtonMale.active = true;
        });
        addRenderableWidget(genderButtonFemale);

        genderButtonMale = new ButtonWidget(x + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.masculine"), sender -> {
            updateGender(Gender.MALE);
            genderButtonFemale.active = true;
            genderButtonMale.active = false;
        });
        addRenderableWidget(genderButtonMale);

        genderButtonFemale.active = villager.getGenetics().getGender() != Gender.FEMALE;
        genderButtonMale.active = villager.getGenetics().getGender() != Gender.MALE;
    }

    private void updateGender(Gender gender) {
        villager.getGenetics().setGender(gender);
        sendCommand("gender");
    }

    void addModelSelectionWidgets(int x, int y) {
        if (allowPlayerModel && allowVillagerModel) {
            VillagerLike.PlayerModel selectedModel = getSelectedPlayerModel();
            villagerSkinWidget = addRenderableWidget(new TooltipButtonWidget(x, y, DATA_WIDTH / 3, 20, "gui.villager_editor.villager_skin", b -> {
                getOrCreateMcaData(villagerData).putInt("PlayerModel", VillagerLike.PlayerModel.VILLAGER.ordinal());
                syncVillagerData();
                playerSkinWidget.active = true;
                villagerSkinWidget.active = false;
                vanillaSkinWidget.active = true;
            }));
            villagerSkinWidget.active = selectedModel != VillagerLike.PlayerModel.VILLAGER;

            playerSkinWidget = addRenderableWidget(new TooltipButtonWidget(x + DATA_WIDTH / 3, y, DATA_WIDTH / 3, 20, "gui.villager_editor.player_skin", b -> {
                getOrCreateMcaData(villagerData).putInt("PlayerModel", VillagerLike.PlayerModel.PLAYER.ordinal());
                syncVillagerData();
                playerSkinWidget.active = false;
                villagerSkinWidget.active = true;
                vanillaSkinWidget.active = true;
            }));
            playerSkinWidget.active = selectedModel != VillagerLike.PlayerModel.PLAYER;

            vanillaSkinWidget = addRenderableWidget(new TooltipButtonWidget(x + DATA_WIDTH / 3 * 2, y, DATA_WIDTH / 3, 20, "gui.villager_editor.vanilla_skin", b -> {
                getOrCreateMcaData(villagerData).putInt("PlayerModel", VillagerLike.PlayerModel.VANILLA.ordinal());
                syncVillagerData();
                villagerSkinWidget.active = true;
                playerSkinWidget.active = true;
                vanillaSkinWidget.active = false;
            }));
            vanillaSkinWidget.active = selectedModel != VillagerLike.PlayerModel.VANILLA;
        } else {
            addRenderableWidget(new TooltipButtonWidget(x, y, DATA_WIDTH, 20, "gui.villager_editor.model_blacklist_hint", b -> {
            })).active = false;
        }
    }

    private void sendCommand(String command) {
        sendCommand(command, new CompoundTag());
    }

    private void sendCommand(String command, CompoundTag nbt) {
        syncVillagerData();
        Network.sendToServer(new VillagerEditorSyncRequest(command, villagerUUID, nbt));
        requestVillagerData();
    }

    private void setTraitPage(int i) {
        Traits.Trait[] traits = getValidTraits();
        int maxPage = Math.max(0, (int) Math.ceil((double) traits.length / TRAITS_PER_PAGE) - 1);
        traitPage = Math.clamp(i, 0, maxPage);
        setPage("traits");
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (nameField != null) {
            double mx = event.x();
            double my = event.y();
            boolean overEditBox = mx >= nameField.getX() && mx < nameField.getX() + nameField.getWidth()
                                  && my >= nameField.getY() && my < nameField.getY() + nameField.getHeight();
            if (!overEditBox) {
                nameField.setFocused(false);
                if (getFocused() == nameField) {
                    setFocused(null);
                }
            }
        }

        if (page.equals("clothing") && (hoveredClothingId >= 0 && filteredClothing.size() > hoveredClothingId)) {
            villager.setClothes(filteredClothing.get(hoveredClothingId));
            markClothingSelected();
            setPage("clothing_style");
            eventCallback("clothing");
            return true;

        }

        if (page.equals("hair") && (hoveredClothingId >= 0 && filteredHairStyles.size() > hoveredClothingId)) {
            applyHairStyle(villager, filteredHairStyles.get(hoveredClothingId));
            setPage("hair_style");
            eventCallback("hair");
            return true;

        }

        if (page.equals("skin") && (hoveredClothingId >= 0 && filteredBodySkins.size() > hoveredClothingId)) {
            villager.setSkin(filteredBodySkins.get(hoveredClothingId));
            setPage("body");
            eventCallback("skin");
            return true;

        }

        if (isLayeredHairPage() && (hoveredClothingId >= 0 && filteredLayeredHair.size() > hoveredClothingId)) {
            String selectedLayerPage = page;
            villager.setHair("");
            villager.setLayeredHair(getLayeredHairCategory(), filteredLayeredHair.get(hoveredClothingId));
            setPage("hair_advanced");
            eventCallback(selectedLayerPage);
            return true;

        }

        if (event.button() == 0 && isMouseOverMainPreview(event.x(), event.y())) {
            draggingPreview = true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            draggingPreview = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (acceptsPreviewRotationInput()) {
            if (event.key() == GLFW.GLFW_KEY_A || event.key() == GLFW.GLFW_KEY_LEFT) {
                rotatePreviewLeft = true;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_D || event.key() == GLFW.GLFW_KEY_RIGHT) {
                rotatePreviewRight = true;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_R) {
                double mouseX = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getWidth();
                double mouseY = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getHeight();
                if (isMouseOverPreview(mouseX, mouseY)) {
                    previewZoom = 1.0F;
                    previewRotation = 0.0F;
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_A || event.key() == GLFW.GLFW_KEY_LEFT) {
            rotatePreviewLeft = false;
        } else if (event.key() == GLFW.GLFW_KEY_D || event.key() == GLFW.GLFW_KEY_RIGHT) {
            rotatePreviewRight = false;
        }
        return super.keyReleased(event);
    }

    @Override
    public void tick() {
        super.tick();
        villager.tickCount++;
        villagerVisualization.tickCount++;
    }

    private boolean acceptsPreviewRotationInput() {
        return !page.equals("loading") && !(getFocused() instanceof EditBox);
    }

    void rotatePreview(float degrees) {
        previewRotation = (previewRotation + degrees) % 360.0F;
    }

    void zoomPreview(float amount) {
        previewZoom = Mth.clamp(previewZoom + amount, MIN_PREVIEW_ZOOM, MAX_PREVIEW_ZOOM);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOverMainPreview(mouseX, mouseY)) {
            zoomPreview((float) scrollY * 0.1F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == 0 && draggingPreview) {
            rotatePreview((float) -deltaX);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    private boolean isMouseOverPreview(double mouseX, double mouseY) {
        if (isSelectionPage()) {
            return mouseY >= height / 2.0 - 90 && mouseY <= height / 2.0 + 75;
        }

        int x = width / 2 - DATA_WIDTH;
        int y = height / 2 - 8;
        return mouseX >= x && mouseX <= x + DATA_WIDTH && mouseY >= y - 57 && mouseY <= y + 88;
    }

    private boolean isMouseOverMainPreview(double mouseX, double mouseY) {
        if (isSelectionPage()) {
            return false;
        }
        return isMouseOverPreview(mouseX, mouseY);
    }

    protected void eventCallback(String event) {
        // nop
    }

    protected boolean shouldUsePlayerModel() {
        return getSelectedPlayerModel() != VillagerLike.PlayerModel.VILLAGER && page.equals("general");
    }

    protected boolean shouldPrintPlayerHint() {
        return true;
    }

    public boolean isEditingPlayer(UUID uuid) {
        return villagerUUID.equals(playerUUID) && playerUUID.equals(uuid);
    }

    public boolean hasVillagerData() {
        return villagerData != null;
    }

    public VillagerLike.PlayerModel getSelectedPlayerModel() {
        int id = villagerData == null
                ? VillagerLike.PlayerModel.VILLAGER.ordinal()
                : getMcaData(villagerData).getInt("PlayerModel").orElse(VillagerLike.PlayerModel.VILLAGER.ordinal());
        return VillagerLike.PlayerModel.byId(id);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        long currentTime = System.currentTimeMillis();
        if (lastFrameTime == -1L) {
            lastFrameTime = currentTime;
        }
        float frameTime = Math.clamp((currentTime - lastFrameTime) / 1000.0F, 0.0F, 0.1F);
        lastFrameTime = currentTime;
        if (rotatePreviewLeft) {
            rotatePreview(120.0F * frameTime);
        }
        if (rotatePreviewRight) {
            rotatePreview(-120.0F * frameTime);
        }

        if (shouldDrawEntity()) {
            int x = width / 2 - DATA_WIDTH;
            int y = height / 2 - 8;
            if (page.equals("presets") && hasVisualChange) {
                // Left: Original (centered under Presets button)
                extractEntityPreview(context, x + 8, y - 57, x + 88, y + 88, 40, 0, mouseX, mouseY, delta, villager);
                // Right: Preset (centered under Export Skin button)
                extractEntityPreview(context, x + 88, y - 57, x + 168, y + 88, 40, 0, mouseX, mouseY, delta, villagerVisualization);

                // Draw labels above the preview models
                final Matrix3x2fStack matrices = context.pose();
                matrices.pushMatrix();
                matrices.translate(x + 47.5F, y - 37);
                matrices.scale(0.75f, 0.75f);
                context.centeredText(font, Component.translatable("gui.mca.presets.original"), 0, 0, 0xAAFFFFFF);
                matrices.popMatrix();

                matrices.pushMatrix();
                matrices.translate(x + 127.5F, y - 37);
                matrices.scale(0.75f, 0.75f);
                context.centeredText(font, Component.translatable("gui.mca.presets.preview"), 0, 0, 0xAAFFFFFF);
                matrices.popMatrix();
            } else {
                if (villagerUUID.equals(playerUUID) && shouldUsePlayerModel()) {
                    assert Minecraft.getInstance().player != null;
                    extractEntityPreview(context, x, y - 57, x + DATA_WIDTH, y + 95, 55, 0, mouseX, mouseY, delta, Minecraft.getInstance().player);
                } else {
                    extractEntityPreview(context, x, y - 57, x + DATA_WIDTH, y + 95, 55, 0, mouseX, mouseY, delta, villager);
                }

                // hint for confused people
                if (shouldPrintPlayerHint() && villagerUUID.equals(playerUUID) && getSelectedPlayerModel() != VillagerLike.PlayerModel.VILLAGER) {
                    final Matrix3x2fStack matrices = context.pose();
                    matrices.pushMatrix();
                    matrices.translate(width / 2.0F, height / 2.0F - 117);
                    matrices.scale(0.5f, 0.5f);
                    context.centeredText(font, Component.translatable("gui.villager_editor.model_hint"), 0, 0, 0xAAFFFFFF);
                    matrices.popMatrix();
                }
            }
        }

        if (isSelectionPage()) {
            CompoundTag nbt = saveEntityData(villager);
            villagerVisualization.load(TagValueInput.create(ProblemReporter.DISCARDING, villagerVisualization.registryAccess(), nbt));
            villagerVisualization.setAge(villager.getAge());
            villagerVisualization.refreshDimensions();

            hoveredClothingId = -1;
            List<String> selection = getFilteredSelection();
            int itemsPerPage = getSelectionItemsPerPage();
            int totalOnPage = Math.min(itemsPerPage, selection.size() - clothingPage * itemsPerPage);
            int row0Count = Math.min(totalOnPage, isLayeredHairPage() ? LAYERED_HAIR_PER_PAGE : CLOTHES_H);
            int row1Count = Math.max(0, totalOnPage - row0Count);

            for (int i = 0; i < totalOnPage; i++) {
                int index = clothingPage * itemsPerPage + i;
                int y = i < row0Count ? 0 : 1;
                int x = y == 0 ? i : i - row0Count;
                int numInRow = y == 0 ? row0Count : row1Count;

                switch (page) {
                    case "clothing" -> villagerVisualization.setClothes(selection.get(index));
                    case "hair" -> applyHairStyle(villagerVisualization, selection.get(index));
                    case "skin" -> villagerVisualization.setSkin(selection.get(index));
                    default -> {
                        villagerVisualization.setHair("");
                        villagerVisualization.setLayeredHair(getLayeredHairCategory(), selection.get(index));
                    }
                }

                boolean layeredHairSelection = isLayeredHairPage();
                int spacing = layeredHairSelection ? 56 : 40;
                int cx = width / 2 + (int) ((x - numInRow / 2.0 + 0.5 - 0.5 * (y % 2)) * spacing);
                int cy = layeredHairSelection ? height / 2 + 8 : height / 2 + (int) ((y - CLOTHES_V / 2.0 + 0.5) * 65);

                int rx = layeredHairSelection ? 25 : 20;
                int ry0 = layeredHairSelection ? 60 : 25;
                int ry1 = layeredHairSelection ? 60 : 40;

                int x0 = cx - rx;
                int y0 = cy - ry0;
                int x1 = cx + rx;
                int y1 = cy + ry1;

                int hoverYMax = layeredHairSelection ? y1 : y1 - 5;
                if (mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= hoverYMax) {
                    hoveredClothingId = index;
                }

                boolean hovered = hoveredClothingId == index;
                int previewPadding = hovered ? 5 : 0;
                float rotationOffset = layeredHairSelection && getLayeredHairCategory() == LayeredHair.Category.BACK ? 180.0F : 0.0F;

                int size = layeredHairSelection ? (hovered ? 45 : 40) : (hovered ? 35 : 30);

                extractEntityPreview(context, x0 - previewPadding, y0 - previewPadding, x1 + previewPadding, y1 + previewPadding, size, 0, mouseX, mouseY, delta, villagerVisualization, rotationOffset);
            }
        }

        if (page.equals("presets")) {
            int startIdx = currentPage * PRESETS_PER_PAGE;
            int count = Math.min(PRESETS_PER_PAGE, presetNames.size() - startIdx);
            int startY = height / 2 - 100;

            int rightX = width / 2;
            int paginationY = startY + 22 + count * 20 + 6;
            context.centeredText(font, Component.literal((currentPage + 1) + " / " + (maxPage + 1)), rightX + DATA_WIDTH / 2, paginationY, 0xFFAAAAAA);
        }
    }

    protected boolean shouldDrawEntity() {
        return !page.equals("loading") && !isSelectionPage();
    }

    protected boolean shouldShowPageSelection() {
        return !isSelectionPage();
    }

    private boolean isMainPageSelected(String mainPage) {
        return mainPage.equals(page)
               || (mainPage.equals("body") && List.of("clothing_style", "hair_style", "head", "eyes", "hair_advanced").contains(page));
    }

    public void setVillagerName(String name) {
        villagerNameField.setValue(name);
        updateName(name);
    }

    public void setVillagerData(CompoundTag villagerData) {
        this.villagerData = villagerData;
        villager.load(TagValueInput.create(ProblemReporter.DISCARDING, villager.registryAccess(), villagerData));

        int hairDye = villager.getHairDye();
        int eyeDye = villager.getEyeDye();
        int eyeLeftDye = villager.getEyeLeftDye();
        int skinDye = villager.getSkinDye();

        if (page.equals("loading")) {
            hsvColoredSkin = skinDye != 0xFF000000;
            hsvColoredHair = hairDye != 0xFF000000;
            hsvColoredEyes = eyeDye != 0xFFFFFFFF;
            hsvColoredEyesLeft = eyeLeftDye != 0xFFFFFFFF;
            eyeColorTarget = 0;
        }

        int initialDye = switch (page) {
            case "body" -> skinDye;
            case "eyes" -> eyeColorTarget == 2 ? eyeLeftDye : eyeDye;
            default -> hairDye;
        };

        int currentPick = ARGB.colorFromFloat(1.0f, (float) color.red, (float) color.green, (float) color.blue);
        if (initialDye != currentPick) {
            loadDyeIntoColorSelector(initialDye);
        }

        villagerBreedingAge = villagerData.getIntOr("Age", 0);
        villager.setAge(villagerBreedingAge);
        if (minecraft.player != null) {
            villager.setPosRaw(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
            villagerVisualization.setPosRaw(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        }
        villager.refreshDimensions();
        if (page.equals("loading")) {
            setPage("general");
        } else {
            setPage(page);
        }
    }

    private void requestVillagerData() {
        Network.sendToServer(new GetVillagerRequest(villagerUUID));
    }

    public void syncVillagerData() {
        CompoundTag nbt = saveEntityData(villager);
        copyEditorFields(nbt,
                "FamilyTreeNewFatherName",
                "FamilyTreeNewMotherName",
                "FamilyTreeNewSpouseName",
                "VillagerDataFinalized"
        );
        copyEditorMcaFields(nbt, "PlayerModel");
        nbt.putInt("Age", villagerBreedingAge);
        Network.sendToServer(new VillagerEditorSyncRequest("sync", villagerUUID, nbt));
    }

    void markClothingSelected() {
        villager.setClothingLocked(true);
    }

    private void copyEditorFields(CompoundTag target, String... keys) {
        if (villagerData == null) {
            return;
        }
        for (String key : keys) {
            if (villagerData.contains(key)) {
                target.put(key, Objects.requireNonNull(villagerData.get(key)).copy());
            }
        }
    }

    private void copyEditorMcaFields(CompoundTag target, String... keys) {
        if (villagerData == null) {
            return;
        }
        CompoundTag source = getMcaData(villagerData);
        for (String key : keys) {
            if (source.contains(key)) {
                getOrCreateMcaData(target).put(key, Objects.requireNonNull(source.get(key)).copy());
            }
        }
    }

    private CompoundTag getMcaData(CompoundTag data) {
        return NbtHelper.getCompoundOrSelf(data, VillagerEntityMCA.MCA_DATA_KEY);
    }

    private CompoundTag getOrCreateMcaData(CompoundTag data) {
        return NbtHelper.getOrCreateCompound(data, VillagerEntityMCA.MCA_DATA_KEY);
    }

    private CompoundTag saveEntityData(VillagerEntityMCA entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.save(output);
        return output.buildResult();
    }

    private void applyHairStyle(VillagerLike<?> villager, String styleId) {
        HairStyle style = ClientSkinCatalog.hairStyles().get(styleId);
        if (style != null) {
            villager.setHairStyle(style);
        }
    }

    void extractEntityPreview(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int size, float offsetY, float mouseX, float mouseY, float delta, LivingEntity entity) {
        extractEntityPreview(context, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, delta, entity, 0.0F);
    }

    void extractEntityPreview(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int size, float offsetY, float mouseX, float mouseY, float delta, LivingEntity entity, float rotationOffset) {
        float centerX = (x0 + x1) / 2.0F;
        float centerY = (y0 + y1) / 2.0F;
        float xAngle = previewFollowsMouse ? (float) Math.atan((centerX - mouseX) / 40.0F) : 0.0F;
        float yAngle = previewFollowsMouse ? (float) Math.atan((centerY - mouseY) / 40.0F) : 0.0F;

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf xRotation = new Quaternionf().rotateX(yAngle * 20.0F * ((float) Math.PI / 180.0F));
        rotation.mul(xRotation);
        EntityRenderState renderState = createInventoryRenderState(entity, delta);
        if (renderState instanceof LivingEntityRenderState livingRenderState) {
            float displayRotation = previewRotation + rotationOffset;
            float cos = (float) Math.cos(Math.toRadians(displayRotation));
            livingRenderState.bodyRot = 180.0F + displayRotation + xAngle * 20.0F * cos;
            livingRenderState.yRot = xAngle * 20.0F * cos;
            livingRenderState.xRot = livingRenderState.pose == Pose.FALL_FLYING ? 0.0F : -yAngle * 20.0F;
            livingRenderState.boundingBoxWidth = livingRenderState.boundingBoxWidth / livingRenderState.scale;
            livingRenderState.boundingBoxHeight = livingRenderState.boundingBoxHeight / livingRenderState.scale;
            livingRenderState.scale = 1.0F;
        }

        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + offsetY, 0.0F);
        context.entity(renderState, Math.round(size * previewZoom), translation, rotation, xRotation, x0, y0, x1, y1);
    }

    private EntityRenderState createInventoryRenderState(LivingEntity entity, float delta) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
        EntityRenderState renderState = renderer.createRenderState(entity, delta);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        renderState.isInvisible = false;
        return renderState;
    }

    void applyLibraryHair(String hairId) {
        villager.setHairStyle(HairStyle.singleLayer(hairId, villager.getGenetics().getGender(), 1.0F));
        if (isLayeredHairPage()) {
            setPage("hair_advanced");
        }
    }

    @Override
    public void skinListUpdatedCallback() {
        filter();
        setPage(page);
    }

    private void refreshPresets() {
        if (!presetsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            presetsDir.mkdirs();
        }
        presetNames.clear();
        File[] files = presetsDir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                presetNames.add(name.substring(0, name.length() - 5)); // remove .json
            }
        }
        presetNames.sort(String::compareToIgnoreCase);
        maxPage = Math.max(0, (presetNames.size() - 1) / PRESETS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);
    }

    private void selectPreset(String name) {
        if (name != null && !isValidPresetName(name)) {
            MCA.LOGGER.warn("Ignoring invalid preset name: {}", name);
            return;
        }
        this.selectedPreset = name;
        if (name != null) {
            if (nameField != null) {
                nameField.setValue(name);
            }
            try {
                File presetFile = getPresetFile(name);
                if (presetFile.exists()) {
                    String json = FileUtils.readFileToString(presetFile, StandardCharsets.UTF_8);
                    CompoundTag tag = PresetCodec.fromJsonString(json);

                    // Load selected preset NBT into villagerVisualization for preview/comparison
                    if (villagerData != null) {
                        villagerVisualization.load(TagValueInput.create(ProblemReporter.DISCARDING, villagerVisualization.registryAccess(), villagerData));
                    }
                    villagerVisualization.readNbtForConversion(tag);
                    villagerVisualization.refreshDimensions();

                    // Restore main villager back to the original backup NBT to keep it unmodified until "Use" is clicked
                    if (presetsBackupNbt != null) {
                        if (villagerData != null) {
                            villager.load(TagValueInput.create(ProblemReporter.DISCARDING, villager.registryAccess(), villagerData));
                        }
                        villager.readNbtForConversion(presetsBackupNbt);
                        villager.refreshDimensions();
                    }

                    // Cache the visual change flag
                    if (presetsBackupNbt != null) {
                        hasVisualChange = !presetsBackupNbt.equals(tag);
                    } else {
                        hasVisualChange = false;
                    }
                }
            } catch (Exception e) {
                MCA.LOGGER.error("Failed to load preset for preview", e);
            }
        } else {
            hasVisualChange = false;
        }
        setPage("presets");
    }

    private void performPresetAction() {
        if (nameField == null) return;
        String name = nameField.getValue().trim();
        if (!isValidPresetName(name)) return;

        if (selectedPreset == null) {
            if (!presetNames.contains(name)) {
                savePreset();
            }
        } else if (selectedPreset.equals(name)) {
            confirmPresetUpdate();
        } else if (!presetNames.contains(name)) {
            renamePreset();
        }
    }

    private void updatePresetActionButton(ButtonWidget button) {
        String name = nameField == null ? "" : nameField.getValue().trim();
        boolean valid = isValidPresetName(name);
        boolean existingOtherPreset = presetNames.contains(name) && !Objects.equals(selectedPreset, name);

        button.active = valid && !existingOtherPreset;
        if (selectedPreset == null) {
            button.setMessage(Component.translatable("gui.mca.presets.save"));
        } else if (selectedPreset.equals(name)) {
            button.setMessage(Component.translatable("gui.mca.presets.update"));
        } else {
            button.setMessage(Component.translatable("gui.mca.presets.rename"));
        }
    }

    private void confirmPresetUpdate() {
        Minecraft client = Objects.requireNonNull(minecraft);
        client.gui.setScreen(new ConfirmScreen(confirmed -> {
            client.gui.setScreen(this);
            if (confirmed) {
                savePreset();
            }
        }, Component.translatable("gui.mca.presets.update_confirm.title"),
                Component.translatable("gui.mca.presets.update_confirm", selectedPreset),
                Component.translatable("gui.mca.presets.update"), CommonComponents.GUI_CANCEL));
    }

    private void savePreset() {
        if (nameField == null) return;
        String name = nameField.getValue().trim();
        if (!isValidPresetName(name)) return;

        try {
            CompoundTag tag = villager.toNbtForConversion();

            // Extract PlayerModel from villagerData NBT if present
            if (villagerData != null) {
                CompoundTag parentMca = getMcaData(villagerData);
                if (parentMca.contains("PlayerModel")) {
                    tag.putInt("PlayerModel", parentMca.getInt("PlayerModel").orElse(0));
                }
            }

            File file = getPresetFile(name);
            String json = PresetCodec.toJsonString(tag);
            FileUtils.writeStringToFile(file, json, StandardCharsets.UTF_8);

            // Update backup NBT so saving doesn't trigger visual difference anymore
            presetsBackupNbt = tag.copy();
            hasVisualChange = false;

            refreshPresets();
            selectPreset(name);
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to save preset", e);
        }
    }

    private void renamePreset() {
        if (selectedPreset == null || nameField == null) return;
        String newName = nameField.getValue().trim();
        if (!isValidPresetName(selectedPreset) || !isValidPresetName(newName) || newName.equals(selectedPreset)) return;

        try {
            File oldFile = getPresetFile(selectedPreset);
            File newFile = getPresetFile(newName);
            if (oldFile.exists() && !newFile.exists()) {
                if (oldFile.renameTo(newFile)) {
                    selectedPreset = newName;
                    refreshPresets();
                    selectPreset(newName);
                }
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to rename preset", e);
        }
    }

    private void deletePreset() {
        if (selectedPreset == null) return;
        try {
            File file = getPresetFile(selectedPreset);
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            selectedPreset = null;
            hasVisualChange = false;
            refreshPresets();
            setPage("presets");
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to delete preset", e);
        }
    }

    private void usePreset() {
        if (selectedPreset == null) return;
        try {
            File presetFile = getPresetFile(selectedPreset);
            if (presetFile.exists()) {
                String json = FileUtils.readFileToString(presetFile, StandardCharsets.UTF_8);
                CompoundTag tag = PresetCodec.fromJsonString(json);

                villager.readNbtForConversion(tag);
                villager.refreshDimensions();

                if (tag.contains("PlayerModel")) {
                    int modelVal = tag.getInt("PlayerModel").orElse(0);
                    if (villagerData != null) {
                        CompoundTag parentMca = getOrCreateMcaData(villagerData);
                        parentMca.putInt("PlayerModel", modelVal);
                    }
                }

                presetsBackupNbt = null; // Discard backup so it doesn't restore on exit
                hasVisualChange = false;
                syncVillagerData();
                setPage("general");
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to apply preset", e);
        }
    }

    private boolean isValidPresetName(String name) {
        return name != null
               && !name.isBlank()
               && name.indexOf('/') < 0
               && name.indexOf('\\') < 0
               && !name.equals(".")
               && !name.equals("..");
    }

    private File getPresetFile(String name) throws IOException {
        File directory = presetsDir.getCanonicalFile();
        File file = new File(directory, name + ".json").getCanonicalFile();
        if (!directory.equals(file.getParentFile())) {
            throw new IOException("Preset path escapes preset directory: " + name);
        }
        return file;
    }

    public VillagerEntityMCA getVillager() {
        return villager;
    }
}
