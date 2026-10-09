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
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class InteractScreen extends AbstractDynamicScreen {
    public static final ResourceLocation ICON_TEXTURES = MCA.locate("textures/gui.png");
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
    private final List<DialogueHit> dialogueHits = new ArrayList<>();
    private int dialogueScroll;
    private int dialogueRowsHeight;
    private int dialogueViewportHeight;
    private int dialoguePassageScroll;
    private int dialoguePreviewReplyScroll;
    private DialogueViewport dialogueListViewport;
    private DialogueViewport dialoguePassageViewport;
    private DialogueViewport dialoguePreviewReplyViewport;
    private long dialogueDisplayedToken;
    private boolean dialogueTokenTracked;
    private long dialogueRenderedToken;

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
            } else if (dialoguePreviewReplyViewport != null && dialoguePreviewReplyViewport.contains(x, y)) {
                int delta = (int) Math.signum(dy) * 20;
                dialoguePreviewReplyScroll = Math.max(0, Math.min(
                        dialoguePreviewReplyViewport.limit(), dialoguePreviewReplyScroll - delta));
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
        // Hotkey to leave gift mode
        if (keyChar == GLFW.GLFW_KEY_ESCAPE) {
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
        dialogueHits.clear();
        dialogueScroll = 0;
        dialoguePassageScroll = 0;
        dialoguePreviewReplyScroll = 0;
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
        dialoguePreviewReplyScroll = 0;
        dialogueTokenTracked = false;
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

    private Optional<ClientHandlerImpl.DialoguePresentation> dialoguePresentation() {
        return ClientProxy.getNetworkHandler() instanceof ClientHandlerImpl handler
                ? Optional.of(handler.dialoguePresentation())
                : Optional.empty();
    }

    private void drawDialogueEventState(GuiGraphics context) {
        dialogueHits.clear();
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
            dialoguePreviewReplyScroll = 0;
            dialogueDisplayedToken = token.getAsLong();
            dialogueTokenTracked = true;
        }

        dialogueListViewport = null;
        dialoguePassageViewport = null;
        dialoguePreviewReplyViewport = null;
        if (presentation.nodeVisible()) {
            drawDialogueNode(context, presentation);
        } else {
            int menuBottom = height - 34;
            if (presentation.hasPreview() && width < 480) {
                int passageHeight = Math.max(62, Math.min(190, (height - 60) / 2));
                menuBottom = Math.max(54, menuBottom - passageHeight - 12);
            }
            int finalMenuBottom = menuBottom;
            presentation.options().ifPresent(options -> drawDialogueOptions(context, options, finalMenuBottom));
            if (presentation.hasPreview()) {
                drawDialogueNode(context, presentation);
            }
        }
    }

    private void drawDialogueOptions(GuiGraphics context, InteractionDialogueOptionsResponse options, int maxBottom) {
        List<DialogueRow> rows = new ArrayList<>();
        options.continuation().ifPresent(prompt -> rows.add(new DialogueRow(
                prompt,
                new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.RESUME, null, null)
        )));
        options.eventOptions().stream()
                .filter(option -> option.mode() == InteractionDialogueOptionsResponse.Mode.HIGHLIGHTED)
                .forEach(option -> rows.add(new DialogueRow(
                        option.prompt(),
                        new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.EVENT, option.id(), null)
                )));

        List<InteractionDialogueOptionsResponse.EventOption> ask = options.eventOptions().stream()
                .filter(option -> option.mode() == InteractionDialogueOptionsResponse.Mode.ASK)
                .toList();
        if (!ask.isEmpty()) {
            rows.add(new DialogueRow(Component.translatable("gui.dialogue.ask"), null));
            ask.forEach(option -> rows.add(new DialogueRow(
                    option.prompt(),
                    new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.EVENT, option.id(), null)
            )));
        }
        if (options.ambientAvailable()) {
            rows.add(new DialogueRow(
                    Component.translatable(options.preview().isPresent()
                            ? "gui.dialogue.ambient_other" : "gui.dialogue.ambient"),
                    new DialogueClick(DialogueClickKind.SELECT, DialogueEngine.DialogueSelection.AMBIENT, null, null)
            ));
        }
        drawDialogueRows(context, rows, true, maxBottom);
    }

    private void drawDialogueNode(GuiGraphics context, ClientHandlerImpl.DialoguePresentation presentation) {
        boolean preview = presentation.hasPreview() && !presentation.nodeVisible();
        int panelWidth = Math.min(width - 24, Math.min(520, Math.max(220, width * 3 / 4)));
        if (preview && width >= 480) {
            panelWidth = Math.min(panelWidth, width - dialogueMenuWidth() - 36);
        }
        int left = preview && width >= 480 ? 12 : (width - panelWidth) / 2;
        boolean hasAdvance = presentation.node().map(node ->
                node.advanceKind() != DialogueEngine.AdvanceKind.NONE).orElse(false)
                || (preview && presentation.options().flatMap(InteractionDialogueOptionsResponse::preview)
                        .map(node -> node.advanceKind() != DialogueEngine.AdvanceKind.NONE).orElse(false));
        int replySpace = preview && presentation.options().flatMap(InteractionDialogueOptionsResponse::preview)
                .map(node -> !node.choices().isEmpty()).orElse(false)
                ? Math.min(68, Math.max(20, (height - 70) / 4)) : 0;
        int fullLineHeight = Math.max(1, font.split(presentation.fullLine(), panelWidth - 24).size()) * 10;
        int desiredHeight = Math.max(92, 30 + fullLineHeight + replySpace + (hasAdvance ? 28 : 0));
        // Leave room above the passage for replies on smaller GUI scales.
        int maxPanelHeight = Math.max(62, Math.min(preview ? 190 : 156, (height - 60) / 2));
        int panelHeight = Math.min(maxPanelHeight, desiredHeight);
        int bottom = height - 34;
        int top = bottom - panelHeight;
        boolean canAdvance = presentation.canAdvance();

        context.fill(left, top, left + panelWidth, bottom, 0xBB101019);
        context.hLine(left + 8, left + panelWidth - 8, top + 4, 0xFF79738C);
        Component visible = presentation.visibleLine();
        List<FormattedCharSequence> lines = font.split(visible, panelWidth - 24);
        int textTop = top + 12;
        int textBottom = Math.max(textTop + 10, bottom - (hasAdvance ? 29 : 12) - replySpace);
        int contentHeight = lines.size() * 10;
        int textLimit = Math.max(0, contentHeight - (textBottom - textTop));
        dialoguePassageScroll = Math.min(dialoguePassageScroll, textLimit);
        dialoguePassageViewport = new DialogueViewport(left + 8, textTop,
                left + panelWidth - 8, textBottom, textLimit);
        context.enableScissor(left + 8, textTop, left + panelWidth - 8, textBottom);
        int y = textTop - dialoguePassageScroll;
        for (FormattedCharSequence line : lines) {
            context.drawString(font, line, left + 12, y, 0xFFFFFFFF);
            y += 10;
        }
        context.disableScissor();
        if (dialoguePassageScroll > 0) {
            context.drawString(font, "▲", left + panelWidth - 18, textTop, 0xFFD5CDD8);
        }
        if (dialoguePassageScroll < textLimit) {
            context.drawString(font, "▼", left + panelWidth - 18, textBottom - 10, 0xFFD5CDD8);
        }

        List<DialogueRow> replies = new ArrayList<>();
        for (InteractionDialogueNodeResponse.Choice choice : presentation.visibleChoices()) {
            replies.add(new DialogueRow(choice.text(),
                    new DialogueClick(DialogueClickKind.CHOICE, null, null, choice.id())));
        }
        if (preview && replySpace > 0) {
            int replyTop = textBottom + 4;
            int replyBottom = bottom - (hasAdvance ? 29 : 8);
            if (replyBottom > replyTop) {
                int replyContentHeight = 0;
                for (DialogueRow reply : replies) {
                    replyContentHeight += dialogueRowHeight(reply, panelWidth);
                }
                int replyLimit = Math.max(0, replyContentHeight - (replyBottom - replyTop));
                dialoguePreviewReplyScroll = Math.min(dialoguePreviewReplyScroll, replyLimit);
                dialoguePreviewReplyViewport = new DialogueViewport(
                        left + 8, replyTop, left + panelWidth - 8, replyBottom, replyLimit);
                context.enableScissor(left + 8, replyTop, left + panelWidth - 8, replyBottom);
                int replyY = replyTop - dialoguePreviewReplyScroll;
                for (DialogueRow reply : replies) {
                    replyY = drawDialogueRow(context, reply, left, panelWidth, replyY, replyTop, replyBottom);
                }
                context.disableScissor();
            }
        } else if (!replies.isEmpty()) {
            drawDialogueRows(context, replies, false, top - 10);
        }
        int topicsWidth = 0;
        if (!preview) {
            Component topics = Component.translatable("gui.dialogue.topics");
            topicsWidth = Math.min((panelWidth - 30) / 2, Math.max(60, font.width(topics) + 16));
            drawDialogueAction(context, new DialogueRow(topics,
                    new DialogueClick(DialogueClickKind.TOPICS, null, null, null)),
                    left + 10, bottom - 24, topicsWidth, 18);
        }
        if (canAdvance) {
            DialogueEngine.AdvanceKind advanceKind = presentation.advanceKind().orElseThrow();
            Component label = switch (advanceKind) {
                case NEXT -> Component.translatable("gui.dialogue.next");
                // This acknowledges/completes the event; Topics instead pauses it.
                case BACK_TO_TOPICS -> Component.translatable("gui.button.done");
                case NONE -> throw new IllegalStateException("Non-advancing dialogue cannot render an advance action");
            };
            int actionWidth = Math.min(panelWidth - topicsWidth - 30, Math.max(68, font.width(label) + 18));
            int actionLeft = left + panelWidth - actionWidth - 10;
            drawDialogueAction(context, new DialogueRow(label,
                    new DialogueClick(DialogueClickKind.ADVANCE, null, null, null)),
                    actionLeft, bottom - 24, actionWidth, 18);
        }
    }

    private void drawDialogueRows(GuiGraphics context, List<DialogueRow> rows, boolean menu, int maxBottom) {
        int panelWidth = dialogueMenuWidth();
        int left = width - panelWidth - 12;
        int contentHeight = rows.stream().mapToInt(row -> dialogueRowHeight(row, panelWidth)).sum();
        int panelHeight = Math.min(Math.min(menu ? Math.max(245, height * 3 / 4) : 170,
                        Math.max(52, maxBottom - 18)),
                menu ? Math.max(210, contentHeight + 54) : contentHeight + 22);
        int top = menu ? 18 : Math.max(18, (maxBottom - panelHeight) / 2);
        int panelBottom = top + panelHeight;
        int clipTop = top + (menu ? 23 : 12);
        int clipBottom = panelBottom - (menu ? 30 : 10);
        context.fill(left, top, left + panelWidth, panelBottom, 0xBB101019);
        context.hLine(left + 8, left + panelWidth - 8, top + 4, 0xFF79738C);
        if (menu) {
            context.drawString(font, Component.translatable("gui.button.talk"), left + 12, top + 11, 0xFFE6E0EF);
        }
        dialogueRowsHeight = contentHeight;
        dialogueViewportHeight = Math.max(1, clipBottom - clipTop);
        int limit = Math.max(0, contentHeight - dialogueViewportHeight);
        dialogueScroll = Math.max(0, Math.min(limit, dialogueScroll));
        dialogueListViewport = new DialogueViewport(left + 8, clipTop, left + panelWidth - 8, clipBottom, limit);
        context.enableScissor(left + 8, clipTop, left + panelWidth - 8, clipBottom);
        int y = clipTop - dialogueScroll;
        for (DialogueRow row : rows) {
            y = drawDialogueRow(context, row, left, panelWidth, y, clipTop, clipBottom);
        }
        context.disableScissor();
        if (dialogueScroll > 0) {
            context.drawString(font, "▲", left + panelWidth - 18, clipTop, 0xFFD5CDD8);
        }
        if (dialogueScroll < limit) {
            context.drawString(font, "▼", left + panelWidth - 18, clipBottom - 10, 0xFFD5CDD8);
        }
        if (menu) {
            drawDialogueAction(context, new DialogueRow(Component.translatable("gui.button.back"),
                    DialogueClick.back()), left + 8, panelBottom - 26, panelWidth - 16, 20);
        }
    }

    private int dialogueMenuWidth() {
        return Math.min(width - 24, Math.min(350, Math.max(225, width * 2 / 5)));
    }

    private int dialogueRowHeight(DialogueRow row, int panelWidth) {
        return Math.max(20, font.split(row.text(), panelWidth - 24).size() * 10 + 10);
    }

    private int drawDialogueRow(GuiGraphics context, DialogueRow row,
                                int left, int panelWidth, int y, int clipTop, int clipBottom) {
        List<FormattedCharSequence> lines = font.split(row.text(), panelWidth - 24);
        int rowHeight = dialogueRowHeight(row, panelWidth);
        int hitTop = Math.max(y - 2, clipTop);
        int hitBottom = Math.min(y + rowHeight - 2, clipBottom);
        boolean hover = row.click() != null && hitBottom > hitTop
                && hoveringOver(left + 8, hitTop, panelWidth - 16, hitBottom - hitTop);
        if (row.click() != null && hitBottom > hitTop) {
            context.fill(left + 8, hitTop, left + panelWidth - 8, hitBottom,
                    hover ? 0xAA655676 : 0x55332D40);
        }
        int color = row.click() == null ? 0xFFB0B0B0 : hover ? 0xFFFFE6A6 : 0xFFFFFFFF;
        int lineY = y + 3;
        for (FormattedCharSequence line : lines) {
            context.drawString(font, line, left + 12, lineY, color);
            lineY += 10;
        }
        if (row.click() != null && hitBottom > hitTop) {
            dialogueHits.add(new DialogueHit(left + 8, hitTop, left + panelWidth - 8, hitBottom, row.click()));
        }
        return y + rowHeight;
    }

    private void drawDialogueAction(GuiGraphics context, DialogueRow row, int x, int y, int w, int h) {
        boolean hover = hoveringOver(x, y, w, h);
        context.fill(x, y, x + w, y + h, hover ? 0xAA655676 : 0x77332D40);
        context.enableScissor(x + 4, y, x + w - 4, y + h);
        context.drawString(font, row.text(), x + 6, y + (h - 8) / 2,
                hover ? 0xFFFFE6A6 : 0xFFFFFFFF);
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
        TOPICS,
        BACK
    }

    private record DialogueClick(
            DialogueClickKind kind,
            DialogueEngine.DialogueSelection selection,
            ResourceLocation eventId,
            String choiceId
    ) {
        static DialogueClick back() {
            return new DialogueClick(DialogueClickKind.BACK, null, null, null);
        }
    }

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
