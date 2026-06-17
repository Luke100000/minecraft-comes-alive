package net.conczin.mca.client.gui;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.widget.*;
import net.conczin.mca.client.resources.ClientUtils;
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
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Matrix3x2fStack;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class VillagerEditorScreen extends Screen implements SkinListUpdateListener {
    protected static final int DATA_WIDTH = 175;
    private static final int TRAITS_PER_PAGE = 8;
    private static boolean isSkinListOutdated = true;
    private static HashMap<String, Clothing> clothing = new HashMap<>();
    private static HashMap<String, Hair> hair = new HashMap<>();
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
    private int clothingPage;
    private int clothingPageCount;
    private ButtonWidget pageButtonWidget;
    private List<String> filteredClothing = new LinkedList<>();
    private List<String> filteredHair = new LinkedList<>();
    private Gender filterGender = Gender.NEUTRAL;
    private String searchString = "";
    private int hoveredClothingId;
    private ButtonWidget villagerSkinWidget;
    private ButtonWidget playerSkinWidget;
    private ButtonWidget vanillaSkinWidget;
    private ButtonWidget doneWidget;
    private ButtonWidget genderButtonFemale;
    private ButtonWidget genderButtonMale;
    private boolean restoreHideGui;

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

    public static void setSkinList(HashMap<String, Clothing> clothing, HashMap<String, Hair> hair) {
        VillagerEditorScreen.clothing = clothing;
        VillagerEditorScreen.hair = hair;
    }

    public static void sync() {
        if (isSkinListOutdated) {
            Network.sendToServer(new SkinListRequest());
            isSkinListOutdated = false;
        }
    }

    public static HashMap<String, Clothing> getClothing() {
        sync();
        return clothing;
    }

    public static HashMap<String, Hair> getHair() {
        sync();
        return hair;
    }

    public static void setSkinListOutdated() {
        isSkinListOutdated = true;
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
            addRenderableWidget(new GeneSliderWidget(width / 2 + (right ? DATA_WIDTH / 2 : 0), y, DATA_WIDTH / 2, 20, Component.translatable(g.getTranslationKey()), genetics.getGene(g), b -> genetics.setGene(g, b.floatValue())));
            if (right) {
                y += 20;
            }
            right = !right;
        }
        return y + 4 + (right ? 20 : 0);
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
        this.page = page;

        clearWidgets();

        if (page.equals("loading")) {
            return;
        }

        //page selection
        if (shouldShowPageSelection()) {
            String[] pages = getPages();
            int w = DATA_WIDTH * 2 / pages.length;
            int x = (int) (width / 2.0 - pages.length / 2.0 * w);
            for (String p : pages) {
                addRenderableWidget(new ButtonWidget(x, height / 2 - 105, w, 20, Component.translatable("gui.villager_editor.page." + p), sender -> setPage(p))).active = !p.equals(page);
                x += w;
            }

            //close
            doneWidget = addRenderableWidget(new ButtonWidget(width / 2 - DATA_WIDTH + 20, height / 2 + 85, DATA_WIDTH - 40, 20, Component.translatable("gui.done"), sender -> {
                syncVillagerData();
                onClose();
            }));
        }

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

                //age
                if (!villagerUUID.equals(playerUUID)) {
                    addRenderableWidget(new GeneSliderWidget(width / 2, y, DATA_WIDTH, 20, Component.translatable("gui.villager_editor.age"), 1.0 + villagerBreedingAge / (double) AgeState.getMaxAge(), b -> {
                        villagerBreedingAge = -(int) ((1.0 - b) * AgeState.getMaxAge()) + 1;
                        villager.setAge(villagerBreedingAge);
                        villager.refreshDimensions();
                    }));
                    y += 28;
                }

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
            case "body" -> {
                //genes
                if (!Config.getServerConfig().allowPlayerSizeAdjustment && villagerUUID.equals(playerUUID)) {
                    y = doubleGeneSliders(y, Genetics.BREAST, Genetics.SKIN);
                    genetics.setGene(Genetics.SIZE, 0.80f);
                    genetics.setGene(Genetics.WIDTH, 0.80f);
                } else {
                    y = doubleGeneSliders(y, Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST, Genetics.SKIN);
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
                // HSV Hair selector
                addRenderableWidget(new TooltipButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20,
                        Component.translatable(hsvColoredHair ? "gui.villager_editor.hair_hsv" : "gui.villager_editor.hair_genetic"),
                        Component.translatable("gui.villager_editor.hair_mode.tooltip"),
                        b -> {
                            hsvColoredHair = !hsvColoredHair;
                            init();
                        }));

                //genes
                y = doubleGeneSliders(y, Genetics.FACE);
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

                //hair color
                if (hsvColoredHair) {
                    //hue
                    color.hueWidget = addRenderableWidget(new HorizontalColorPickerWidget(width / 2 + 20, y, DATA_WIDTH - 40, 15,
                            color.hue / 360.0,
                            MCA.locate("textures/colormap/hue.png"),
                            (vx, vy) -> {
                                color.setHSV(
                                        vx * 360,
                                        color.saturation,
                                        color.brightness
                                );
                                refreshHairColor();
                            }));

                    //saturation
                    color.saturationWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 20, y + 20, DATA_WIDTH - 40, 15,
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
                                refreshHairColor();
                            }));


                    //brightness
                    color.brightnessWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 20, y + 40, DATA_WIDTH - 40, 15,
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
                    addRenderableWidget(new ColorPickerWidget(width / 2 + margin, y, DATA_WIDTH - margin * 2, DATA_WIDTH - margin * 2,
                            genetics.getGene(Genetics.PHEOMELANIN),
                            genetics.getGene(Genetics.EUMELANIN),
                            MCA.locate("textures/colormap/villager_hair.png"),
                            (vx, vy) -> {
                                genetics.setGene(Genetics.PHEOMELANIN, vx.floatValue());
                                genetics.setGene(Genetics.EUMELANIN, vy.floatValue());
                            }));
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
                    Component text = p.name();
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
            case "clothing", "hair" -> {
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
                y = height / 2 + 85;
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
                    if (page.equals("clothing")) {
                        setPage("body");
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
        } else {
            filteredHair = filter(getHair());
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
                .map(Map.Entry::getKey)
                .toList();

        clothingPageCount = Math.max(1, (int) Math.ceil(filtered.size() / ((float) CLOTHES_PER_PAGE)));
        clothingPage = Math.max(0, Math.min(clothingPage, clothingPageCount - 1));

        updateClothingPageWidget();

        return filtered;
    }

    protected String[] getPages() {
        if (villagerUUID.equals(playerUUID)) {
            return new String[]{"general", "body", "head", "traits"};
        } else {
            return new String[]{"general", "body", "head", "personality", "traits", "debug"};
        }
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
        if (page.equals("clothing") && (hoveredClothingId >= 0 && filteredClothing.size() > hoveredClothingId)) {
            villager.setClothes(filteredClothing.get(hoveredClothingId));
            setPage("body");
            eventCallback("clothing");
            return true;

        }

        if (page.equals("hair") && (hoveredClothingId >= 0 && filteredHair.size() > hoveredClothingId)) {
            villager.setHair(filteredHair.get(hoveredClothingId));
            setPage("head");
            eventCallback("hair");
            return true;

        }

        return super.mouseClicked(event, doubleClick);
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

        if (villager == null) {
            return;
        }

        villager.tickCount = (int) (System.currentTimeMillis() / 50L);

        if (shouldDrawEntity()) {
            int x = width / 2 - DATA_WIDTH;
            int y = height / 2;
            if (villagerUUID.equals(playerUUID) && shouldUsePlayerModel()) {
                assert Minecraft.getInstance().player != null;
                InventoryScreen.extractEntityInInventoryFollowsMouse(context, x, y - 75, x + DATA_WIDTH, y + 75, 60, 0, mouseX, mouseY, Minecraft.getInstance().player);
            } else {
                InventoryScreen.extractEntityInInventoryFollowsMouse(context, x, y - 75, x + DATA_WIDTH, y + 75, 60, 0, mouseX, mouseY, villager);
            }

            // hint for confused people
            if (shouldPrintPlayerHint() && villagerUUID.equals(playerUUID) && getSelectedPlayerModel() != VillagerLike.PlayerModel.VILLAGER) {
                final Matrix3x2fStack matrices = context.pose();
                matrices.pushMatrix();
                matrices.translate(x + DATA_WIDTH / 2.0F, y + 75);
                matrices.scale(0.5f, 0.5f);
                context.centeredText(font, Component.translatable("gui.villager_editor.model_hint"), 0, 0, 0xAAFFFFFF);
                matrices.popMatrix();
            }
        }

        if (page.equals("clothing") || page.equals("hair")) {
            CompoundTag nbt = saveEntityData(villager);
            villagerVisualization.load(TagValueInput.create(ProblemReporter.DISCARDING, villagerVisualization.registryAccess(), nbt));
            villagerVisualization.setAge(villager.getAge());
            villagerVisualization.refreshDimensions();

            int i = 0;
            hoveredClothingId = -1;
            for (int y = 0; y < CLOTHES_V; y++) {
                for (int x = 0; x < CLOTHES_H + y; x++) {
                    int index = clothingPage * CLOTHES_PER_PAGE + i;
                    if ((page.equals("clothing") ? filteredClothing : filteredHair).size() > index) {
                        if (page.equals("clothing")) {
                            villagerVisualization.setClothes(filteredClothing.get(index));
                        } else {
                            villagerVisualization.setHair(filteredHair.get(index));
                        }

                        int cx = width / 2 + (int) ((x - CLOTHES_H / 2.0 + 0.5 - 0.5 * (y % 2)) * 40);
                        int cy = height / 2 + (int) ((y - CLOTHES_V / 2.0 + 0.5) * 65);

                        if (Math.abs(cx - mouseX) <= 20 && Math.abs(cy - mouseY + 5) <= 30) {
                            hoveredClothingId = index;
                        }

                        boolean hovered = hoveredClothingId == index;
                        int previewPadding = hovered ? 5 : 0;
                        InventoryScreen.extractEntityInInventoryFollowsMouse(context, cx - 20 - previewPadding, cy - 25 - previewPadding, cx + 20 + previewPadding, cy + 40 + previewPadding,
                                hovered ? 35 : 30, 0, mouseX, mouseY, villagerVisualization);
                        i++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    protected boolean shouldDrawEntity() {
        return !page.equals("loading") && !page.equals("clothing") && !page.equals("hair");
    }

    protected boolean shouldShowPageSelection() {
        return !page.equals("clothing") && !page.equals("hair");
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
            hsvColoredHair = hairDye != 0xFF000000;
            color.setRGB(
                    ARGB.red(hairDye) / 255.0,
                    ARGB.green(hairDye) / 255.0,
                    ARGB.blue(hairDye) / 255.0
            );

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

    @Override
    public void skinListUpdatedCallback() {
        filter();
    }

    public VillagerEntityMCA getVillager() {
        return villager;
    }
}



