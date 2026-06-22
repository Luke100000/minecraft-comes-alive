package net.mca.client.gui;

import net.mca.MCA;
import net.mca.client.resources.Icon;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.network.c2s.GetFamilyTreeRequest;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.*;

public class FamilyTreeScreen extends Screen {
    private static final int HORIZONTAL_SPACING = 20;
    private static final int VERTICAL_SPACING = 60;

    private static final int SPOUSE_HORIZONTAL_SPACING = 50;

    private UUID focusedEntityId;

    private final Map<UUID, FamilyTreeNode> family = new HashMap<>();

    private final TreeNode emptyNode = new TreeNode();

    private TreeNode tree = emptyNode;

    @Nullable
    private TreeNode focused;

    private double scrollX;
    private double scrollY;

    private final Screen parent;

    public FamilyTreeScreen(UUID entityId) {
        super(Text.translatable("gui.family_tree.title"));
        this.focusedEntityId = entityId;
        this.parent = MinecraftClient.getInstance().currentScreen;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void setFamilyData(UUID uuid, Map<UUID, FamilyTreeNode> family) {
        this.focusedEntityId = uuid;
        this.family.putAll(family);
        rebuildTree();
    }

    private boolean focusEntity(UUID id) {
        focusedEntityId = id;

        NetworkHandler.sendToServer(new GetFamilyTreeRequest(id));

        return false;
    }

    @Override
    public void init() {
        focusEntity(focusedEntityId);

        addDrawableChild(new ButtonWidget(width / 2 - 100, height - 25, 200, 20, Text.translatable("gui.done"), sender -> {
            close();
        }));
    }

    @Override
    public void close() {
        assert client != null;
        client.setScreen(parent);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) {
            scrollX += deltaX;
            scrollY += deltaY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && focused != null) {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1));
            if (focusEntity(focused.id)) {
                rebuildTree();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.fill(0, 30, width, height - 30, 0x66000000);

        focused = null;

        Window window = MinecraftClient.getInstance().getWindow();
        double f = window.getScaleFactor();
        int windowHeight = (int)Math.round(window.getScaledHeight() * f);

        int x = 0;
        int y = (int)(30 * f);
        int w = (int)(width * f);
        int h = (int)((height - 60) * f);

        GL11.glScissor(x, windowHeight - h - y, w, h);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        final MatrixStack matrices = context.getMatrices();
        matrices.push();

        int xx = (int)(scrollX + width / 2);
        int yy = (int)(scrollY + height / 2);
        matrices.translate(xx, yy, 0);
        tree.render(context, mouseX - xx, mouseY - yy);
        matrices.pop();

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        FamilyTreeNode selected = family.get(focusedEntityId);

        Text label = selected == null ? title : Text.literal(selected.getName()).append("'s ").append(title);

        context.drawCenteredTextWithShadow(textRenderer, label, width / 2, 10, 16777215);

        super.render(context, mouseX, mouseY, delta);
    }

    private void rebuildTree() {
        scrollX = 14;
        scrollY = -69;
        FamilyTreeNode focusedNode = family.get(focusedEntityId);

        // garbage collect
        focused = null;
        tree = emptyNode;

        if (focusedNode != null) {
            tree = insertParents(new TreeNode(focusedNode, true), focusedNode, 2);
        }
    }

    private TreeNode insertParents(TreeNode root, FamilyTreeNode focusedNode, int levels) {
        @Nullable FamilyTreeNode father = family.get(focusedNode.father());
        @Nullable FamilyTreeNode mother = family.get(focusedNode.mother());

        @Nullable FamilyTreeNode newRoot = father != null ? father : mother;

        TreeNode fNode = newRoot == null ? new TreeNode() : new TreeNode(newRoot, false);
        fNode.children.add(root);

        @Nullable FamilyTreeNode spouse = newRoot == father ? mother : father;

        fNode.spouse = spouse == null ? new TreeNode() : new TreeNode(spouse, false);

        if (newRoot != null && levels > 0) {
            return insertParents(fNode, newRoot, levels - 1);
        }

        return fNode;
    }

