package net.conczin.mca.client.gui;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.widget.*;
import net.conczin.mca.client.resources.ClientUtils;
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
import net.conczin.mca.network.c2s.SkinListRequest;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.conczin.mca.network.c2s.VillagerNameRequest;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairList;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.LayeredHairList;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.client.resources.PresetCodec;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class VillagerEditorScreen extends Screen implements SkinListUpdateListener {
    protected static final int DATA_WIDTH = 175;
    private static final Identifier PREVIEW_MOUSE_FOLLOW_TEXTURE = MCA.locate("textures/gui/preview_mouse_follow.png");
    private static final int VOICE_PREVIEW_BUTTON_WIDTH = 22;
    private static final int TRAITS_PER_PAGE = 8;
    private static final int LAYERED_HAIR_PER_PAGE = 6;
    private static final float MIN_PREVIEW_ZOOM = 0.7F;
    private static final float MAX_PREVIEW_ZOOM = 1.4F;
    private static boolean isSkinListOutdated = true;
    private static HashMap<String, Clothing> clothing = new HashMap<>();
    private static HashMap<String, Hair> hair = new HashMap<>();
    private static HashMap<String, BodySkin> bodySkins = new HashMap<>();
    private static HashMap<String, LayeredHair> layeredHair = new HashMap<>();
    private static HashMap<String, HairStyle> hairStyles = new HashMap<>();
    private static boolean isSkinListLoaded;
    protected final VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(Objects.requireNonNull(Minecraft.getInstance().level), EntitySpawnReason.LOAD));
    protected final VillagerEntityMCA villagerVisualization = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(Objects.requireNonNull(Minecraft.getInstance().level), EntitySpawnReason.LOAD));
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
    private boolean hsvColoredHair;
    private int eyeColorTarget = 0; // 0 = Hair, 1 = Right/Both Eye, 2 = Left Eye
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
    private boolean restoreHideGui;
    private float previewRotation;
    private float previewZoom = 1.0F;
    private long lastFrameTime = -1L;
    private boolean rotatePreviewLeft;
    private boolean rotatePreviewRight;
    private boolean previewFollowsMouse = true;

    private static final int PRESETS_PER_PAGE = 4;
    private final File presetsDir = new File(Minecraft.getInstance().gameDirectory, "config/mca/presets");
    private final List<String> presetNames = new ArrayList<>();
    private String selectedPreset = null;
    private int currentPage = 0;
    private int maxPage = 0;
    private EditBox nameField;
    private ButtonWidget useButton;
    private ButtonWidget overwriteButton;
    private ButtonWidget deleteButton;
    private ButtonWidget renameButton;
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

    public static void setSkinList(HashMap<String, Clothing> clothing, HashMap<String, Hair> hair, HashMap<String, BodySkin> bodySkins, HashMap<String, LayeredHair> layeredHair, HashMap<String, HairStyle> hairStyles) {
        loadClientSkinLists();
        VillagerEditorScreen.clothing.putAll(clothing);
        VillagerEditorScreen.hair.putAll(hair);
        VillagerEditorScreen.bodySkins.putAll(bodySkins);
        VillagerEditorScreen.layeredHair.putAll(layeredHair);
        VillagerEditorScreen.hairStyles = getClientHairStyles(VillagerEditorScreen.hair);
        VillagerEditorScreen.hairStyles.putAll(hairStyles);
        isSkinListLoaded = true;
    }

    public static void sync() {
        seedClientSkinList();
        if (isSkinListOutdated) {
            Network.sendToServer(new SkinListRequest());
            isSkinListOutdated = false;
        }
    }

    private static void seedClientSkinList() {
        if (isSkinListLoaded) {
            return;
        }

        loadClientSkinLists();
        isSkinListLoaded = !clothing.isEmpty() || !hair.isEmpty() || !bodySkins.isEmpty() || !layeredHair.isEmpty() || !hairStyles.isEmpty();
    }

    private static void loadClientSkinLists() {
        ClothingList clothingList = ClothingList.getInstance();
        HairList hairList = HairList.getInstance();
        BodySkinList bodySkinList = BodySkinList.getInstance();
        LayeredHairList layeredHairList = LayeredHairList.getInstance();

        clothing = clothingList == null ? new HashMap<>() : new HashMap<>(clothingList.clothing);
        hair = hairList == null ? new HashMap<>() : new HashMap<>(hairList.hair);
        bodySkins = bodySkinList == null ? new HashMap<>() : new HashMap<>(bodySkinList.skins);
        layeredHair = layeredHairList == null ? new HashMap<>() : new HashMap<>(layeredHairList.hair);
        hairStyles = getClientHairStyles(hair);
    }

    private static HashMap<String, HairStyle> getClientHairStyles(Map<String, Hair> legacyHair) {
        HairStyleList hairStyleList = HairStyleList.getInstance();
        if (hairStyleList != null) {
            return hairStyleList.getAllStyles(legacyHair);
        }

        HashMap<String, HairStyle> styles = new HashMap<>();
        legacyHair.values().forEach(hair -> styles.putIfAbsent(hair.getIdentifier(), HairStyle.fromHair(hair)));
        return styles;
    }

    public static HashMap<String, Clothing> getClothing() {
        sync();
        return clothing;
    }

    public static HashMap<String, Hair> getHair() {
        sync();
        return hair;
    }

    public static HashMap<String, BodySkin> getBodySkins() {
        sync();
        return bodySkins;
    }

    public static HashMap<String, LayeredHair> getLayeredHair() {
        sync();
        return layeredHair;
    }

    public static HashMap<String, HairStyle> getHairStyles() {
        sync();
        return hairStyles;
    }

    public static void setSkinListOutdated() {
        isSkinListOutdated = true;
        isSkinListLoaded = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void added() {
        super.added();
        Minecraft minecraft = Minecraft.getInstance();
        restoreHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
    }

    @Override
    public void removed() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.hideGui = restoreHideGui;
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
        Genetics genetics = villager.getGenetics();
        for (Genetics.GeneType g : genes) {
            int x = width / 2 + (right ? DATA_WIDTH / 2 : 0);
            int widgetWidth = DATA_WIDTH / 2;
            if (g == Genetics.VOICE_TONE) {
                addVoicePreviewButton(width / 2 - VOICE_PREVIEW_BUTTON_WIDTH - 2, y);
            }
            addRenderableWidget(new GeneSliderWidget(x, y, widgetWidth, 20, Component.translatable(g.getTranslationKey()), genetics.getGene(g), b -> genetics.setGene(g, b.floatValue())));
            if (right) {
                y += 20;
            }
            right = !right;
        }
        return y + 4 + (right ? 20 : 0);
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
            int presetsX = (isPresetsPage || isTraitsPage) ? (width / 2 - DATA_WIDTH + 10) : (width / 2 - DATA_WIDTH + 50);

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
                            : Component.translatable("gui.mca.export_skin"),
                    b -> {
                        if (page.equals("traits")) {
                            Config.getInstance().enablePlayerShaders = !Config.getInstance().enablePlayerShaders;
                            b.setMessage(Component.translatable("gui.mca.trait_shaders",
                                    Component.translatable(Config.getInstance().enablePlayerShaders
                                            ? "gui.mca.trait_shaders.on" : "gui.mca.trait_shaders.off")
                                            .withStyle(Config.getInstance().enablePlayerShaders ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
                        } else if (selectedPreset != null) {
                            SkinExporter.export(villagerVisualization, selectedPreset);
                        }
                    }
            ));
            exportSkinButton.visible = isPresetsPage || isTraitsPage;
            exportSkinButton.active = isTraitsPage || (isPresetsPage && selectedPreset != null);
        }

        int y = height / 2 - 80;
        int margin = 40;
        Genetics genetics = villager.getGenetics();
        EditBox textFieldWidget;
        addPreviewRotationWidgets();

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

                y = addSkinSelectionWidgets(y);

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
                        textFieldWidget = addRenderableWidget(new NamedTextFieldWidget(this.font, width / 2, y, DATA_WIDTH, 18,
                                Component.translatable("gui.villager_editor.relation." + who.toLowerCase(Locale.ROOT))));
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
                //genes
                if (!Config.getServerConfig().allowPlayerSizeAdjustment && villagerUUID.equals(playerUUID)) {
                    y = doubleGeneSliders(y, Genetics.BREAST/*, Genetics.SKIN*/);
                    genetics.setGene(Genetics.SIZE, 0.80f);
                    genetics.setGene(Genetics.WIDTH, 0.80f);
                } else {
                    y = doubleGeneSliders(y, Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST/*, Genetics.SKIN*/);
                }

                //clothes
                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.randClothing"), b -> {
                    sendCommand("clothing");
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.selectClothing"), b -> {
                    setPage("clothing");
                }));
                y += 22;
                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.prev"), b -> {
                    CompoundTag compound = new CompoundTag();
                    compound.putInt("offset", -1);
                    sendCommand("clothing", compound);
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.next"), b -> {
                    CompoundTag compound = new CompoundTag();
                    compound.putInt("offset", 1);
                    sendCommand("clothing", compound);
                }));
                y += 22;

                //skin color
                addRenderableWidget(new ColorPickerWidget(width / 2 + margin, y, DATA_WIDTH - margin * 2, DATA_WIDTH - margin * 2,
                        genetics.getGene(Genetics.HEMOGLOBIN),
                        genetics.getGene(Genetics.MELANIN),
                        MCA.locate("textures/colormap/villager_skin.png"),
                        (vx, vy) -> {
                            genetics.setGene(Genetics.HEMOGLOBIN, vx.floatValue());
                            genetics.setGene(Genetics.MELANIN, vy.floatValue());
                        }));
            }
            case "head" -> {
                boolean hasHetero = villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
                int maxTarget = hasHetero ? 3 : 2;

                // Determine target label key
                String targetLabelKey = switch (eyeColorTarget) {
                    case 0 -> "gui.villager_editor.customize_hair";
                    case 1 -> hasHetero ? "gui.villager_editor.customize_eyes_right" : "gui.villager_editor.customize_eyes";
                    case 2 -> "gui.villager_editor.customize_eyes_left";
                    default -> "gui.villager_editor.customize_hair";
                };

                // Target Toggle Button
                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20,
                        Component.translatable(targetLabelKey),
                        b -> {
                            // Save current slider state to current target
                            switch (eyeColorTarget) {
                                case 0 -> refreshHairColor();
                                case 1 -> refreshEyeColor();
                                case 2 -> refreshEyeLeftColor();
                            }
                            // Advance target
                            eyeColorTarget = (eyeColorTarget + 1) % maxTarget;
                            // Load new target's dye
                            int targetDye = switch (eyeColorTarget) {
                                case 0 -> villager.getHairDye();
                                case 1 -> villager.getEyeDye();
                                case 2 -> villager.getEyeLeftDye();
                                default -> villager.getHairDye();
                            };
                            color.setRGB(
                                    ARGB.red(targetDye) / 255.0,
                                    ARGB.green(targetDye) / 255.0,
                                    ARGB.blue(targetDye) / 255.0
                            );
                            init();
                        }));

                // Mode button (Natural vs RGB) next to it
                String modeLabelKey = switch (eyeColorTarget) {
                    case 0 -> hsvColoredHair ? "gui.villager_editor.hair_hsv" : "gui.villager_editor.hair_genetic";
                    case 1 -> hsvColoredEyes ? "gui.villager_editor.eye_hsv" : "gui.villager_editor.eye_genetic";
                    case 2 -> hsvColoredEyesLeft ? "gui.villager_editor.eye_hsv" : "gui.villager_editor.eye_genetic";
                    default -> hsvColoredHair ? "gui.villager_editor.hair_hsv" : "gui.villager_editor.hair_genetic";
                };

                String modeTooltipKey = eyeColorTarget > 0 ? "gui.villager_editor.eye_mode.tooltip" : "gui.villager_editor.hair_mode.tooltip";

                addRenderableWidget(new TooltipButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20,
                        Component.translatable(modeLabelKey),
                        Component.translatable(modeTooltipKey),
                        b -> {
                            switch (eyeColorTarget) {
                                case 0 -> {
                                    hsvColoredHair = !hsvColoredHair;
                                }
                                case 1 -> {
                                    hsvColoredEyes = !hsvColoredEyes;
                                    if (hsvColoredEyes) {
                                        color.setHSV(0.0, 0.0, 1.0);
                                        refreshEyeColor();
                                    } else {
                                        villager.clearEyeDye();
                                    }
                                }
                                case 2 -> {
                                    hsvColoredEyesLeft = !hsvColoredEyesLeft;
                                    if (hsvColoredEyesLeft) {
                                        color.setHSV(0.0, 0.0, 1.0);
                                        refreshEyeLeftColor();
                                    } else {
                                        villager.clearEyeLeftDye();
                                    }
                                }
                            }
                            init();
                        }));

                y += 22;

                //genes
                y = geneChanger(y, Genetics.FACE, 7);
                y = doubleGeneSliders(y, Genetics.VOICE_TONE, Genetics.VOICE);

                //hair
                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.randHair"), b -> {
                    sendCommand("hair");
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.selectHair"), b -> {
                    setPage("hair");
                }));
                y += 22;
                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.prev"), b -> {
                    CompoundTag compound = new CompoundTag();
                    compound.putInt("offset", -1);
                    sendCommand("hair", compound);
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.next"), b -> {
                    CompoundTag compound = new CompoundTag();
                    compound.putInt("offset", 1);
                    sendCommand("hair", compound);
                }));
                y += 22;

                addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.villager_editor.advancedHair"), b -> {
                    setPage("hair_advanced");
                }));
                y += 22;

                // hair/eye color
                if (eyeColorTarget > 0) {
                    boolean activeHsv = eyeColorTarget == 1 ? hsvColoredEyes : hsvColoredEyesLeft;
                    if (activeHsv) {
                        // hue
                        color.hueWidget = addRenderableWidget(new HorizontalColorPickerWidget(width / 2 + 20, y, DATA_WIDTH - 40, 15,
                                color.hue / 360.0,
                                MCA.locate("textures/colormap/hue.png"),
                                (vx, vy) -> {
                                    color.setHSV(vx * 360, color.saturation, color.brightness);
                                    if (eyeColorTarget == 1) {
                                        refreshEyeColor();
                                    } else {
                                        refreshEyeLeftColor();
                                    }
                                }));

                        // saturation
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
                                    if (eyeColorTarget == 1) {
                                        refreshEyeColor();
                                    } else {
                                        refreshEyeLeftColor();
                                    }
                                }));

                        // brightness
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
                                    if (eyeColorTarget == 1) {
                                        refreshEyeColor();
                                    } else {
                                        refreshEyeLeftColor();
                                    }
                                }));

                        y += 65;

                        // Clear eye dye
                        String clearLabelKey = eyeColorTarget == 2 ? "gui.villager_editor.clear_eyes_left" : "gui.villager_editor.clear_eyes";
                        addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20,
                                Component.translatable(clearLabelKey),
                                b -> {
                                    if (eyeColorTarget == 1) {
                                        villager.clearEyeDye();
                                        hsvColoredEyes = false;
                                    } else {
                                        villager.clearEyeLeftDye();
                                        hsvColoredEyesLeft = false;
                                    }
                                    init();
                                }));
                    }
                } else {
                    // hair color
                    if (hsvColoredHair) {
                        // hue
                        color.hueWidget = addRenderableWidget(new HorizontalColorPickerWidget(width / 2 + 20, y, DATA_WIDTH - 40, 15,
                                color.hue / 360.0,
                                MCA.locate("textures/colormap/hue.png"),
                                (vx, vy) -> {
                                    color.setHSV(vx * 360, color.saturation, color.brightness);
                                    refreshHairColor();
                                }));

                        // saturation
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
                                    refreshHairColor();
                                }));

                        // brightness
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
                                    refreshHairColor();
                                }));

                        y += 65;

                        // Clear hair
                        addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH, 20,
                                Component.translatable("gui.villager_editor.clear_hair"),
                                b -> {
                                    villager.clearHairDye();
                                    init();
                                }));
                    } else {
                        y += 8;
                        int pickerSize = fitHairPickerSize(y, DATA_WIDTH - 20);
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
                    MutableComponent text = Component.translatable("entity.minecraft.villager." + p);
                    ButtonWidget widget = addRenderableWidget(new ButtonWidget(width / 2 + (right ? DATA_WIDTH / 2 : 0), y, DATA_WIDTH / 2, 20, text, b -> {
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
                assert minecraft != null;
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
                    setPage(this instanceof CombScreen ? "hair" : "head");
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
                        setPage(page.equals("skin") ? "general" : "body");
                    } else if (isLayeredHairPage()) {
                        setPage("hair_advanced");
                    } else {
                        setPage("head");
                    }
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 128, y, 64, 20, Component.translatable("gui.button.library"), b -> {
                    Minecraft.getInstance().setScreen(new SkinLibraryScreen(this, villagerVisualization));
                }));
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
                    addRenderableWidget(new ButtonWidget(width / 2, yVal, DATA_WIDTH, 20, btnText, b -> selectPreset(name)));
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
                
                ButtonWidget saveButton = addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - 80, yVal, 38, 20, Component.translatable("gui.mca.presets.save"), b -> savePreset()));
                renameButton = addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH - 40, yVal, 40, 20, Component.translatable("gui.mca.presets.rename"), b -> renamePreset()));

                String initialVal = nameField.getValue().trim();
                saveButton.active = !initialVal.isEmpty();
                renameButton.active = selectedPreset != null 
                    && !initialVal.isEmpty() 
                    && !initialVal.equals(selectedPreset) 
                    && !presetNames.contains(initialVal);

                // Set responder for reactive updates
                nameField.setResponder(val -> {
                    String newName = val.trim();
                    saveButton.active = !newName.isEmpty();
                    renameButton.active = selectedPreset != null 
                        && !newName.isEmpty() 
                        && !newName.equals(selectedPreset) 
                        && !presetNames.contains(newName);
                });
                yVal += 23;

                // Action Buttons: Use, Overwrite, Delete
                useButton = addRenderableWidget(new ButtonWidget(width / 2, yVal, 57, 20, Component.translatable("gui.mca.presets.use"), b -> usePreset()));
                useButton.active = selectedPreset != null;
                
                overwriteButton = addRenderableWidget(new ButtonWidget(width / 2 + 58, yVal, 59, 20, Component.translatable("gui.mca.presets.overwrite"), b -> overwritePreset()));
                overwriteButton.active = selectedPreset != null;
                
                deleteButton = addRenderableWidget(new ButtonWidget(width / 2 + 118, yVal, 57, 20, Component.translatable("gui.mca.presets.delete"), b -> deletePreset()));
                deleteButton.active = selectedPreset != null;
                yVal += 24;

                // Back Button
                addRenderableWidget(new ButtonWidget(width / 2, yVal, DATA_WIDTH, 20, Component.translatable("gui.back"), b -> setPage("general")));
            }
        }
    }

    private void refreshHairColor() {
        if (villager.getHairDye() == 0) {
            color.setHSV(0.0, 0.5, 0.5);
        }
        villager.setHairDye(
                Math.max(1.0f / 255.0f, (float) color.red),
                Math.max(1.0f / 255.0f, (float) color.green),
                Math.max(1.0f / 255.0f, (float) color.blue)
        );
    }

    private void refreshEyeColor() {
        if (villager.getEyeDye() == 0) {
            color.setHSV(0.0, 0.0, 1.0);
        }
        villager.setEyeDye(
                Math.max(1.0f / 255.0f, (float) color.red),
                Math.max(1.0f / 255.0f, (float) color.green),
                Math.max(1.0f / 255.0f, (float) color.blue)
        );
    }

    private void refreshEyeLeftColor() {
        if (villager.getEyeLeftDye() == 0) {
            color.setHSV(0.0, 0.0, 1.0);
        }
        villager.setEyeLeftDye(
                Math.max(1.0f / 255.0f, (float) color.red),
                Math.max(1.0f / 255.0f, (float) color.green),
                Math.max(1.0f / 255.0f, (float) color.blue)
        );
    }

    private int geneChanger(int y, Genetics.GeneType gene, int maxCount) {
        int bw = 22;
        Genetics genetics = villager.getGenetics();
        float val = genetics.getGene(gene);
        int currentIndex = (int) Math.min(maxCount - 1, Math.max(0, val * maxCount));

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

    private int fitHairPickerSize(int y, int preferredSize) {
        return Math.max(48, Math.min(preferredSize, height - y - 8));
    }

    private void addPreviewRotationWidgets() {
        boolean isSelection = isSelectionPage();
        int centerX = isSelection ? width / 2 : width / 2 - DATA_WIDTH / 2;
        int y = height / 2 + 75;

        if (isSelection) {
            // 6x22px buttons = 132px total, centered above the selection previews.
            int selY = height / 2 - 76;
            addRenderableWidget(new TooltipButtonWidget(centerX - 77, selY, 22, 14, Component.literal("R"), Component.translatable("gui.villager_editor.reset_zoom.tooltip"), b -> { previewZoom = 1.0F; }));
            addRenderableWidget(new ButtonWidget(centerX - 55, selY, 22, 14, Component.literal("-"), b -> zoomPreview(-0.1F)));
            addRenderableWidget(new ButtonWidget(centerX - 33, selY, 22, 14, Component.literal("<"), b -> rotatePreview(22.5F)));
            addRenderableWidget(new ToggleableTextureButtonWidget(centerX - 11, selY, 22, 14,
                    PREVIEW_MOUSE_FOLLOW_TEXTURE,
                    previewFollowsMouse,
                    Component.translatable("gui.villager_editor.preview_mouse_follow.tooltip"),
                    b -> {
                        previewFollowsMouse = !previewFollowsMouse;
                        setPage(page);
                    }));
            addRenderableWidget(new ButtonWidget(centerX + 11, selY, 22, 14, Component.literal(">"), b -> rotatePreview(-22.5F)));
            addRenderableWidget(new ButtonWidget(centerX + 33, selY, 22, 14, Component.literal("+"), b -> zoomPreview(0.1F)));
        } else {
            // Standard 22x20 buttons, centered above Done button (total width: 132px).
            addRenderableWidget(new TooltipButtonWidget(centerX - 77, y, 22, 20, Component.literal("R"), Component.translatable("gui.villager_editor.reset_zoom.tooltip"), b -> { previewZoom = 1.0F; }));
            addRenderableWidget(new ButtonWidget(centerX - 55, y, 22, 20, Component.literal("-"), b -> zoomPreview(-0.1F)));
            addRenderableWidget(new ButtonWidget(centerX - 33, y, 22, 20, Component.literal("<"), b -> rotatePreview(22.5F)));
            addRenderableWidget(new ToggleableTextureButtonWidget(centerX - 11, y, 22, 20,
                    PREVIEW_MOUSE_FOLLOW_TEXTURE,
                    previewFollowsMouse,
                    Component.translatable("gui.villager_editor.preview_mouse_follow.tooltip"),
                    b -> {
                        previewFollowsMouse = !previewFollowsMouse;
                        setPage(page);
                    }));
            addRenderableWidget(new ButtonWidget(centerX + 11, y, 22, 20, Component.literal(">"), b -> rotatePreview(-22.5F)));
            addRenderableWidget(new ButtonWidget(centerX + 33, y, 22, 20, Component.literal("+"), b -> zoomPreview(0.1F)));
        }
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
        sync();
        addRenderableWidget(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.randSkin"), b -> {
            sendCommand("skin");
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, Component.translatable("gui.villager_editor.selectSkin"), b -> {
            setPage("skin");
        }));
        y += 22;

        if (!isSkinListLoaded) {
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

    private Component getSkinIndexText() {
        List<String> skins = getBodySkinIdsForCurrentGender();
        String selected = villager.getSkin();
        int index = skins.indexOf(selected);
        int displayTotal = skins.size();
        int displayIndex = skins.isEmpty() ? 0 : index < 0 ? 1 : index + 1;
        return Component.translatable("gui.villager_editor.skin_index", displayIndex, displayTotal);
    }

    private List<String> getBodySkinIdsForCurrentGender() {
        Gender gender = villager.getGenetics().getGender();
        return getBodySkins().values().stream()
                .filter(skin -> skin.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || skin.getGender() == gender)
                .map(SkinListEntry::getIdentifier)
                .distinct()
                .sorted(VillagerEditorScreen::mca$compareNumerically)
                .toList();
    }

    private List<String> getLayeredHairIdsForCurrentGender(LayeredHair.Category category) {
        Gender gender = villager.getGenetics().getGender();
        List<String> layers = getLayeredHair().values().stream()
                .filter(hair -> hair.getCategory() == category)
                .filter(hair -> hair.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || hair.getGender() == gender)
                .map(SkinListEntry::getIdentifier)
                .distinct()
                .sorted(VillagerEditorScreen::mca$compareNumerically)
                .toList();
        if (category.isRequired()) {
            return layers;
        }

        List<String> optionalLayers = new ArrayList<>(layers.size() + 1);
        optionalLayers.add("");
        optionalLayers.addAll(layers);
        return optionalLayers;
    }

    private static int mca$compareNumerically(String a, String b) {
        int idxA = a.lastIndexOf('/');
        int idxB = b.lastIndexOf('/');
        String strA = idxA >= 0 ? a.substring(idxA) : a;
        String strB = idxB >= 0 ? b.substring(idxB) : b;
        String numA = strA.replaceAll("\\D+", "");
        String numB = strB.replaceAll("\\D+", "");
        return (!numA.isEmpty() && !numB.isEmpty()) ? Integer.compare(Integer.parseInt(numA), Integer.parseInt(numB)) : a.compareTo(b);
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
        List<String> layers = getLayeredHairIdsForCurrentGender(category);
        int index = layers.indexOf(selected);
        int displayIndex = index < 0 ? 1 : index + 1;
        return Component.translatable("gui.villager_editor.hair_layer_index", layerName, displayIndex, Math.max(1, layers.size()));
    }

    private Component getLayerName(LayeredHair.Category category) {
        return Component.translatable("gui.villager_editor.hair_layer." + category.getId());
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
            filteredClothing = filter(getClothing());
        } else if (Objects.equals(page, "hair")) {
            filteredHairStyles = filter(getHairStyles());
        } else if (Objects.equals(page, "skin")) {
            filteredBodySkins = filter(getBodySkins());
        } else if (isLayeredHairPage()) {
            LayeredHair.Category category = getLayeredHairCategory();
            filteredLayeredHair = filter(getLayeredHair().entrySet().stream()
                    .filter(entry -> entry.getValue().getCategory() == category)
                    .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll));
        }
    }

    private <T extends SkinListEntry> List<String> filter(HashMap<String, T> map) {
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
                .sorted(VillagerEditorScreen::mca$compareNumerically)
                .toList();

        clothingPageCount = Math.max(1, (int) Math.ceil(filtered.size() / ((float) getSelectionItemsPerPage())));
        clothingPage = Math.max(0, Math.min(clothingPage, clothingPageCount - 1));

        updateClothingPageWidget();

        return filtered;
    }

    private int getSelectionItemsPerPage() {
        return isLayeredHairPage() ? LAYERED_HAIR_PER_PAGE : CLOTHES_PER_PAGE;
    }

    protected String[] getPages() {
        if (villagerUUID.equals(playerUUID)) {
            return new String[]{"general", "body", "head", "traits"};
        } else {
            return new String[]{"general", "body", "head", "personality", "traits", "debug"};
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
        if (page.equals("clothing")) {
            return filteredClothing;
        }
        if (page.equals("hair")) {
            return filteredHairStyles;
        }
        if (page.equals("skin")) {
            return filteredBodySkins;
        }
        return filteredLayeredHair;
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
            assert minecraft != null;
            assert minecraft.player != null;
            villagerName = minecraft.player.getCustomName();
        } else if (villager.hasCustomName()) {
            villagerName = villager.getCustomName();
        }

        if (villagerName == null || MCA.isBlankString(villagerName.getString())) {
            // Failsafe-conditions for non-present custom names
            if (isPlayer) {
                assert minecraft != null;
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
                assert minecraft != null;
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
        int maxPage = (int) Math.ceil((double) traits.length / TRAITS_PER_PAGE) - 1;
        traitPage = Math.max(0, Math.min(maxPage, i));
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
            setPage("body");
            eventCallback("clothing");
            return true;

        }

        if (page.equals("hair") && (hoveredClothingId >= 0 && filteredHairStyles.size() > hoveredClothingId)) {
            applyHairStyle(villager, filteredHairStyles.get(hoveredClothingId));
            setPage("head");
            eventCallback("hair");
            return true;

        }

        if (page.equals("skin") && (hoveredClothingId >= 0 && filteredBodySkins.size() > hoveredClothingId)) {
            villager.setSkin(filteredBodySkins.get(hoveredClothingId));
            setPage("general");
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

        return super.mouseClicked(event, doubleClick);
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
        if (villager != null) {
            villager.tickCount++;
        }
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
        if (isMouseOverPreview(mouseX, mouseY)) {
            zoomPreview((float) scrollY * 0.1F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isMouseOverPreview(double mouseX, double mouseY) {
        if (isSelectionPage()) {
            return mouseY >= height / 2.0 - 90 && mouseY <= height / 2.0 + 75;
        }

        int x = width / 2 - DATA_WIDTH;
        int y = height / 2 - 8;
        return mouseX >= x && mouseX <= x + DATA_WIDTH && mouseY >= y - 57 && mouseY <= y + 88;
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
        float dt = (currentTime - lastFrameTime) / 1000.0F;
        lastFrameTime = currentTime;
        dt = Math.max(0.0F, Math.min(dt, 0.1F));

        if (rotatePreviewLeft) {
            rotatePreview(120.0F * dt);
        }
        if (rotatePreviewRight) {
            rotatePreview(-120.0F * dt);
        }

        if (villager == null) {
            return;
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
                    extractEntityPreview(context, x, y - 57, x + DATA_WIDTH, y + 88, 50, 0, mouseX, mouseY, delta, Minecraft.getInstance().player);
                } else {
                    extractEntityPreview(context, x, y - 57, x + DATA_WIDTH, y + 88, 50, 0, mouseX, mouseY, delta, villager);
                }

                // hint for confused people
                if (shouldPrintPlayerHint() && villagerUUID.equals(playerUUID) && getSelectedPlayerModel() != VillagerLike.PlayerModel.VILLAGER) {
                    final Matrix3x2fStack matrices = context.pose();
                    matrices.pushMatrix();
                    matrices.translate(width / 2.0F, height / 2 - 117);
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

                if (page.equals("clothing")) {
                    villagerVisualization.setClothes(selection.get(index));
                } else if (page.equals("hair")) {
                    applyHairStyle(villagerVisualization, selection.get(index));
                } else if (page.equals("skin")) {
                    villagerVisualization.setSkin(selection.get(index));
                } else {
                    villagerVisualization.setHair("");
                    villagerVisualization.setLayeredHair(getLayeredHairCategory(), selection.get(index));
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
        return mainPage.equals(page) || (mainPage.equals("head") && page.equals("hair_advanced"));
    }

    public void setVillagerName(String name) {
        villagerNameField.setValue(name);
        updateName(name);
    }

    public void setVillagerData(CompoundTag villagerData) {
        if (villager != null) {
            this.villagerData = villagerData;
            villager.load(TagValueInput.create(ProblemReporter.DISCARDING, villager.registryAccess(), villagerData));

            int hairDye = villager.getHairDye();
            int eyeDye = villager.getEyeDye();
            int eyeLeftDye = villager.getEyeLeftDye();

            if (page.equals("loading")) {
                hsvColoredHair = hairDye != 0xFF000000;
                hsvColoredEyes = eyeDye != 0xFFFFFFFF;
                hsvColoredEyesLeft = eyeLeftDye != 0xFFFFFFFF;
                eyeColorTarget = 0;
            }

            int initialDye = switch (eyeColorTarget) {
                case 1 -> eyeDye;
                case 2 -> eyeLeftDye;
                default -> hairDye;
            };

            int currentPick = ARGB.colorFromFloat(1.0f, (float) color.red, (float) color.green, (float) color.blue);
            if (initialDye != currentPick) {
                color.setRGB(
                        ARGB.red(initialDye) / 255.0,
                        ARGB.green(initialDye) / 255.0,
                        ARGB.blue(initialDye) / 255.0
                );
            }

            villagerBreedingAge = villagerData.getIntOr("Age", 0);
            villager.setAge(villagerBreedingAge);
            if (minecraft != null && minecraft.player != null) {
                villager.setPosRaw(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
                villagerVisualization.setPosRaw(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
            }
            villager.refreshDimensions();
        }
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
        HairStyle style = getHairStyles().get(styleId);
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
        this.selectedPreset = name;
        if (name != null) {
            if (nameField != null) {
                nameField.setValue(name);
            }
            try {
                File presetFile = new File(presetsDir, name + ".json");
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

    private void savePreset() {
        if (nameField == null) return;
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;
        
        try {
            CompoundTag tag = villager.toNbtForConversion();
            
            // Extract PlayerModel from villagerData NBT if present
            if (villagerData != null) {
                CompoundTag parentMca = getMcaData(villagerData);
                if (parentMca != null && parentMca.contains("PlayerModel")) {
                    tag.putInt("PlayerModel", parentMca.getInt("PlayerModel").orElse(0));
                }
            }

            File file = new File(presetsDir, name + ".json");
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
        if (newName.isEmpty() || newName.equals(selectedPreset)) return;

        try {
            File oldFile = new File(presetsDir, selectedPreset + ".json");
            File newFile = new File(presetsDir, newName + ".json");
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

    private void overwritePreset() {
        if (selectedPreset == null) return;
        savePreset();
    }

    private void deletePreset() {
        if (selectedPreset == null) return;
        try {
            File file = new File(presetsDir, selectedPreset + ".json");
            if (file.exists()) {
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
            File presetFile = new File(presetsDir, selectedPreset + ".json");
            if (presetFile.exists()) {
                String json = FileUtils.readFileToString(presetFile, StandardCharsets.UTF_8);
                CompoundTag tag = PresetCodec.fromJsonString(json);
                
                // Permanently apply to main villager
                villager.readNbtForConversion(tag);
                
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

    public VillagerEntityMCA getVillager() {
        return villager;
    }
}



