package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.c2s.*;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.conczin.mca.network.s2c.InteractionDialogueOptionsResponse;
import net.conczin.mca.resources.data.Analysis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class InteractScreen extends AbstractDynamicScreen {
    public static final ResourceLocation ICON_TEXTURES = MCA.locate("textures/gui.png");
    private static final int INK = 0xFF292018;
    private static final int FRAME = 0xFF67452F;
    private static final int GOLD = 0xFFFFCD68;
    private static final int CREAM = 0xFFFFEED3;
    private static final int FOOTER_HEIGHT = 27;
    private static Analysis analysis;
    private final VillagerLike<?> villager;
    private final UUID interactionId;
    private final Player player = Objects.requireNonNull(Minecraft.getInstance().player);
    private boolean inGiftMode;
    private int timeSinceLastClick;
    private String father;
    private String mother;
    private RelationshipState marriageState;
    private Component spouse;
    private boolean dialogueMode;
    private final EnumSet<DialogueTopicSection> expandedSections = EnumSet.noneOf(DialogueTopicSection.class);
    private final List<DialogueHit> dialogueHits = new ArrayList<>();
    private final DialogueMenuScrollWidget dialogueScroller = new DialogueMenuScrollWidget();
    private int dialoguePassageScroll;
    private DialogueViewport dialogueListViewport;
    private DialogueViewport dialoguePassageViewport;
    private long dialogueDisplayedToken;
    private boolean dialogueTokenTracked;
    private long dialogueRenderedToken;
    private final DialogueMenuAnimation dialogueHeight = new DialogueMenuAnimation();
    private InteractionDialogueNodeResponse.Node dialogueDisplayedNode;
    private boolean qHeld;
    private int dialogueKeyboardIndex = -1;
    private DialogueTopicSection dialogueSectionToReveal;

    public InteractScreen(VillagerLike<?> villager, UUID interactionId) {
        super(Component.literal("Interact"));
        this.villager = villager;
        this.interactionId = Objects.requireNonNull(interactionId, "interactionId");
    }

    public boolean isInteractingWith(UUID villagerId) {
        return villager.asEntity().getUUID().equals(villagerId);
    }

    public boolean matchesInteraction(int villagerEntityId, UUID interactionId) {
        return villager.asEntity().getId() == villagerEntityId && this.interactionId.equals(interactionId);
    }

    public void closeFromServer() {
        dialoguePresentation().ifPresent(ClientHandlerImpl.DialoguePresentation::pause);
        Objects.requireNonNull(this.minecraft).setScreen(null);
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
        ClientHandlerImpl.DialoguePresentation presentation = dialoguePresentation().orElse(null);
        UUID sessionId = presentation == null ? Util.NIL_UUID : presentation.sessionId().orElse(Util.NIL_UUID);
        long offerToken = presentation == null ? 0L : presentation.offerToken().orElse(0L);
        dialoguePresentation().ifPresent(ClientHandlerImpl.DialoguePresentation::pause);
        Objects.requireNonNull(this.minecraft).setScreen(null);
        Network.sendToServer(new InteractionCloseRequest(villager.asEntity().getUUID(), interactionId, sessionId, offerToken));
    }

    @Override
    public void init() {
        dialogueSectionToReveal = null;
        dialogueHits.clear();
        dialogueScroller.reset();
        dialogueHeight.reset();
        dialogueDisplayedNode = null;
        Network.sendToServer(new GetInteractDataRequest(villager.asEntity().getId(), interactionId));
    }

    @Override
    public void setConstraints(Set<Constraint> constraints) {
        super.setConstraints(constraints);
        // Interaction data may arrive after Talk has already hidden the normal buttons.
        // Keep the refreshed constraints without restoring clickable controls over Talk.
        if (dialogueMode) {
            clearWidgets();
        }
    }

    @Override
    public void tick() {
        timeSinceLastClick++;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Nop
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);

        if (dialogueMode) {
            dialoguePresentation().ifPresent(presentation ->
                    presentation.tick(System.nanoTime() / 1_000_000L));
        }
        drawIcons(context);
        drawTextPopups(context);
        drawDialogueEventState(context, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dialogueMode) {
            dialogueHits.clear();
            if (dialogueListViewport != null && dialogueListViewport.contains(x, y)) {
                dialogueHeight.hold(System.nanoTime() / 1_000_000L);
                dialogueScroller.mouseScrolled(x, y, dx, dy);
            } else if (dialoguePassageViewport != null && dialoguePassageViewport.contains(x, y)) {
                int delta = (int) Math.signum(dy) * 20;
                dialoguePassageScroll = Math.max(0, Math.min(dialoguePassageViewport.limit(),
                        dialoguePassageScroll - delta));
            }
            return true;
        }
        if (dy < 0) {
            player.getInventory().selected = player.getInventory().selected == 8 ? 0 : player.getInventory().selected + 1;
        } else if (dy > 0) {
            player.getInventory().selected = player.getInventory().selected == 0 ? 8 : player.getInventory().selected - 1;
        }

        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseClicked(double posX, double posY, int button) {
        super.mouseClicked(posX, posY, button);

        if (button == 0 && dialogueMode) {
            if (dialogueScroller.hasScrollbar()
                    && posX >= dialogueScroller.getX() + dialogueScroller.getWidth()
                    && posX <= dialogueScroller.getX() + dialogueScroller.getWidth() + dialogueScroller.scrollbarWidth()
                    && dialogueListViewport != null && dialogueListViewport.contains(posX, posY)
                    && dialogueScroller.mouseClicked(posX, posY, button)) {
                dialogueScroller.setFocused(true);
                dialogueHeight.hold(System.nanoTime() / 1_000_000L);
                dialogueHits.clear();
                return true;
            }
            dialogueScroller.setFocused(false);
            ClientHandlerImpl.DialoguePresentation presentation = dialoguePresentation().orElse(null);
            if (presentation != null && presentation.offerToken().orElse(Long.MIN_VALUE) == dialogueRenderedToken) {
                for (DialogueHit hit : dialogueHits) {
                    if (posX > hit.left() && posX < hit.right()
                            && posY > hit.top() && posY < hit.bottom()) {
                        return handleDialogueClick(hit.click());
                    }
                }
                if (dialoguePassageViewport != null && dialoguePassageViewport.contains(posX, posY)
                        && presentation.revealFully()) {
                    return true;
                }
            }
        }

        // Right mouse button
        if (inGiftMode && button == 1) {
            Network.sendToServer(new InteractionVillagerMessage("gift", villager.asEntity().getUUID(), interactionId));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dialogueMode && dialogueScroller.isFocused()
                && dialogueScroller.mouseDragged(x, y, button, dx, dy)) {
            dialogueHits.clear();
            dialogueKeyboardIndex = -1;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (dialogueMode && button == 0) {
            dialogueScroller.mouseReleased(x, y, button);
            dialogueScroller.setFocused(false);
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int keyChar, int keyCode, int unknown) {
        if (keyChar == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyChar == GLFW.GLFW_KEY_Q) {
            if (!qHeld) {
                qHeld = true;
                navigateBack();
            }
            return true;
        }
        if (dialogueMode && (keyChar == GLFW.GLFW_KEY_UP || keyChar == GLFW.GLFW_KEY_DOWN)) {
            if (!dialogueHits.isEmpty()) {
                int direction = keyChar == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                dialogueKeyboardIndex = Math.floorMod(dialogueKeyboardIndex + direction, dialogueHits.size());
            }
            return true;
        }
        if (dialogueMode && (keyChar == GLFW.GLFW_KEY_ENTER || keyChar == GLFW.GLFW_KEY_KP_ENTER
                || keyChar == GLFW.GLFW_KEY_SPACE) && dialogueKeyboardIndex >= 0
                && dialogueKeyboardIndex < dialogueHits.size()) {
            if (dialoguePresentation().map(p -> p.offerToken().orElse(Long.MIN_VALUE) == dialogueRenderedToken)
                    .orElse(false)) {
                return handleDialogueClick(dialogueHits.get(dialogueKeyboardIndex).click());
            }
        }
        // Allow keyboard gifting without repeating a transfer for every held-key event.
        if (inGiftMode && !dialogueMode
                && (keyChar == GLFW.GLFW_KEY_ENTER || keyChar == GLFW.GLFW_KEY_KP_ENTER)) {
            if (timeSinceLastClick > 2) {
                timeSinceLastClick = 0;
                Network.sendToServer(new InteractionVillagerMessage("gift", villager.asEntity().getUUID(), interactionId));
            }
            return true;
        }
        return super.keyPressed(keyChar, keyCode, unknown);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_Q) {
            qHeld = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void navigateBack() {
        if (dialogueMode) {
            if (dialoguePresentation().map(ClientHandlerImpl.DialoguePresentation::nodeVisible).orElse(false)) {
                requestDialogueTopics();
            } else {
                leaveDialogueMode();
            }
        } else if (inGiftMode) {
            inGiftMode = false;
            setLayout("interact");
        } else if (getActiveScreen().equals("locations")) {
            setLayout("interact");
        } else if (!getActiveScreen().equals("main")) {
            setLayout("main");
        }
    }

    private void drawIcons(GuiGraphics context) {
        final PoseStack matrices = context.pose();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        matrices.pushPose();
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

        matrices.popPose();
    }

    private void drawTextPopups(GuiGraphics context) {
        //name or state tip (gifting, ...)
        int h = 17;
        if (inGiftMode) {
            context.renderTooltip(font, Component.translatable("gui.interact.label.giveGift"), 10, 28);
        } else {
            context.renderTooltip(font, villager.asEntity().getName(), 10, 28);
        }

        //age or profession
        context.renderTooltip(font, villager.asEntity().isBaby() ? villager.getAgeState().getName() : villager.getProfessionText(), 10, 30 + h);

        VillagerBrain<?> brain = villager.getVillagerBrain();

        //mood
        context.renderTooltip(font,
                Component.translatable("gui.interact.label.mood", brain.getMood().getText())
                        .withStyle(brain.getMood().getColor()), 10, 30 + h * 2);

        //personality
        if (hoveringOverText(10, 30 + h * 3, 128)) {
            context.renderTooltip(font, brain.getPersonality().getDescription(), 10, 30 + h * 3);
        } else {
            //White as we don't know if a personality is negative
            context.renderTooltip(font, Component.translatable("gui.interact.label.personality", brain.getPersonality().getName()).withStyle(ChatFormatting.WHITE), 10, 30 + h * 3);
        }

        //traits
        Set<Traits.Trait> traits = villager.getTraits().getTraits();
        if (!traits.isEmpty()) {
            if (hoveringOverText(10, 30 + h * 4, 128)) {
                //details
                List<Component> traitText = traits.stream().map(Traits.Trait::getDescription).collect(Collectors.toList());
                traitText.addFirst(Component.translatable("traits.title"));
                context.renderComponentTooltip(font, traitText, 10, 30 + h * 4);
            } else {
                //list
                MutableComponent traitText = Component.translatable("traits.title");
                traits.stream().map(Traits.Trait::getName).forEach(t -> {
                    if (!traitText.getSiblings().isEmpty()) {
                        traitText.append(Component.literal(", "));
                    }
                    traitText.append(t);
                });
                context.renderTooltip(font, traitText, 10, 30 + h * 4);
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

    public boolean isDialogueMode() {
        return dialogueMode;
    }

    public Component resolveDialogueLine(Component line, boolean silent) {
        // The Talk panel already displays this speech. Keep its normal transformation and
        // sound without sending a second copy to the Minecraft chat HUD.
        Component resolved = villager.transformMessage(line);
        if (!silent) {
            villager.playSpeechEffect();
        }
        return resolved;
    }

    public void requestDialogueMenu() {
        requestDialogueMenu(true);
    }

    public void requestDialogueTopics() {
        requestDialogueMenu(false);
    }

    private void requestDialogueMenu(boolean showOpening) {
        dialogueSectionToReveal = null;
        dialogueMode = true;
        if (showOpening) {
            expandedSections.clear();
        }
        dialogueHits.clear();
        dialogueScroller.reset();
        dialoguePassageScroll = 0;
        dialogueKeyboardIndex = -1;
        dialogueHeight.reset();
        dialogueDisplayedNode = null;
        clearWidgets();
        dialoguePresentation().ifPresent(ClientHandlerImpl.DialoguePresentation::beginRequest);
        Network.sendToServer(new InteractionDialogueBeginMessage(villager.asEntity().getUUID(), showOpening));
    }

    public void leaveDialogueMode() {
        leaveDialogueMode(true);
    }

    public void leaveDialogueMode(boolean notifyServer) {
        dialogueSectionToReveal = null;
        ClientHandlerImpl.DialoguePresentation presentation = dialoguePresentation().orElse(null);
        UUID sessionId = presentation == null ? Util.NIL_UUID : presentation.sessionId().orElse(Util.NIL_UUID);
        long offerToken = presentation == null ? 0L : presentation.offerToken().orElse(0L);
        dialogueMode = false;
        dialogueHits.clear();
        dialogueScroller.reset();
        dialoguePassageScroll = 0;
        dialogueTokenTracked = false;
        dialogueKeyboardIndex = -1;
        dialogueHeight.reset();
        dialogueDisplayedNode = null;
        dialoguePresentation().ifPresent(ClientHandlerImpl.DialoguePresentation::dismissOptions);
        if (notifyServer) {
            Network.sendToServer(new InteractionDialogueLeaveMessage(villager.asEntity().getUUID(), sessionId, offerToken));
        }
        setLayout("main");
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
            requestDialogueMenu();
        } else if (id.equals("gui.button.work")) {
            setLayout("work");
            disableButton("gui.button." + villager.getVillagerBrain().getCurrentJob().name().toLowerCase(Locale.ENGLISH));
        } else if (id.equals("gui.button.professions")) {
            setLayout("professions");
        } else if (id.equals("gui.button.backarrow")) {
            navigateBack();
        } else if (id.equals("gui.button.locations")) {
            setLayout("locations");
        } else if (button.notifyServer()) {
            /* Anything that should notify the server is handled here */

            if (!button.targetServer()) {
                Network.sendToServer(new InteractionVillagerMessage(id.replace("gui.button.", ""),
                        villager.asEntity().getUUID(), interactionId));
            }
        } else if (id.equals("gui.button.gift")) {
            this.inGiftMode = true;
            disableAllButtons();
        }
    }

    private Optional<ClientHandlerImpl.DialoguePresentation> dialoguePresentation() {
        return ClientProxy.getNetworkHandler() instanceof ClientHandlerImpl handler
                ? Optional.of(handler.dialoguePresentation())
                : Optional.empty();
    }

    private List<DialogueRow> dialogueRows(ClientHandlerImpl.DialoguePresentation presentation) {
        List<DialogueRow> rows = new ArrayList<>();
        boolean preview = presentation.hasPreview() && !presentation.nodeVisible();
        if (!presentation.nodeVisible()) {
            presentation.options().ifPresent(options -> {
                DialogueMenuOrdering.Selection<InteractionDialogueOptionsResponse.EventOption> selected =
                        DialogueMenuOrdering.split(options.eventOptions(),
                                option -> option.mode() == InteractionDialogueOptionsResponse.Mode.HIGHLIGHTED);
                selected.featured().ifPresent(option -> rows.add(new DialogueRow(option.prompt(),
                        new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.EVENT, option.id(), null),
                        true, -1)));
                options.continuation().ifPresent(prompt -> rows.add(new DialogueRow(
                        Component.translatable("gui.dialogue.resume_topic", prompt),
                        new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.RESUME, null, null))));
                for (DialogueTopicSection section : DialogueTopicSection.values()) {
                    List<InteractionDialogueOptionsResponse.EventOption> group = selected.remaining().stream()
                            .filter(option -> DialogueTopicSection.fromTopic(option.topic()) == section)
                            .toList();
                    boolean ambient = section == DialogueTopicSection.CONVERSATIONS && options.ambientAvailable();
                    if (group.isEmpty() && !ambient) {
                        continue;
                    }
                    boolean expanded = expandedSections.contains(section);
                    rows.add(new DialogueRow(Component.translatable(section.label),
                            new DialogueClick(DialogueClickKind.TOGGLE_SECTION, null, null, section.name()), false,
                            section.ordinal()));
                    if (!expanded) {
                        continue;
                    }
                    for (InteractionDialogueOptionsResponse.EventOption option : group) {
                        rows.add(DialogueRow.child(option.prompt(),
                                new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.EVENT, option.id(), null)));
                    }
                    if (ambient) {
                        rows.add(DialogueRow.child(Component.translatable(options.preview().isPresent()
                                ? "gui.dialogue.ambient_other" : "gui.dialogue.ambient"),
                                new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.AMBIENT, null, null)));
                    }
                }
            });
        }
        if (presentation.nodeVisible() || preview) {
            for (InteractionDialogueNodeResponse.Choice choice : presentation.visibleChoices()) {
                rows.add(new DialogueRow(choice.text(),
                        new DialogueClick(DialogueClickKind.CHOICE, null, null, choice.id())));
            }
            if (presentation.canAdvance()) {
                Component label = switch (presentation.advanceKind().orElseThrow()) {
                            case NEXT -> Component.translatable("gui.dialogue.continue");
                            case BACK_TO_TOPICS -> Component.translatable("gui.dialogue.finish");
                            case NONE -> throw new IllegalStateException("Non-advancing dialogue cannot render an advance action");
                        };
                rows.add(new DialogueRow(label, new DialogueClick(DialogueClickKind.ADVANCE, null, null, null)));
            } else if (presentation.reviewingHistory()) {
                // Keep the live replies visible for context, but read-only during history review.
                presentation.node().ifPresent(node -> {
                    for (InteractionDialogueNodeResponse.Choice choice : node.choices()) {
                        rows.add(new DialogueRow(choice.text(), null));
                    }
                    if (node.advanceKind() != DialogueEngine.AdvanceKind.NONE) {
                        rows.add(new DialogueRow(Component.translatable(node.advanceKind() == DialogueEngine.AdvanceKind.NEXT
                                ? "gui.dialogue.continue" : "gui.dialogue.finish"), null));
                    }
                });
            }
        }
        return rows;
    }

    private int dialoguePanelWidth() {
        return DialogueOverlayLayout.calculate(width, height, FOOTER_HEIGHT + 20, 0).menu().width();
    }

    private int dialoguePaperHeight(ClientHandlerImpl.DialoguePresentation presentation) {
        int bubbleWidth = dialoguePaperWidth(presentation);
        int lines = dialogueLines(presentation.paperFullLine(), bubbleWidth - dialoguePixels(32)).size();
        int padding = Config.getInstance().dialogueSpeechAboveChoices ? 40 : 48;
        int minimumHeight = Config.getInstance().dialogueSpeechAboveChoices ? 58 : 62;
        return dialoguePixels(Math.max(minimumHeight, lines * 10 + padding)) + dialogueHistoryHeight(presentation);
    }

    private int dialoguePaperWidth(ClientHandlerImpl.DialoguePresentation presentation) {
        if (Config.getInstance().dialogueSpeechAboveChoices) {
            return dialoguePanelWidth();
        }
        return DialogueOverlayLayout.calculate(width, height, dialogueFooterHeight() + 20, 1).bubble().width();
    }

    private int dialogueTargetHeight(ClientHandlerImpl.DialoguePresentation presentation) {
        int panelWidth = dialoguePanelWidth();
        List<DialogueRow> rows = dialogueRows(presentation);
        int listHeight = rows.isEmpty() ? 0 : rows.stream().mapToInt(row -> dialogueRowHeight(row, panelWidth)).sum() + 10;
        int desiredHeight = listHeight + dialogueFooterHeight();
        int maximumHeight = Math.max(dialogueFooterHeight() + 20, Math.min(height - 54, height * 3 / 5));
        return Math.min(maximumHeight, Math.max(dialogueFooterHeight() + 20, desiredHeight));
    }

    private void drawDialogueEventState(GuiGraphics context, int mouseX, int mouseY, float tickDelta) {
        dialogueHits.clear();
        dialogueListViewport = null;
        dialoguePassageViewport = null;
        if (!dialogueMode) {
            return;
        }
        ClientHandlerImpl.DialoguePresentation presentation = dialoguePresentation().orElse(null);
        if (presentation == null) {
            return;
        }
        OptionalLong token = presentation.offerToken();
        dialogueRenderedToken = token.orElse(Long.MIN_VALUE);
        if (token.isPresent() && (!dialogueTokenTracked || dialogueDisplayedToken != token.getAsLong())) {
            dialogueScroller.reset();
            dialoguePassageScroll = 0;
            dialogueKeyboardIndex = -1;
            dialogueDisplayedToken = token.getAsLong();
            dialogueTokenTracked = true;
        }

        InteractionDialogueNodeResponse.Node visibleNode = presentation.node().orElse(null);
        if (visibleNode != dialogueDisplayedNode) {
            dialogueScroller.reset();
            dialoguePassageScroll = 0;
            dialogueKeyboardIndex = -1;
            dialogueDisplayedNode = visibleNode;
        }
        // A shorter reply should shrink the menu again instead of retaining
        // the largest height reached anywhere in the ongoing conversation.
        int target = dialogueTargetHeight(presentation);
        long nowMillis = System.nanoTime() / 1_000_000L;
        int paperDesired = dialoguePaperHeight(presentation);
        DialogueOverlayLayout.Geometry settledLayout = DialogueOverlayLayout.calculate(width, height, target,
                paperDesired, dialoguePaperWidth(presentation));
        int settledViewportHeight = Math.max(1, settledLayout.menu().height() - dialogueFooterHeight() - 10);
        int panelHeight = dialogueHeight.update(settledLayout.menu().height(), nowMillis, dialogueScroller.isFocused());
        DialogueOverlayLayout.Geometry layout = DialogueOverlayLayout.calculate(width, height, panelHeight,
                paperDesired, dialoguePaperWidth(presentation));
        if (Config.getInstance().dialogueSpeechAboveChoices) {
            layout = DialogueOverlayLayout.speechAboveChoices(layout);
        }
        DialogueOverlayLayout.Rect menu = layout.menu();
        DialogueOverlayLayout.Rect bubble = layout.bubble();
        int left = menu.x();
        int top = menu.y();
        int bottom = menu.bottom();
        int panelWidth = menu.width();
        List<DialogueRow> rows = dialogueRows(presentation);
        int footerTop = bottom - dialogueFooterHeight();
        boolean positionToggleHovered = false;

        if (bubble.height() > 0) {
            DialogueSkin.drawFrame(context, DialogueSkin.PAPER, bubble.x(), bubble.y(),
                    bubble.width(), bubble.height());
            if (layout.canShowTail()) {
                DialogueOverlayLayout.Rect pointer = DialogueOverlayLayout.tailBounds(bubble);
                DialogueSkin.drawTail(context, pointer.x(), pointer.y());
            }
            int dividerY = bubble.y() + dialoguePixels(23);
            DialogueOverlayLayout.Rect speaker = DialogueOverlayLayout.speakerHeader(bubble, dialogueTextScale());
            if (speaker.width() > 0 && speaker.height() > 0) {
                context.fill(speaker.x(), speaker.y(), speaker.right(), speaker.bottom(), 0x336B482F);
                context.enableScissor(speaker.x(), speaker.y(), speaker.right(), speaker.bottom());
                drawDialogueText(context, villager.asEntity().getName().copy().withStyle(style -> style.withBold(true)),
                        speaker.x() + dialoguePixels(3), speaker.y() + dialoguePixels(3), 0xFF58331E, false);
                context.disableScissor();
            }
            context.fill(bubble.x() + 11, dividerY, bubble.right() - 11, dividerY + 1, 0xFFC49765);

            int textTop = dividerY + dialoguePixels(8);
            int textBottom = bubble.bottom() - dialoguePixels(12) - dialogueHistoryHeight(presentation);
            if (textBottom > textTop) {
                List<FormattedCharSequence> lines = dialogueLines(presentation.paperVisibleLine(), bubble.width() - dialoguePixels(32));
                int totalTextHeight = lines.size() * dialoguePixels(10);
                int scrollLimit = Math.max(0, totalTextHeight - (textBottom - textTop));
                dialoguePassageScroll = Mth.clamp(dialoguePassageScroll, 0, scrollLimit);
                dialoguePassageViewport = new DialogueViewport(bubble.x(), bubble.y(),
                        bubble.right(), bubble.bottom(), scrollLimit);
                context.enableScissor(bubble.x() + 11, textTop, bubble.right() - 8, textBottom);
                int y = textTop - dialoguePassageScroll;
                for (FormattedCharSequence line : lines) {
                    drawDialogueText(context, line, bubble.x() + dialoguePixels(16), y, INK, false);
                    y += dialoguePixels(10);
                }
                context.disableScissor();
                if (dialoguePassageScroll > 0) {
                    context.drawString(font, "▲", bubble.right() - 17, textTop, INK);
                }
                if (dialoguePassageScroll < scrollLimit) {
                    context.drawString(font, "▼", bubble.right() - 17, textBottom - 10, INK);
                }
            }
            drawDialogueHistory(context, presentation, bubble);
            positionToggleHovered = drawDialoguePositionToggle(context, bubble);
        }

        DialogueSkin.drawFrame(context, DialogueSkin.MENU, left, top, panelWidth, menu.height());

        int clipTop = top + 7;
        int clipBottom = footerTop - 3;
        if (!rows.isEmpty() && clipBottom > clipTop) {
            int rowsHeight = rows.stream().mapToInt(row -> dialogueRowHeight(row, panelWidth)).sum();
            int viewportHeight = clipBottom - clipTop;
            // Keep the vanilla scrollbar inside the frame, outside both row hitboxes and text.
            boolean needsScrollbar = rowsHeight > settledViewportHeight;
            int rowPanelWidth = panelWidth - (needsScrollbar ? 8 : 0);
            if (needsScrollbar) {
                rowsHeight = rows.stream().mapToInt(row -> dialogueRowHeight(row, rowPanelWidth)).sum();
            }
            dialogueScroller.layout(left + 8, clipTop, rowPanelWidth - 16, viewportHeight, rowsHeight,
                    settledViewportHeight);
            if (dialogueSectionToReveal != null) {
                int rowTop = 0;
                for (int i = 0; i < rows.size(); i++) {
                    DialogueRow row = rows.get(i);
                    int rowHeight = dialogueRowHeight(row, rowPanelWidth);
                    if (row.click() != null && row.click().kind() == DialogueClickKind.TOGGLE_SECTION
                            && dialogueSectionToReveal.name().equals(row.click().choiceId())) {
                        dialogueScroller.scrollToSection(rowTop);
                        break;
                    }
                    rowTop += rowHeight;
                }
                dialogueSectionToReveal = null;
            }
            int dialogueScroll = dialogueScroller.offset();
            int scrollLimit = needsScrollbar ? Math.max(0, rowsHeight - viewportHeight) : 0;
            int contentRight = dialogueScroller.getX() + dialogueScroller.getWidth();
            dialogueListViewport = new DialogueViewport(left + 8, clipTop,
                    left + panelWidth - 8, clipBottom, scrollLimit);
            if (needsScrollbar) {
                // The textured thumb is still vanilla; the shallow track is
                // part of the dark menu so it remains visible on touchpads.
                context.fill(contentRight, clipTop, contentRight + 8, clipBottom, 0xFF251916);
            }
            context.enableScissor(left + 8, clipTop, contentRight, clipBottom);
            int y = clipTop - dialogueScroll;
            for (DialogueRow row : rows) {
                y = drawDialogueRow(context, row, left, rowPanelWidth, y, clipTop, clipBottom);
            }
            context.disableScissor();
            if (dialogueScroll > 0) {
                context.drawString(font, "▲", contentRight - 10, clipTop, GOLD);
            }
            if (dialogueScroll < scrollLimit) {
                context.drawString(font, "▼", contentRight - 10, clipBottom - 10, GOLD);
            }
            dialogueScroller.render(context, mouseX, mouseY, tickDelta);
        }
        DialogueOverlayLayout.FooterLayout footer = DialogueOverlayLayout.footer(menu, dialogueFooterHeight());
        DialogueOverlayLayout.Rect divider = footer.divider();
        context.fill(divider.x(), divider.y(), divider.right(), divider.bottom(), FRAME);
        boolean active = presentation.nodeVisible();
        Component back = Component.literal("Q ").append(Component.translatable(
                active ? "gui.dialogue.topics" : "gui.dialogue.back_interaction"));
        DialogueOverlayLayout.Rect backAction = footer.back();
        drawDialogueAction(context, new DialogueRow(back, new DialogueClick(
                active ? DialogueClickKind.TOPICS : DialogueClickKind.BACK, null, null, null)),
                backAction.x(), backAction.y(), backAction.width(), backAction.height());
        DialogueOverlayLayout.Rect closeAction = footer.close();
        drawDialogueAction(context, new DialogueRow(Component.literal("Esc ")
                .append(Component.translatable("gui.dialogue.close")),
                new DialogueClick(DialogueClickKind.CLOSE, null, null, null)),
                closeAction.x(), closeAction.y(), closeAction.width(), closeAction.height());
        context.fill(closeAction.x(), closeAction.y(), closeAction.x() + 1, closeAction.bottom(), FRAME);
        if (positionToggleHovered) {
            context.renderTooltip(font, Component.translatable(Config.getInstance().dialogueSpeechAboveChoices
                    ? "gui.dialogue.position_corner" : "gui.dialogue.position_above"), mouseX, mouseY);
        }
    }

    private int dialogueHistoryHeight(ClientHandlerImpl.DialoguePresentation presentation) {
        return presentation.canReviewPrevious() || presentation.reviewingHistory()
                ? dialoguePixels(presentation.reviewingHistory() ? 39 : 24) : 0;
    }

    private void drawDialogueHistory(GuiGraphics context, ClientHandlerImpl.DialoguePresentation presentation,
                                     DialogueOverlayLayout.Rect bubble) {
        int historyHeight = dialogueHistoryHeight(presentation);
        if (historyHeight == 0) {
            return;
        }
        int left = bubble.x() + dialoguePixels(16);
        int right = bubble.right() - dialoguePixels(16);
        int top = bubble.bottom() - historyHeight;
        int controlHeight = dialoguePixels(16);
        context.fill(left, top - dialoguePixels(4), right, top - dialoguePixels(4) + 1, 0xFFBC8248);
        Component previous = Component.literal("‹ ").append(Component.translatable("gui.dialogue.previous"));
        Component next = Component.translatable("gui.dialogue.next").copy().append(" ›");
        int previousWidth = dialoguePixels(font.width(previous) + 4);
        int nextWidth = dialoguePixels(font.width(next) + 4);
        drawSpeechControl(context, previous, left, top, previousWidth, controlHeight,
                new DialogueClick(DialogueClickKind.PREVIOUS, null, null, null), presentation.canReviewPrevious());
        drawSpeechControl(context, next, right - nextWidth, top, nextWidth, controlHeight,
                new DialogueClick(DialogueClickKind.REVIEW_NEXT, null, null, null), presentation.reviewingHistory());
        if (presentation.reviewingHistory()) {
            Component current = Component.translatable("gui.dialogue.return_current");
            int controlWidth = Math.min(right - left, dialoguePixels(font.width(current) + 8));
            drawSpeechControl(context, current, left + (right - left - controlWidth) / 2,
                    top + controlHeight, controlWidth, controlHeight,
                    new DialogueClick(DialogueClickKind.REVIEW_LATEST, null, null, null), true);
        }
    }

    private void drawSpeechControl(GuiGraphics context, Component label, int x, int y, int w, int h,
                                   DialogueClick click, boolean enabled) {
        boolean hovered = enabled && hoveringOver(x, y, w, h);
        boolean focused = enabled && dialogueKeyboardIndex == dialogueHits.size();
        if (hovered || focused) {
            context.renderOutline(x, y, w, h, 0xFFC49765);
        }
        int textX = x + (w - dialoguePixels(font.width(label))) / 2;
        drawDialogueText(context, label, textX, y + (h - dialoguePixels(font.lineHeight - 1)) / 2,
                enabled ? INK : 0xFF967A56, false);
        if (enabled) {
            dialogueHits.add(new DialogueHit(x, y, x + w, y + h, click));
        }
    }

    private boolean drawDialoguePositionToggle(GuiGraphics context, DialogueOverlayLayout.Rect bubble) {
        int size = dialoguePixels(15);
        int x = bubble.right() - dialoguePixels(12) - size;
        int y = bubble.y() + dialoguePixels(7);
        drawSpeechControl(context, Component.literal("≡"), x, y, size, size,
                new DialogueClick(DialogueClickKind.TOGGLE_POSITION, null, null, null), true);
        return hoveringOver(x, y, size, size);
    }

    private float dialogueTextScale() {
        return DialogueOverlayLayout.contentScale(width, height);
    }

    private int dialoguePixels(int pixels) {
        return Math.round(pixels * dialogueTextScale());
    }

    private int dialogueFooterHeight() {
        return dialoguePixels(32);
    }

    private List<FormattedCharSequence> dialogueLines(Component text, int availableWidth) {
        return font.split(text, Math.max(1, (int) (availableWidth / dialogueTextScale())));
    }

    private void drawDialogueText(GuiGraphics context, Component text, float x, float y, int colour, boolean shadow) {
        drawDialogueText(context, text.getVisualOrderText(), x, y, colour, shadow);
    }

    private void drawDialogueText(GuiGraphics context, FormattedCharSequence text, float x, float y,
                                  int colour, boolean shadow) {
        context.pose().pushPose();
        context.pose().translate(x, y, 0);
        context.pose().scale(dialogueTextScale(), dialogueTextScale(), 1);
        context.drawString(font, text, 0, 0, colour, shadow);
        context.pose().popPose();
    }

    private int dialogueRowTextWidth(DialogueRow row, int panelWidth) {
        return DialogueRowLayout.insets(panelWidth, dialogueTextScale(), row.kind()).textWidth();
    }

    private int dialogueRowHeight(DialogueRow row, int panelWidth) {
        int lines = dialogueLines(row.text(), dialogueRowTextWidth(row, panelWidth)).size();
        return DialogueRowLayout.height(dialogueTextScale(), lines, row.kind());
    }

    private int drawDialogueRow(GuiGraphics context, DialogueRow row,
                                int left, int panelWidth, int y, int clipTop, int clipBottom) {
        DialogueRowLayout.Insets insets = DialogueRowLayout.insets(panelWidth, dialogueTextScale(), row.kind());
        List<FormattedCharSequence> lines = dialogueLines(row.text(), dialogueRowTextWidth(row, panelWidth));
        int rowHeight = dialogueRowHeight(row, panelWidth);
        int hitTop = Math.max(y, clipTop);
        int hitBottom = Math.min(y + rowHeight - 2, clipBottom);
        boolean clickable = row.click() != null && hitBottom > hitTop;
        boolean hover = clickable && hoveringOver(left + 8, hitTop, panelWidth - 16, hitBottom - hitTop);
        boolean focused = clickable && dialogueKeyboardIndex == dialogueHits.size();
        if (row.featured()) {
            DialogueSkin.drawFrame(context, DialogueSkin.FEATURED, left + 8, y + 1,
                    panelWidth - 16, rowHeight - 2);
            if ((hover || focused) && hitBottom - hitTop > 4) {
                context.renderOutline(left + 9, hitTop + 1, panelWidth - 18,
                        hitBottom - hitTop - 2, 0xFFFFD88B);
            }
        } else if (clickable) {
            int surfaceLeft = left + 8;
            int surfaceRight = left + panelWidth - 8;
            int surfaceTop = y + 1;
            int surfaceBottom = y + rowHeight - 2;
            boolean highlighted = hover || focused;
            int fill = row.iconIndex() >= 0 ? 0xFF362318 : 0xFF422B1C;
            context.fill(surfaceLeft, surfaceTop, surfaceRight, surfaceBottom, fill);
            if (row.iconIndex() >= 0 || highlighted) {
                int border = highlighted ? 0xFFD7A76B : 0xFF71513A;
                context.renderOutline(surfaceLeft, surfaceTop, surfaceRight - surfaceLeft,
                        surfaceBottom - surfaceTop, border);
            }
        }
        int iconSize = dialoguePixels(12);
        int glyphHeight = dialoguePixels(font.lineHeight - 1);
        int lineStep = dialoguePixels(10);
        // Centre against the row itself: the featured frame starts at y + 1 and ends
        // at y + rowHeight - 1, so its centre is also y + rowHeight / 2.
        // Use the rounded line step actually drawn below, not a separately rounded total.
        int textHeight = glyphHeight + (lines.size() - 1) * lineStep;
        int lineY = y + (rowHeight - textHeight) / 2;
        int glyphY = y + (rowHeight - glyphHeight) / 2;
        int symbolY = y + (rowHeight - iconSize) / 2;
        // A partially clipped first row used to show only a stray coloured pixel
        // of its symbol when the user scrolled a small amount.
        if (symbolY >= clipTop && symbolY + iconSize <= clipBottom) {
            if (row.featured() || row.iconIndex() >= 0) {
                int iconX = left + insets.iconX();
                if (row.featured()) {
                    DialogueSkin.drawFeaturedIcon(context, iconX, symbolY, iconSize);
                } else if (row.iconIndex() == DialogueTopicSection.RELATIONSHIPS.ordinal()) {
                    // Use the same MCA heart artwork shown in the villager status indicators.
                    context.blit(ICON_TEXTURES, iconX, symbolY, iconSize, iconSize,
                            84.0f, 4.0f, 9, 9, 256, 256);
                } else {
                    DialogueSkin.drawIcon(context, row.iconIndex(), iconX, symbolY, iconSize);
                }
            }
        }
        for (FormattedCharSequence line : lines) {
            drawDialogueText(context, line, left + insets.textX(), lineY,
                    !clickable ? 0xFFAA937A : hover || focused ? GOLD : CREAM, true);
            lineY += lineStep;
        }
        if (row.featured() || row.iconIndex() >= 0) {
            boolean expanded = row.click() != null && row.click().kind() == DialogueClickKind.TOGGLE_SECTION
                    && expandedSections.contains(DialogueTopicSection.valueOf(row.click().choiceId()));
            drawDialogueText(context, Component.literal(expanded ? "▾" : "▸"),
                    left + insets.trailingX(),
                    glyphY,
                    hover || focused ? GOLD : CREAM, false);
        }
        if (clickable) {
            dialogueHits.add(new DialogueHit(left + 8, hitTop, left + panelWidth - 8, hitBottom, row.click()));
        }
        return y + rowHeight;
    }

    private void drawDialogueAction(GuiGraphics context, DialogueRow row, int x, int y, int w, int h) {
        boolean hover = hoveringOver(x, y, w, h);
        boolean focused = dialogueKeyboardIndex == dialogueHits.size();
        if (hover || focused) {
            context.renderOutline(x + 3, y + 1, w - 6, h - 4, 0xFFD7A76B);
        }
        String fullText = row.text().getString();
        int space = fullText.indexOf(' ');
        String key = space > 0 ? fullText.substring(0, space) : fullText;
        Component label = row.text().getSiblings().isEmpty()
                ? row.text()
                : row.text().getSiblings().getFirst();
        int keyWidth = dialoguePixels(Math.max(17, font.width(key) + 8));
        int keyHeight = dialoguePixels(16);
        int gap = dialoguePixels(7);
        int groupWidth = keyWidth + gap + dialoguePixels(font.width(label));
        int opticalOffset = row.click().kind() == DialogueClickKind.CLOSE ? 0 : dialoguePixels(3);
        int keyX = x + Math.max(dialoguePixels(4), (w - groupWidth) / 2 + opticalOffset);
        int keyY = y + (h - keyHeight) / 2;
        DialogueSkin.drawKeycap(context, keyX, keyY, keyWidth, keyHeight);
        int textY = y + (h - dialoguePixels(font.lineHeight - 1)) / 2;
        float textScale = dialogueTextScale();
        float keyTextX = keyX + (keyWidth - font.width(key) * textScale) / 2.0f;
        float keyTextY = keyY + (keyHeight - (font.lineHeight - 1) * textScale) / 2.0f;
        drawDialogueText(context, Component.literal(key), keyTextX, keyTextY, CREAM, true);
        context.enableScissor(keyX + keyWidth + gap, y, x + w - 3, y + h);
        drawDialogueText(context, label, keyX + keyWidth + gap, textY,
                hover || focused ? GOLD : CREAM, true);
        context.disableScissor();
        dialogueHits.add(new DialogueHit(x, y, x + w, y + h, row.click()));
    }

    private boolean handleDialogueClick(DialogueClick click) {
        ClientHandlerImpl.DialoguePresentation presentation = dialoguePresentation().orElse(null);
        if (presentation == null) {
            return false;
        }
        if (click.kind() == DialogueClickKind.BACK) {
            leaveDialogueMode();
            return true;
        }
        if (click.kind() == DialogueClickKind.CLOSE) {
            onClose();
            return true;
        }
        if (click.kind() == DialogueClickKind.TOGGLE_SECTION) {
            DialogueTopicSection section = DialogueTopicSection.valueOf(click.choiceId());
            if (!expandedSections.remove(section)) {
                expandedSections.add(section);
                dialogueSectionToReveal = section;
            } else {
                dialogueSectionToReveal = null;
            }
            dialogueHits.clear();
            dialogueKeyboardIndex = -1;
            return true;
        }
        if (click.kind() == DialogueClickKind.TOGGLE_POSITION) {
            Config config = Config.getInstance();
            config.dialogueSpeechAboveChoices = !config.dialogueSpeechAboveChoices;
            config.save();
            dialogueHeight.reset();
            dialogueScroller.reset();
            dialogueHits.clear();
            dialogueKeyboardIndex = -1;
            return true;
        }
        if (click.kind() == DialogueClickKind.PREVIOUS) {
            if (presentation.reviewPrevious()) {
                dialoguePassageScroll = 0;
                return true;
            }
            return false;
        }
        if (click.kind() == DialogueClickKind.REVIEW_LATEST) {
            if (presentation.reviewLatest()) {
                dialoguePassageScroll = 0;
                return true;
            }
            return false;
        }
        if (click.kind() == DialogueClickKind.REVIEW_NEXT) {
            if (presentation.reviewNext()) {
                dialoguePassageScroll = 0;
                return true;
            }
            return false;
        }
        if (click.kind() == DialogueClickKind.TOPICS) {
            requestDialogueTopics();
            return true;
        }
        if (click.kind() == DialogueClickKind.ADVANCE) {
            if (!presentation.canAdvance() || presentation.offerToken().isEmpty()) {
                return false;
            }
            if (presentation.hasPreview()) {
                if (!presentation.canEngagePreview()) {
                    return false;
                }
                presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);
            }
            long token = presentation.offerToken().orElseThrow();
            Network.sendToServer(new InteractionDialogueAdvanceMessage(token));
            return true;
        }
        if (click.kind() == DialogueClickKind.CHOICE) {
            if (presentation.offerToken().isEmpty()
                    || presentation.visibleChoices().stream().noneMatch(choice -> choice.id().equals(click.choiceId()))) {
                return false;
            }
            if (presentation.hasPreview()) {
                if (!presentation.canEngagePreview()) {
                    return false;
                }
                presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);
            }
            long token = presentation.offerToken().orElseThrow();
            Network.sendToServer(new InteractionDialogueChoiceMessage(token, click.choiceId()));
            return true;
        }

        InteractionDialogueOptionsResponse options = presentation.options().orElse(null);
        if (options == null
                || presentation.offerToken().isEmpty()
                || !selectionStillOffered(options, click)) {
            return false;
        }
        long token = presentation.offerToken().orElseThrow();
        presentation.markSelectionRequested(click.selection());
        Network.sendToServer(new InteractionDialogueSelectMessage(token, click.selection(), Optional.ofNullable(click.eventId())));
        return true;
    }

    private static boolean selectionStillOffered(InteractionDialogueOptionsResponse options, DialogueClick click) {
        return switch (click.selection()) {
            case RESUME -> options.continuation().isPresent() && click.eventId() == null;
            case EVENT -> click.eventId() != null && options.eventOptions().stream().anyMatch(option -> option.id().equals(click.eventId()));
            case AMBIENT -> click.eventId() == null && options.ambientAvailable();
        };
    }

    private enum DialogueClickKind {
        SELECT,
        CHOICE,
        ADVANCE,
        PREVIOUS,
        REVIEW_NEXT,
        REVIEW_LATEST,
        TOGGLE_SECTION,
        TOGGLE_POSITION,
        TOPICS,
        BACK,
        CLOSE
    }

    private record DialogueClick(
            DialogueClickKind kind,
            DialogueEngine.DialogueSelection selection,
            ResourceLocation eventId,
            String choiceId
    ) { }

    private record DialogueRow(Component text, DialogueClick click, boolean featured, int iconIndex,
                               boolean nested) {
        DialogueRow(Component text, DialogueClick click) {
            this(text, click, false, -1, false);
        }

        DialogueRow(Component text, DialogueClick click, boolean featured, int iconIndex) {
            this(text, click, featured, iconIndex, false);
        }

        DialogueRowLayout.Kind kind() {
            return featured ? DialogueRowLayout.Kind.FEATURED
                    : iconIndex >= 0 ? DialogueRowLayout.Kind.CATEGORY
                    : nested ? DialogueRowLayout.Kind.CHILD : DialogueRowLayout.Kind.ORDINARY;
        }

        static DialogueRow child(Component text, DialogueClick click) {
            return new DialogueRow(text, click, false, -1, true);
        }
    }

    private record DialogueHit(int left, int top, int right, int bottom, DialogueClick click) {
    }

    private record DialogueViewport(int left, int top, int right, int bottom, int limit) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