    private final class TreeNode {
        private boolean widthComputed;

        private int width;

        private int labelWidth;

        private final List<Text> label = new ArrayList<>();

        private final List<TreeNode> children = new ArrayList<>();

        private Bounds bounds;

        TreeNode spouse;

        final UUID id;

        final boolean deceased;
        private final RelationshipState relationship;

        private final String defaultNodeName = "???";

        private TreeNode() {
            this.id = null;
            this.deceased = false;
            this.relationship = RelationshipState.SINGLE;
            this.label.add(Text.literal(defaultNodeName));
        }

        public TreeNode(FamilyTreeNode node, boolean recurse) {
            this(node, new HashSet<>(), recurse);
        }

        public TreeNode(FamilyTreeNode node, Set<UUID> parsed, boolean recurse) {
            this.id = node.id();
            this.deceased = node.isDeceased();
            this.relationship = node.getRelationshipState();
            final MutableText text = Text.literal(MCA.isBlankString(node.getName()) ? defaultNodeName : node.getName());
            this.label.add(text.setStyle(text.getStyle().withColor(node.gender().getColor())));
            this.label.add(node.getProfessionText().formatted(Formatting.GRAY));

            FamilyTreeNode father = family.get(node.father());
            FamilyTreeNode mother = family.get(node.mother());
            if ((father == null || father.isDeceased()) && (mother == null || mother.isDeceased())) {
                this.label.add(Text.translatable("gui.family_tree.label.orphan").formatted(Formatting.GRAY));
            }

            if (node.getRelationshipState() != RelationshipState.SINGLE) {
                this.label.add(Text.translatable("marriage." + node.getRelationshipState().base().getIcon()));
            }

            if (recurse) {
                node.children().forEach(child -> {
                    FamilyTreeNode e = family.get(child);
                    if (e != null) {
                        children.add(new TreeNode(e, parsed, parsed.add(child)));
                    }
                });

                FamilyTreeNode spouse = family.get(node.partner());

                if (spouse != null) {
                    this.spouse = new TreeNode(spouse, parsed, false);
                } else if (!children.isEmpty()) {
                    this.spouse = new TreeNode();
                }
            }
        }

        public void render(DrawContext context, int mouseX, int mouseY) {
            final MatrixStack matrices = context.getMatrices();
            Bounds bounds = getBounds();

            boolean isFocused = id != null && bounds.contains(mouseX, mouseY);

            if (isFocused) {
                focused = this;
            }

            int childrenStartX = -getWidth() / 2;

            for (TreeNode node : children) {
                childrenStartX += (node.getWidth() + HORIZONTAL_SPACING) / 2;

                int x = childrenStartX + HORIZONTAL_SPACING / 2;
                int y = VERTICAL_SPACING;

                drawHook(context, x, y);

                matrices.push();
                matrices.translate(x, y, 0);
                node.render(context, mouseX - x, mouseY - y);
                matrices.pop();

                childrenStartX += (node.getWidth() + HORIZONTAL_SPACING) / 2;
            }

            matrices.push();
            matrices.translate(0, 0, 400);

            int fillColor = isFocused ? 0xF0100040 : 0xF0100010;
            int borderColor = isFocused ? 0xFF28007F : 1347420415;

            context.fill(bounds.left, bounds.top + 1, bounds.left + 1, bounds.bottom - 1, fillColor);
            context.fill(bounds.right - 1, bounds.top + 1, bounds.right, bounds.bottom - 1, fillColor);
            context.fill(bounds.left + 1, bounds.top, bounds.right - 1, bounds.bottom, fillColor);

            context.fill(bounds.left + 1, bounds.top + 1, bounds.left + 2, bounds.bottom - 1, borderColor);
            context.fill(bounds.right - 2, bounds.top + 1, bounds.right - 1, bounds.bottom - 1, borderColor);

            context.fill(bounds.left + 2, bounds.top + 1, bounds.right - 2, bounds.top + 2, borderColor);
            context.fill(bounds.left + 2, bounds.bottom - 2, bounds.right - 2, bounds.bottom - 1, borderColor);

            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());

