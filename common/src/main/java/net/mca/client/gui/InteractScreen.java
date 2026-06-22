package net.mca.client.gui;

import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.network.c2s.*;
import net.mca.resources.data.analysis.Analysis;
import net.mca.resources.data.dialogue.Question;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class InteractScreen extends AbstractDynamicScreen {
    public static final Identifier ICON_TEXTURES = MCA.locate("textures/gui.png");

    private final VillagerLike<?> villager;
    private final PlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);

    private boolean inGiftMode;
    private int timeSinceLastClick;

    private String father;
    private String mother;

    private RelationshipState marriageState;
    private Text spouse;

    private List<String> dialogAnswers;
    private String dialogAnswerHover;
    private List<OrderedText> dialogQuestionText;
    private String dialogQuestionId;

    private static Analysis<?> analysis;

    public InteractScreen(VillagerLike<?> villager) {
        super(Text.literal("Interact"));
        this.villager = villager;
    }

    public void setParents(String father, String mother) {
        this.father = father;
        this.mother = mother;
    }

    public void setSpouse(RelationshipState marriageState, String spouse) {
        this.marriageState = marriageState;
        this.spouse = spouse == null ? Text.translatable("gui.interact.label.parentUnknown") : Text.literal(spouse);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        Objects.requireNonNull(this.client).setScreen(null);
        NetworkHandler.sendToServer(new InteractionCloseRequest(villager.asEntity().getUuid()));
    }

    @Override
    public void init() {
        NetworkHandler.sendToServer(new GetInteractDataRequest(villager.asEntity().getUuid()));
    }

    @Override
    public void tick() {
        timeSinceLastClick++;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);

        drawIcons(context);
        drawTextPopups(context);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double d) {
        if (d < 0) {
            player.getInventory().selectedSlot = player.getInventory().selectedSlot == 8 ? 0 : player.getInventory().selectedSlot + 1;
        } else if (d > 0) {
            player.getInventory().selectedSlot = player.getInventory().selectedSlot == 0 ? 8 : player.getInventory().selectedSlot - 1;
        }

        return super.mouseScrolled(x, y, d);
    }

    @Override
    public boolean mouseClicked(double posX, double posY, int button) {
        super.mouseClicked(posX, posY, button);

        // Dialog
        if (button == 0 && dialogAnswerHover != null && dialogQuestionText != null) {
            //todo double click (Likely fixable via using a different event -- 7.4.0)
            NetworkHandler.sendToServer(new InteractionDialogueMessage(villager.asEntity().getUuid(), dialogQuestionId, dialogAnswerHover));
        }

        // Right mouse button
        if (inGiftMode && button == 1) {
            NetworkHandler.sendToServer(new InteractionVillagerMessage("gui.button.gift", villager.asEntity().getUuid()));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(int keyChar, int keyCode, int unknown) {
        // Hotkey to leave gift mode
        if (keyChar == GLFW.GLFW_KEY_ESCAPE) {
            if (inGiftMode) {
                inGiftMode = false;
                setLayout("interact");
            } else {
                close();
            }
            return true;
        }
        return false;
    }

    private void drawIcons(DrawContext context) {
        final MatrixStack matrices = context.getMatrices();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        matrices.push();
        matrices.scale(iconScale, iconScale, iconScale);

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

        matrices.pop();
    }

    private void drawTextPopups(DrawContext context) {
        //name or state tip (gifting, ...)
        int h = 17;
        if (inGiftMode) {
            context.drawTooltip(textRenderer, Text.translatable("gui.interact.label.giveGift"), 10, 28);
        } else {
            context.drawTooltip(textRenderer, villager.asEntity().getName(), 10, 28);
        }

        //age or profession
        context.drawTooltip(textRenderer, villager.asEntity().isBaby() ? villager.getAgeState().getName() : villager.getProfessionText(), 10, 30 + h);

        VillagerBrain<?> brain = villager.getVillagerBrain();

        //mood
        context.drawTooltip(textRenderer,
                Text.translatable("gui.interact.label.mood", brain.getMood().getText())
                        .formatted(brain.getMood().getColor()), 10, 30 + h * 2);

        //personality
        if (hoveringOverText(10, 30 + h * 3, 128)) {
            context.drawTooltip(textRenderer, brain.getPersonality().getDescription(), 10, 30 + h * 3);
        } else {
            //White as we don't know if a personality is negative
            context.drawTooltip(textRenderer, Text.translatable("gui.interact.label.personality", brain.getPersonality().getName()).formatted(Formatting.WHITE), 10, 30 + h * 3);
        }

        //traits
        Set<Traits.Trait> traits = villager.getTraits().getTraits();
        if (traits.size() > 0) {
            if (hoveringOverText(10, 30 + h * 4, 128)) {
                //details
                List<Text> traitText = traits.stream().map(Traits.Trait::getDescription).collect(Collectors.toList());
                traitText.add(0, Text.translatable("traits.title"));
                context.drawTooltip(textRenderer, traitText, 10, 30 + h * 4);
            } else {
                //list
                MutableText traitText = Text.translatable("traits.title");
                traits.stream().map(Traits.Trait::getName).forEach(t -> {
                    if (traitText.getSiblings().size() > 0) {
                        traitText.append(Text.literal(", "));
                    }
                    traitText.append(t);
                });
                context.drawTooltip(textRenderer, traitText, 10, 30 + h * 4);
            }
        }

        //hearts
        if (hoveringOverIcon("redHeart")) {
            int hearts = brain.getMemoriesForPlayer(player).getHearts();
            drawHoveringIconText(context, Text.literal(hearts + " hearts"), "redHeart");
        }

        //marriage status
        if (marriageState != null && hoveringOverIcon("married") && villager instanceof CompassionateEntity<?>) {
            String ms = marriageState.base().getIcon().toLowerCase(Locale.ENGLISH);
            drawHoveringIconText(context, Text.translatable("gui.interact.label." + ms, spouse), "married");
        }

        //parents
        if (canDrawParentsIcon() && hoveringOverIcon("parents")) {
            drawHoveringIconText(context, Text.translatable("gui.interact.label.parents",
                    father == null ? Text.translatable("gui.interact.label.parentUnknown") : father,
                    mother == null ? Text.translatable("gui.interact.label.parentUnknown") : mother
            ), "parents");
        }

        //gift
        if (canDrawGiftIcon() && hoveringOverIcon("gift")) {
            drawHoveringIconText(context, Text.translatable("gui.interact.label.gift"), "gift");
        }

        //genes
        if (hoveringOverIcon("genes")) {
            List<Text> lines = new LinkedList<>();
            lines.add(Text.literal("Genes"));

            for (Genetics.Gene gene : villager.getGenetics()) {
                String key = gene.getType().getTranslationKey();
                int value = (int)(gene.get() * 100);
                lines.add(Text.translatable("gene.tooltip", Text.translatable(key), value));
            }

            drawHoveringIconText(context, lines, "genes");
        }

        //analysis
        if (hoveringOverIcon("analysis") && analysis != null) {
            List<Text> lines = new LinkedList<>();
            lines.add(Text.translatable("analysis.title").formatted(Formatting.GRAY));

            //summands
            for (Analysis.AnalysisElement d : analysis) {
                lines.add(Text.translatable("analysis." + d.getKey())
                        .append(Text.literal(": " + (d.isPositive() ? "+" : "") + d.getValue()))
                        .formatted(d.isPositive() ? Formatting.GREEN : Formatting.RED));
            }

            //total
            String chance = analysis.getTotalAsString();
            lines.add(Text.translatable("analysis.total").append(": " + chance));

            drawHoveringIconText(context, lines, "analysis");
        }

        //dialogue
        if (dialogQuestionText != null) {
            //background
            context.fill(width / 2 - 85, height / 2 - 50 - 10 * dialogQuestionText.size(), width / 2 + 85,
                    height / 2 - 30 + 10 * dialogAnswers.size(), 0x77000000);

            //question
            int i = -dialogQuestionText.size();
            for (OrderedText t : dialogQuestionText) {
                i++;
                context.drawTextWithShadow(textRenderer, t, width / 2 - textRenderer.getWidth(t) / 2, height / 2 - 50 + i * 10, 0xFFFFFFFF);
            }
            dialogAnswerHover = null;

            //separator
            context.drawHorizontalLine(width / 2 - 75, width / 2 + 75, height / 2 - 40, 0xAAFFFFFF);

            //answers
            int y = height / 2 - 35;
            for (String a : dialogAnswers) {
                boolean hover = hoveringOver(width / 2 - 100, y - 3, 200, 10);
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(Question.getTranslationKey(dialogQuestionId, a)), width / 2, y, hover ? 0xFFD7D784 : 0xAAFFFFFF);
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

    public void setLastPhrase(MutableText questionText, boolean silent) {
        MutableText text;
        if (!silent) {
            text = villager.sendChatMessage(questionText, player);
        } else {
            text = villager.transformMessage(questionText);
        }
        dialogQuestionText = textRenderer.wrapLines(text, 160);
    }

    @Override
    protected void buttonPressed(Button button) {
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
            MinecraftClient.getInstance().setScreen(new FamilyTreeScreen(villager.asEntity().getUuid()));
        } else if (id.equals("gui.button.talk")) {
            clearChildren();
            NetworkHandler.sendToServer(new InteractionDialogueInitMessage(villager.asEntity().getUuid()));
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
                NetworkHandler.sendToServer(new InteractionVillagerMessage(id, villager.asEntity().getUuid()));
            }
        } else if (id.equals("gui.button.gift")) {
            this.inGiftMode = true;
            disableAllButtons();
        }
    }

    public static void setAnalysis(Analysis<?> analysis) {
        InteractScreen.analysis = analysis;
    }
}
