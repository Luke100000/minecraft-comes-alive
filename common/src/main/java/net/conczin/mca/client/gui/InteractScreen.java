package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.ClientProxy;
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
    private static final int PAPER = 0xFFE6C995;
    private static final int INK = 0xFF292018;
    private static final int MENU = 0xEE201917;
    private static final int FRAME = 0xFF67452F;
    private static final int GOLD = 0xFFD7A34D;
    private static final int CREAM = 0xFFEEE2D2;
    private static final int FOOTER_HEIGHT = 27;
    private static final long HEIGHT_TWEEN_MILLIS = 120L;
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
    private final EnumSet<DialogueTopicSection> expandedSections =
            EnumSet.of(DialogueTopicSection.CONVERSATIONS, DialogueTopicSection.RELATIONSHIPS);
    private final List<DialogueHit> dialogueHits = new ArrayList<>();
    private int dialogueScroll;
    private int dialogueRowsHeight;
    private int dialogueViewportHeight;
    private int dialoguePassageScroll;
    private DialogueViewport dialogueListViewport;
    private DialogueViewport dialoguePassageViewport;
    private long dialogueDisplayedToken;
    private boolean dialogueTokenTracked;
    private long dialogueRenderedToken;
    private float dialogueHeightStart;
    private int dialogueHeightTarget;
    private long dialogueHeightStartedAt;
    private int dialogueStoryHeight;
    private boolean dialogueHeightInitialized;
    private boolean qHeld;
    private int dialogueKeyboardIndex = -1;

    public InteractScreen(VillagerLike<?> villager, UUID interactionId) {
        super(Component.literal("Interact"));
        this.villager = villager;
        this.interactionId = Objects.requireNonNull(interactionId, "interactionId");
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
        dialogueHits.clear();
        dialogueHeightInitialized = false;
        dialogueStoryHeight = 0;
        Network.sendToServer(new GetInteractDataRequest(villager.asEntity().getId()));
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
        drawDialogueEventState(context);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dialogueMode) {
            dialogueHits.clear();
            if (dialogueListViewport != null && dialogueListViewport.contains(x, y)) {
                int limit = Math.max(0, dialogueRowsHeight - dialogueViewportHeight);
                int delta = (int) Math.signum(dy) * 24;
                dialogueScroll = Math.max(0, Math.min(limit, dialogueScroll - delta));
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
            Network.sendToServer(new InteractionVillagerMessage("gift", villager.asEntity().getUUID()));
            return true;
        } else {
            return false;
        }
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
        dialogueMode = true;
        if (showOpening) {
            expandedSections.clear();
            expandedSections.add(DialogueTopicSection.CONVERSATIONS);
            expandedSections.add(DialogueTopicSection.RELATIONSHIPS);
        }
        dialogueHits.clear();
        dialogueScroll = 0;
        dialoguePassageScroll = 0;
        dialogueKeyboardIndex = -1;
        dialogueHeightInitialized = false;
        dialogueStoryHeight = 0;
        clearWidgets();
        dialoguePresentation().ifPresent(ClientHandlerImpl.DialoguePresentation::beginRequest);
        Network.sendToServer(new InteractionDialogueBeginMessage(villager.asEntity().getUUID(), showOpening));
    }

    public void leaveDialogueMode() {
        leaveDialogueMode(true);
    }

    public void leaveDialogueMode(boolean notifyServer) {
        ClientHandlerImpl.DialoguePresentation presentation = dialoguePresentation().orElse(null);
        UUID sessionId = presentation == null ? Util.NIL_UUID : presentation.sessionId().orElse(Util.NIL_UUID);
        long offerToken = presentation == null ? 0L : presentation.offerToken().orElse(0L);
        dialogueMode = false;
        dialogueHits.clear();
        dialogueScroll = 0;
        dialoguePassageScroll = 0;
        dialogueTokenTracked = false;
        dialogueKeyboardIndex = -1;
        dialogueHeightInitialized = false;
        dialogueStoryHeight = 0;
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
                Network.sendToServer(new InteractionVillagerMessage(id.replace("gui.button.", ""), villager.asEntity().getUUID()));
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
                options.continuation().ifPresent(prompt -> rows.add(new DialogueRow(
                        Component.translatable("gui.dialogue.resume_topic", prompt),
                        new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.RESUME, null, null))));
                for (DialogueTopicSection section : DialogueTopicSection.values()) {
                    List<InteractionDialogueOptionsResponse.EventOption> group = options.eventOptions().stream()
                            .filter(option -> DialogueTopicSection.fromTopic(option.topic()) == section)
                            .toList();
                    boolean ambient = section == DialogueTopicSection.CONVERSATIONS && options.ambientAvailable();
                    if (group.isEmpty() && !ambient) {
                        continue;
                    }
                    boolean expanded = expandedSections.contains(section);
                    rows.add(new DialogueRow(Component.translatable(section.label).copy()
                            .append(expanded ? " ▾" : " ▸"),
                            new DialogueClick(DialogueClickKind.TOGGLE_SECTION, null, null, section.name())));
                    if (!expanded) {
                        continue;
                    }
                    for (InteractionDialogueOptionsResponse.EventOption option : group) {
                        Component label = Component.literal(option.mode() == InteractionDialogueOptionsResponse.Mode.HIGHLIGHTED
                                ? "  ★ " : "    ").append(option.prompt());
                        rows.add(new DialogueRow(label,
                                new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.EVENT, option.id(), null)));
                    }
                    if (ambient) {
                        rows.add(new DialogueRow(Component.literal("    ").append(Component.translatable(options.preview().isPresent()
                                ? "gui.dialogue.ambient_other" : "gui.dialogue.ambient")),
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
            if (!preview && (presentation.canReviewPrevious() || presentation.reviewingHistory())) {
                rows.add(new DialogueRow(Component.translatable("gui.dialogue.history"), null));
            }
            if (!preview && presentation.canReviewPrevious()) {
                rows.add(new DialogueRow(Component.translatable("gui.dialogue.earlier_line"),
                        new DialogueClick(DialogueClickKind.PREVIOUS, null, null, null)));
            }
            if (presentation.canAdvance() || presentation.reviewingHistory()) {
                Component label = presentation.reviewingHistory() ? Component.translatable("gui.dialogue.latest_line")
                        : switch (presentation.advanceKind().orElseThrow()) {
                            case NEXT -> Component.translatable("gui.dialogue.continue");
                            case BACK_TO_TOPICS -> Component.translatable("gui.dialogue.finish");
                            case NONE -> throw new IllegalStateException("Non-advancing dialogue cannot render an advance action");
                        };
                rows.add(new DialogueRow(label, new DialogueClick(
                        presentation.reviewingHistory() ? DialogueClickKind.REVIEW_LATEST : DialogueClickKind.ADVANCE,
                        null, null, null)));
            }
        }
        return rows;
    }

    private int dialoguePanelWidth() {
        return Math.min(Math.max(1, width - 24), 560);
    }

    private int dialoguePaperHeight(ClientHandlerImpl.DialoguePresentation presentation, int panelWidth) {
        if (!presentation.nodeVisible() && !presentation.hasPreview()) {
            return 0;
        }
        return Math.max(43, font.split(presentation.fullLine(), Math.max(1, panelWidth - 28)).size() * 10 + 22);
    }

    private int dialogueTargetHeight(ClientHandlerImpl.DialoguePresentation presentation) {
        int panelWidth = dialoguePanelWidth();
        List<DialogueRow> rows = dialogueRows(presentation);
        int listHeight = rows.isEmpty() ? 0 : rows.stream().mapToInt(row -> dialogueRowHeight(row, panelWidth)).sum() + 10;
        int desiredHeight = dialoguePaperHeight(presentation, panelWidth) + listHeight + FOOTER_HEIGHT;
        int maximumHeight = Math.max(FOOTER_HEIGHT + 20, Math.min(height - 54, height * 3 / 5));
        return Math.min(maximumHeight, Math.max(FOOTER_HEIGHT + 20, desiredHeight));
    }

    private float animatedDialogueHeight(long nowMillis) {
        float progress = Mth.clamp((nowMillis - dialogueHeightStartedAt) / (float) HEIGHT_TWEEN_MILLIS, 0.0f, 1.0f);
        float remaining = 1.0f - progress;
        return Mth.lerp(1.0f - remaining * remaining * remaining, dialogueHeightStart, dialogueHeightTarget);
    }

    private void drawDialogueEventState(GuiGraphics context) {
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
            dialogueScroll = 0;
            dialoguePassageScroll = 0;
            dialogueKeyboardIndex = -1;
            dialogueDisplayedToken = token.getAsLong();
            dialogueTokenTracked = true;
        }

        int panelWidth = dialoguePanelWidth();
        int left = (width - panelWidth) / 2;
        int bottom = height - 34;
        int target = dialogueTargetHeight(presentation);
        if (!presentation.nodeVisible()) {
            dialogueStoryHeight = 0;
        } else {
            dialogueStoryHeight = Math.max(dialogueStoryHeight, target);
            target = dialogueStoryHeight;
        }
        long nowMillis = System.nanoTime() / 1_000_000L;
        if (!dialogueHeightInitialized) {
            dialogueHeightStart = FOOTER_HEIGHT + 20;
            dialogueHeightTarget = FOOTER_HEIGHT + 20;
            dialogueHeightStartedAt = nowMillis;
            dialogueHeightInitialized = true;
        }
        if (target != dialogueHeightTarget) {
            dialogueHeightStart = animatedDialogueHeight(nowMillis);
            dialogueHeightTarget = target;
            dialogueHeightStartedAt = nowMillis;
        }
        int panelHeight = Math.round(animatedDialogueHeight(nowMillis));
        int top = bottom - panelHeight;
        List<DialogueRow> rows = dialogueRows(presentation);
        int paperDesired = dialoguePaperHeight(presentation, panelWidth);
        int availableContent = Math.max(0, panelHeight - FOOTER_HEIGHT);
        int paperHeight = paperDesired == 0 ? 0 : Math.min(availableContent,
                Math.min(paperDesired, Math.max(20, availableContent - (rows.isEmpty() ? 0 : 26))));
        int footerTop = bottom - FOOTER_HEIGHT;
        int menuTop = top + paperHeight;

        context.fill(left - 2, top - 2, left + panelWidth + 2, bottom + 2, FRAME);
        if (paperHeight > 0) {
            context.fill(left, top, left + panelWidth, menuTop, PAPER);
            context.fill(left + 5, top + 4, left + 7, menuTop - 3, GOLD);
            int textTop = top + 9;
            int textBottom = menuTop - 6;
            if (textBottom > textTop) {
                List<FormattedCharSequence> lines = font.split(presentation.visibleLine(), Math.max(1, panelWidth - 28));
                int totalTextHeight = lines.size() * 10;
                int scrollLimit = Math.max(0, totalTextHeight - (textBottom - textTop));
                dialoguePassageScroll = Mth.clamp(dialoguePassageScroll, 0, scrollLimit);
                dialoguePassageViewport = new DialogueViewport(left, top,
                        left + panelWidth, menuTop, scrollLimit);
                context.enableScissor(left + 10, textTop, left + panelWidth - 8, textBottom);
                int y = textTop - dialoguePassageScroll;
                for (FormattedCharSequence line : lines) {
                    context.drawString(font, line, left + 14, y, INK, false);
                    y += 10;
                }
                context.disableScissor();
                if (dialoguePassageScroll > 0) {
                    context.drawString(font, "▲", left + panelWidth - 17, textTop, INK);
                }
                if (dialoguePassageScroll < scrollLimit) {
                    context.drawString(font, "▼", left + panelWidth - 17, textBottom - 10, INK);
                }
            }
        }
        context.fill(left, menuTop, left + panelWidth, bottom, MENU);
        if (paperHeight > 0) {
            context.fill(left, menuTop, left + panelWidth, menuTop + 2, FRAME);
        }

        int clipTop = menuTop + 5;
        int clipBottom = footerTop - 3;
        if (!rows.isEmpty() && clipBottom > clipTop) {
            dialogueRowsHeight = rows.stream().mapToInt(row -> dialogueRowHeight(row, panelWidth)).sum();
            dialogueViewportHeight = clipBottom - clipTop;
            int scrollLimit = Math.max(0, dialogueRowsHeight - dialogueViewportHeight);
            dialogueScroll = Mth.clamp(dialogueScroll, 0, scrollLimit);
            dialogueListViewport = new DialogueViewport(left + 8, clipTop,
                    left + panelWidth - 8, clipBottom, scrollLimit);
            context.enableScissor(left + 8, clipTop, left + panelWidth - 8, clipBottom);
            int y = clipTop - dialogueScroll;
            for (DialogueRow row : rows) {
                y = drawDialogueRow(context, row, left, panelWidth, y, clipTop, clipBottom);
            }
            context.disableScissor();
            if (dialogueScroll > 0) {
                context.drawString(font, "▲", left + panelWidth - 18, clipTop, GOLD);
            }
            if (dialogueScroll < scrollLimit) {
                context.drawString(font, "▼", left + panelWidth - 18, clipBottom - 10, GOLD);
            }
        }
        context.fill(left, footerTop, left + panelWidth, footerTop + 1, FRAME);
        boolean active = presentation.nodeVisible();
        Component back = Component.literal("Q ").append(Component.translatable(
                active ? "gui.dialogue.topics" : "gui.dialogue.back_interaction"));
        drawDialogueAction(context, new DialogueRow(back, new DialogueClick(
                active ? DialogueClickKind.TOPICS : DialogueClickKind.BACK, null, null, null)),
                left + 6, footerTop + 3, (panelWidth - 18) / 2, 20);
        drawDialogueAction(context, new DialogueRow(Component.literal("Esc ")
                .append(Component.translatable("gui.dialogue.close")),
                new DialogueClick(DialogueClickKind.CLOSE, null, null, null)),
                left + panelWidth / 2 + 3, footerTop + 3, (panelWidth - 18) / 2, 20);
    }

    private int dialogueRowHeight(DialogueRow row, int panelWidth) {
        return Math.max(22, font.split(row.text(), Math.max(1, panelWidth - 30)).size() * 10 + 10);
    }

    private int drawDialogueRow(GuiGraphics context, DialogueRow row,
                                int left, int panelWidth, int y, int clipTop, int clipBottom) {
        List<FormattedCharSequence> lines = font.split(row.text(), Math.max(1, panelWidth - 30));
        int rowHeight = dialogueRowHeight(row, panelWidth);
        int hitTop = Math.max(y, clipTop);
        int hitBottom = Math.min(y + rowHeight - 2, clipBottom);
        boolean clickable = row.click() != null && hitBottom > hitTop;
        boolean hover = clickable && hoveringOver(left + 8, hitTop, panelWidth - 16, hitBottom - hitTop);
        boolean focused = clickable && dialogueKeyboardIndex == dialogueHits.size();
        if (clickable) {
            context.fill(left + 8, hitTop, left + panelWidth - 8, hitBottom,
                    hover || focused ? 0xCC67452F : 0x88402D24);
            if (hover || focused) {
                context.fill(left + 8, hitTop, left + 11, hitBottom, GOLD);
            }
        }
        int textHeight = (lines.size() - 1) * 10 + font.lineHeight;
        int lineY = y + (rowHeight - textHeight) / 2;
        for (FormattedCharSequence line : lines) {
            context.drawString(font, line, left + 16, lineY,
                    hover || focused ? GOLD : CREAM);
            lineY += 10;
        }
        if (clickable) {
            dialogueHits.add(new DialogueHit(left + 8, hitTop, left + panelWidth - 8, hitBottom, row.click()));
        }
        return y + rowHeight;
    }

    private void drawDialogueAction(GuiGraphics context, DialogueRow row, int x, int y, int w, int h) {
        boolean hover = hoveringOver(x, y, w, h);
        boolean focused = dialogueKeyboardIndex == dialogueHits.size();
        context.fill(x, y, x + w, y + h, hover || focused ? 0xCC67452F : 0x9935261D);
        if (hover || focused) {
            context.fill(x, y, x + 2, y + h, GOLD);
        }
        context.enableScissor(x + 4, y, x + w - 4, y + h);
        context.drawString(font, row.text(), x + 6, y + (h - font.lineHeight) / 2,
                hover || focused ? GOLD : CREAM);
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
            }
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
        REVIEW_LATEST,
        TOGGLE_SECTION,
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

    private record DialogueRow(Component text, DialogueClick click) {
    }

    private record DialogueHit(int left, int top, int right, int bottom, DialogueClick click) {
    }

    private record DialogueViewport(int left, int top, int right, int bottom, int limit) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
