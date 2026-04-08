package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.*;
import net.conczin.mca.resources.data.Analysis;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class InteractScreen extends AbstractDynamicScreen {
    public static final Identifier ICON_TEXTURES = MCA.locate("textures/gui.png");
    private static Analysis analysis;
    private final VillagerLike<?> villager;
    private final Player player = Objects.requireNonNull(Minecraft.getInstance().player);
    private boolean inGiftMode;
    private int timeSinceLastClick;
    private String father;
    private String mother;
    private RelationshipState marriageState;
    private Component spouse;
    private List<String> dialogAnswers;
    private String dialogAnswerHover;
    private List<FormattedCharSequence> dialogQuestionText;
    private String dialogQuestionId;

    public InteractScreen(VillagerLike<?> villager) {
        super(Component.literal("Interact"));
        this.villager = villager;
    }

    public static void setAnalysis(Analysis analysis) {
        InteractScreen.analysis = analysis;
    }

    public void setParents(String father, String mother) {
        this.father = father;
        this.mother = mother;
    }

    public void setSpouse(RelationshipState marriageState, String spouse) {
        this.marriageState = marriageState;
        this.spouse = spouse == null ? Component.translatable("gui.interact.label.parentUnknown") : Component.literal(spouse);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Objects.requireNonNull(this.minecraft).setScreen(null);
        Network.sendToServer(new InteractionCloseRequest(villager.asEntity().getUUID()));
    }

    @Override
    public void init() {
        Network.sendToServer(new GetInteractDataRequest(villager.asEntity().getId()));
    }

    @Override
    public void tick() {
        timeSinceLastClick++;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Nop
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float tickDelta) {
        super.extractRenderState(context, mouseX, mouseY, tickDelta);

        drawIcons(context);
        drawTextPopups(context);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (dy < 0) {
            player.getInventory().setSelectedSlot(selectedSlot == 8 ? 0 : selectedSlot + 1);
        } else if (dy > 0) {
            player.getInventory().setSelectedSlot(selectedSlot == 0 ? 8 : selectedSlot - 1);
        }

        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        super.mouseClicked(event, doubleClick);

        // Dialog
        if (event.button() == 0 && dialogAnswerHover != null && dialogQuestionText != null) {
            //todo double click (Likely fixable via using a different event -- 7.4.0)
            Network.sendToServer(new InteractionDialogueMessage(villager.asEntity().getUUID(), dialogQuestionId, dialogAnswerHover));
        }

        // Right mouse button
        if (inGiftMode && event.button() == 1) {
            Network.sendToServer(new InteractionVillagerMessage("gift", villager.asEntity().getUUID()));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Hotkey to leave gift mode
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (inGiftMode) {
                inGiftMode = false;
                setLayout("interact");
            } else {
                onClose();
            }
            return true;
        }
        return false;
    }

    private void drawIcons(GuiGraphicsExtractor context) {
        final Matrix3x2fStack matrices = context.pose();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        matrices.pushMatrix();
        matrices.scale(iconScale, iconScale);

        if (marriageState != null) {
            drawIcon(context, ICON_TEXTURES, marriageState.getIcon());
        }

        drawIcon(context, ICON_TEXTURES, memory.getHearts() < 0 ? "blackHeart" : memory.getHearts() >= 100 ? "goldHeart" : "redHeart");
        // drawIcon(transform, "neutralEmerald");
        drawIcon(context, ICON_TEXTURES, "genes");

        if (canDrawParentsIcon()) {
            drawIcon(context, ICON_TEXTURES, "parents");
        }
        if (canDrawGiftIcon()) {
            drawIcon(context, ICON_TEXTURES, "gift");
        }

        if (analysis != null) {
            drawIcon(context, ICON_TEXTURES, "analysis");
        }

        matrices.popMatrix();
    }

    private void drawTextPopups(GuiGraphicsExtractor context) {
        //name or state tip (gifting, ...)
        int h = 17;
        if (inGiftMode) {
            context.setTooltipForNextFrame(font, Component.translatable("gui.interact.label.giveGift"), 10, 28);
        } else {
            context.setTooltipForNextFrame(font, villager.asEntity().getName(), 10, 28);
        }

        //age or profession
        context.setTooltipForNextFrame(font, villager.asEntity().isBaby() ? villager.getAgeState().getName() : villager.getProfessionText(), 10, 30 + h);

        VillagerBrain<?> brain = villager.getVillagerBrain();

        //mood
        context.setTooltipForNextFrame(font,
                Component.translatable("gui.interact.label.mood", brain.getMood().getText())
                        .withStyle(brain.getMood().getColor()), 10, 30 + h * 2);

        //personality
        if (hoveringOverText(10, 30 + h * 3, 128)) {
            context.setTooltipForNextFrame(font, brain.getPersonality().getDescription(), 10, 30 + h * 3);
        } else {
            //White as we don't know if a personality is negative
            context.setTooltipForNextFrame(font, Component.translatable("gui.interact.label.personality", brain.getPersonality().getName()).withStyle(ChatFormatting.WHITE), 10, 30 + h * 3);
        }

        //traits
        Set<Traits.Trait> traits = villager.getTraits().getTraits();
        if (!traits.isEmpty()) {
            if (hoveringOverText(10, 30 + h * 4, 128)) {
                //details
                List<Component> traitText = traits.stream().map(Traits.Trait::getDescription).collect(Collectors.toList());
                traitText.addFirst(Component.translatable("traits.title"));
                context.setComponentTooltipForNextFrame(font, traitText, 10, 30 + h * 4);
            } else {
                //list
                MutableComponent traitText = Component.translatable("traits.title");
                traits.stream().map(Traits.Trait::getName).forEach(t -> {
                    if (!traitText.getSiblings().isEmpty()) {
                        traitText.append(Component.literal(", "));
                    }
                    traitText.append(t);
                });
                context.setTooltipForNextFrame(font, traitText, 10, 30 + h * 4);
            }
        }

        //hearts
        if (hoveringOverIcon("redHeart")) {
            int hearts = brain.getMemoriesForPlayer(player).getHearts();
            drawHoveringIconText(context, Component.literal(hearts + " hearts"), "redHeart");
        }

        //marriage status
        if (marriageState != null && hoveringOverIcon("married") && villager instanceof CompassionateEntity<?>) {
            String ms = marriageState.base().getIcon().toLowerCase(Locale.ENGLISH);
            drawHoveringIconText(context, Component.translatable("gui.interact.label." + ms, spouse), "married");
        }

        //parents
        if (canDrawParentsIcon() && hoveringOverIcon("parents")) {
            drawHoveringIconText(context, Component.translatable("gui.interact.label.parents",
                    father == null ? Component.translatable("gui.interact.label.parentUnknown") : father,
                    mother == null ? Component.translatable("gui.interact.label.parentUnknown") : mother
            ), "parents");
        }

        //gift
        if (canDrawGiftIcon() && hoveringOverIcon("gift")) {
            drawHoveringIconText(context, Component.translatable("gui.interact.label.gift"), "gift");
        }

        //genes
        if (hoveringOverIcon("genes")) {
            List<Component> lines = new LinkedList<>();
            lines.add(Component.literal("Genes"));

            for (Genetics.Gene gene : villager.getGenetics()) {
                String key = gene.getType().getTranslationKey();
                int value = (int) (gene.get() * 100);
                lines.add(Component.translatable("gene.tooltip", Component.translatable(key), value));
            }

            drawHoveringIconText(context, lines, "genes");
        }

        //analysis
        if (hoveringOverIcon("analysis") && analysis != null) {
            List<Component> lines = new LinkedList<>();
            lines.add(Component.translatable("analysis.title").withStyle(ChatFormatting.GRAY));

            //summands
            for (Analysis.AnalysisElement d : analysis) {
                lines.add(Component.translatable("analysis." + d.key())
                        .append(Component.literal(": " + (d.positive() ? "+" : "") + d.value()))
                        .withStyle(d.positive() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }

            //total
            String chance = analysis.getTotalAsString();
            lines.add(Component.translatable("analysis.total").append(": " + chance));

            drawHoveringIconText(context, lines, "analysis");
        }

        //dialogue
        if (dialogQuestionText != null) {
            //background
            context.fill(width / 2 - 85, height / 2 - 50 - 10 * dialogQuestionText.size(), width / 2 + 85,
                    height / 2 - 30 + 10 * dialogAnswers.size(), 0x77000000);

            //question
            int i = -dialogQuestionText.size();
            for (FormattedCharSequence t : dialogQuestionText) {
                i++;
                context.text(font, t, width / 2 - font.width(t) / 2, height / 2 - 50 + i * 10, 0xFFFFFFFF);
            }
            dialogAnswerHover = null;

            //separator
            context.horizontalLine(width / 2 - 75, width / 2 + 75, height / 2 - 40, 0xAAFFFFFF);

            //answers
            int y = height / 2 - 35;
            for (String a : dialogAnswers) {
                boolean hover = hoveringOver(width / 2 - 100, y - 3, 200, 10);
                context.centeredText(font, Component.translatable(Question.getTranslationKey(dialogQuestionId, a)), width / 2, y, hover ? 0xFFD7D784 : 0xAAFFFFFF);
                if (hover) {
                    dialogAnswerHover = a;
                }
                y += 10;
            }
        }
    }

    //checks if the mouse hovers over a tooltip
    //tooltips are not rendered on the given coordinates, so we need an offset
    private boolean hoveringOverText(int x, int y, int w) {
        return hoveringOver(x + 8, y - 16, w, 16);
    }

    private boolean canDrawParentsIcon() {
        return father != null || mother != null;
    }

    private boolean canDrawGiftIcon() {
        return false;//villager.getVillagerBrain().getMemoriesForPlayer(player).isGiftPresent();
    }

    public void setDialogue(String dialogue, List<String> answers) {
        dialogQuestionId = dialogue;
        dialogAnswers = answers;
    }

    public void setLastPhrase(Component questionText, boolean silent) {
        Component text;
        if (!silent) {
            text = villager.sendChatMessage(questionText, player);
        } else {
            text = villager.transformMessage(questionText);
        }
        dialogQuestionText = font.split(text, 160);
    }

    @Override
    protected void buttonPressed(MCAButton button) {
        String id = button.identifier();

        if (timeSinceLastClick <= 2) {
            return; /* Prevents click-through on Mojang's button system */
        }
        timeSinceLastClick = 0;

        /* Progression to different GUIs */
        if (id.equals("gui.button.interact")) {
            setLayout("interact");
        } else if (id.equals("gui.button.command")) {
            setLayout("command");
            disableButton("gui.button." + villager.getVillagerBrain().getMoveState().name().toLowerCase(Locale.ENGLISH));
        } else if (id.equals("gui.button.clothing")) {
            setLayout("clothing");
        } else if (id.equals("gui.button.familyTree")) {
            Minecraft.getInstance().setScreen(new FamilyTreeScreen(villager.asEntity().getUUID()));
        } else if (id.equals("gui.button.talk")) {
            clearWidgets();
            Network.sendToServer(new InteractionDialogueInitMessage(villager.asEntity().getUUID()));
        } else if (id.equals("gui.button.work")) {
            setLayout("work");
            disableButton("gui.button." + villager.getVillagerBrain().getCurrentJob().name().toLowerCase(Locale.ENGLISH));
        } else if (id.equals("gui.button.professions")) {
            setLayout("professions");
        } else if (id.equals("gui.button.backarrow")) {
            if (inGiftMode) {
                inGiftMode = false;
                setLayout("interact");
            } else if (getActiveScreen().equals("locations")) {
                setLayout("interact");
            } else {
                setLayout("main");
            }
        } else if (id.equals("gui.button.locations")) {
            setLayout("locations");
        } else if (button.notifyServer()) {
            /* Anything that should notify the server is handled here */

            if (!button.targetServer()) {
                Network.sendToServer(new InteractionVillagerMessage(id.replace("gui.button.", ""), villager.asEntity().getUUID()));
            }
        } else if (id.equals("gui.button.gift")) {
            this.inGiftMode = true;
            disableAllButtons();
        }
    }
}