            int l = bounds.top + 5;
            int k = bounds.left + 6;

            if (deceased) {
                k += 20;
            }

            Matrix4f matrix4f = matrices.peek().getPositionMatrix();

            TextRenderer r = MinecraftClient.getInstance().textRenderer;

            for (int s = 0; s < label.size(); ++s) {
                Text line = label.get(s);
                if (line != null) {
                    r.draw(line, k, l, -1, true, matrix4f, immediate, TextRenderer.TextLayerType.NORMAL, 0, 15728880);
                }

                if (s == 0) {
                    l += 2;
                }

                l += 10;
            }

            immediate.draw();
            matrices.pop();

            if (deceased) {
                Icon icon = MCAScreens.getInstance().getIcon("deceased");
                context.drawTexture(InteractScreen.ICON_TEXTURES, bounds.left + 6, bounds.top + 6, 0, icon.u(), icon.v(), 16, 16, 256, 256);

                if (isFocused && mouseX <= bounds.left + 20) {
                    matrices.push();
                    matrices.translate(0, 0, 20);
                    context.drawTooltip(textRenderer, Text.translatable("gui.family_tree.label.deceased"), mouseX, mouseY);
                    matrices.pop();
                }
            }

            if (spouse != null) {
                int x = bounds.left - SPOUSE_HORIZONTAL_SPACING;
                int y = bounds.top + bounds.bottom / 2;

                context.drawHorizontalLine(x, bounds.left - 1, y, 0xffffffff);

                if (relationship == RelationshipState.MARRIED_TO_PLAYER ||
                        relationship == RelationshipState.MARRIED_TO_VILLAGER ||
                        relationship == RelationshipState.ENGAGED ||
                        relationship == RelationshipState.PROMISED ||
                        relationship == RelationshipState.WIDOW) {
                    Icon icon = MCAScreens.getInstance().getIcon(relationship.getIcon());
                    context.drawTexture(InteractScreen.ICON_TEXTURES, bounds.left - SPOUSE_HORIZONTAL_SPACING / 2 - 8, y - 8, 0, icon.u(), icon.v(), 16, 16, 256, 256);
                }

                y -= spouse.label.size() * textRenderer.fontHeight / 2;
                x -= spouse.getWidth() / 2 - 6;

                matrices.push();
                matrices.translate(x, y, 0);

                spouse.render(context, mouseX - x, mouseY - y);
                matrices.pop();
            }
        }

        private void drawHook(DrawContext context, int endX, int endY) {
            int midY = endY / 2;

            context.drawVerticalLine(0, 0, midY, 0xffffffff);
            context.drawHorizontalLine(0, endX, midY, 0xffffffff);
            context.drawVerticalLine(endX, midY, endY, 0xffffffff);
        }

        public int getWidth() {
            if (!widthComputed) {
                widthComputed = true;
                labelWidth = label.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
                if (deceased) {
                    labelWidth += 20;
                }
                width = Math.max(labelWidth + 10, children.stream().mapToInt(TreeNode::getWidth).sum()) + (HORIZONTAL_SPACING / 2);
                if (spouse != null) {
                    width += spouse.getWidth() + SPOUSE_HORIZONTAL_SPACING;
                }
            }
            return width;
        }

        public Bounds getBounds() {
            if (bounds == null) {
                getWidth();

                int padding = 4;
                bounds = new Bounds(
                        (-labelWidth / 2) - padding,
                        (labelWidth / 2) + padding * 2,
                        -padding,
                        textRenderer.fontHeight * label.size() + padding * 2
                );
            }
            return bounds;
        }
    }

    static final class Bounds {
        final int left;
        final int right;
        final int top;
        final int bottom;

        public Bounds(int left, int right, int top, int bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        public Bounds add(int x, int y) {
            return new Bounds(left + x, right + x, top + y, bottom + y);
        }

        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= left
                    && mouseY >= top
                    && mouseX <= right
                    && mouseY <= bottom;
        }
    }
}
