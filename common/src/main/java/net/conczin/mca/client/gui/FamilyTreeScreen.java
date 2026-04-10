package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.Window;
import net.conczin.mca.MCA;
import net.conczin.mca.client.resources.Icon;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.GetFamilyTreeRequest;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.*;

public class FamilyTreeScreen extends Screen {
    private static final int HORIZONTAL_SPACING = 20;
    private static final int VERTICAL_SPACING = 60;

    private static final int SPOUSE_HORIZONTAL_SPACING = 50;
    private final Map<UUID, FamilyTreeNode> family = new HashMap<>();
    private final TreeNode emptyNode = new TreeNode();
    private final Screen parent;
    private UUID focusedEntityId;
    private TreeNode tree = emptyNode;
    @Nullable
    private TreeNode focused;
    private double scrollX;
    private double scrollY;

    public FamilyTreeScreen(UUID entityId) {
        super(Component.translatable("gui.family_tree.title"));
        this.focusedEntityId = entityId;
        this.parent = Minecraft.getInstance().screen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void setFamilyData(UUID uuid, Map<UUID, FamilyTreeNode> family) {
        this.focusedEntityId = uuid;
        this.family.putAll(family);
        rebuildTree();
    }

    private boolean focusEntity(UUID id) {
        focusedEntityId = id;

        Network.sendToServer(new GetFamilyTreeRequest(id));

        return false;
    }

    @Override
    public void init() {
        focusEntity(focusedEntityId);

        addRenderableWidget(new ButtonWidget(width / 2 - 100, height - 25, 200, 20, Component.translatable("gui.done"), sender -> {
            onClose();
        }));
    }

    @Override
    public void onClose() {
        assert minecraft != null;
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        if (mouseButtonEvent.button() == 0) {
            scrollX += deltaX;
            scrollY += deltaY;
            return true;
        }
        return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (mouseButtonEvent.button() == 0 && focused != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
            if (focusEntity(focused.id)) {
                rebuildTree();
            }
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClicked);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.fill(0, 30, width, height - 30, 0x66000000);

        focused = null;

        Window window = Minecraft.getInstance().getWindow();
        double f = window.getGuiScale();
        int windowHeight = (int) Math.round(window.getGuiScaledHeight() * f);

        int x = 0;
        int y = (int) (30 * f);
        int w = (int) (width * f);
        int h = (int) ((height - 60) * f);

        GL11.glScissor(x, windowHeight - h - y, w, h);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        var matrices = context.pose();
        matrices.pushMatrix();

        int xx = (int) (scrollX + width / 2.0);
        int yy = (int) (scrollY + height / 2.0);
        matrices.translate(xx, yy);
        tree.render(context, mouseX - xx, mouseY - yy);
        matrices.popMatrix();

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        FamilyTreeNode selected = family.get(focusedEntityId);

        Component label = selected == null ? title : Component.literal(selected.getName()).append("'s ").append(title);

        context.drawCenteredString(font, label, width / 2, 10, 16777215);
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

    record Bounds(int left, int right, int top, int bottom) {
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

    private final class TreeNode {
        final UUID id;
        final boolean deceased;
        private final List<Component> label = new ArrayList<>();
        private final List<TreeNode> children = new ArrayList<>();
        private final RelationshipState relationship;
        private final String defaultNodeName = "???";
        TreeNode spouse;
        private boolean widthComputed;
        private int width;
        private int labelWidth;
        private Bounds bounds;

        private TreeNode() {
            this.id = null;
            this.deceased = false;
            this.relationship = RelationshipState.SINGLE;
            this.label.add(Component.literal(defaultNodeName));
        }

        public TreeNode(FamilyTreeNode node, boolean recurse) {
            this(node, new HashSet<>(), recurse);
        }

        public TreeNode(FamilyTreeNode node, Set<UUID> parsed, boolean recurse) {
            this.id = node.id();
            this.deceased = node.isDeceased();
            this.relationship = node.getRelationshipState();
            final MutableComponent text = Component.literal(MCA.isBlankString(node.getName()) ? defaultNodeName : node.getName());
            this.label.add(text.setStyle(text.getStyle().withColor(node.gender().getColor())));
            this.label.add(node.getProfessionText().withStyle(ChatFormatting.GRAY));

            FamilyTreeNode father = family.get(node.father());
            FamilyTreeNode mother = family.get(node.mother());
            if ((father == null || father.isDeceased()) && (mother == null || mother.isDeceased())) {
                this.label.add(Component.translatable("gui.family_tree.label.orphan").withStyle(ChatFormatting.GRAY));
            }

            if (node.getRelationshipState() != RelationshipState.SINGLE) {
                this.label.add(Component.translatable("marriage." + node.getRelationshipState().base().getIcon()));
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

        public void render(GuiGraphics context, int mouseX, int mouseY) {
            var matrices = context.pose();
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

                matrices.pushMatrix();
                matrices.translate(x, y);
                node.render(context, mouseX - x, mouseY - y);
                matrices.popMatrix();

                childrenStartX += (node.getWidth() + HORIZONTAL_SPACING) / 2;
            }

            matrices.pushMatrix();

            int fillColor = isFocused ? 0xF0100040 : 0xF0100010;
            int borderColor = isFocused ? 0xFF28007F : 1347420415;

            context.fill(bounds.left, bounds.top + 1, bounds.left + 1, bounds.bottom - 1, fillColor);
            context.fill(bounds.right - 1, bounds.top + 1, bounds.right, bounds.bottom - 1, fillColor);
            context.fill(bounds.left + 1, bounds.top, bounds.right - 1, bounds.bottom, fillColor);

            context.fill(bounds.left + 1, bounds.top + 1, bounds.left + 2, bounds.bottom - 1, borderColor);
            context.fill(bounds.right - 2, bounds.top + 1, bounds.right - 1, bounds.bottom - 1, borderColor);

            context.fill(bounds.left + 2, bounds.top + 1, bounds.right - 2, bounds.top + 2, borderColor);
            context.fill(bounds.left + 2, bounds.bottom - 2, bounds.right - 2, bounds.bottom - 1, borderColor);

            int l = bounds.top + 5;
            int k = bounds.left + 6;

            if (deceased) {
                k += 20;
            }

            for (int s = 0; s < label.size(); ++s) {
                Component line = label.get(s);
                if (line != null) {
                    context.drawString(font, line, k, l, -1);
                }

                if (s == 0) {
                    l += 2;
                }

                l += 10;
            }

            matrices.popMatrix();

            if (deceased) {
                Icon icon = MCAScreens.getInstance().getIcon("deceased");
                context.blit(RenderPipelines.GUI_TEXTURED, InteractScreen.ICON_TEXTURES, bounds.left + 6, bounds.top + 6, icon.u(), icon.v(), 16, 16, 256, 256);

                if (isFocused && mouseX <= bounds.left + 20) {
                    context.setTooltipForNextFrame(font, Component.translatable("gui.family_tree.label.deceased"), mouseX, mouseY);
                }
            }

            if (spouse != null) {
                int x = bounds.left - SPOUSE_HORIZONTAL_SPACING;
                int y = bounds.top + bounds.bottom / 2;

                context.hLine(x, bounds.left - 1, y, 0xffffffff);

                if (relationship == RelationshipState.MARRIED_TO_PLAYER ||
                    relationship == RelationshipState.MARRIED_TO_VILLAGER ||
                    relationship == RelationshipState.ENGAGED ||
                    relationship == RelationshipState.PROMISED ||
                    relationship == RelationshipState.WIDOW) {
                    Icon icon = MCAScreens.getInstance().getIcon(relationship.getIcon());
                    context.blit(RenderPipelines.GUI_TEXTURED, InteractScreen.ICON_TEXTURES, bounds.left - SPOUSE_HORIZONTAL_SPACING / 2 - 8, y - 8, icon.u(), icon.v(), 16, 16, 256, 256);
                }

                y -= spouse.label.size() * font.lineHeight / 2;
                x -= spouse.getWidth() / 2 - 6;

                matrices.pushMatrix();
                matrices.translate(x, y);

                spouse.render(context, mouseX - x, mouseY - y);
                matrices.popMatrix();
            }
        }

        private void drawHook(GuiGraphics context, int endX, int endY) {
            int midY = endY / 2;

            context.vLine(0, 0, midY, 0xffffffff);
            context.hLine(0, endX, midY, 0xffffffff);
            context.vLine(endX, midY, endY, 0xffffffff);
        }

        public int getWidth() {
            if (!widthComputed) {
                widthComputed = true;
                labelWidth = label.stream().mapToInt(font::width).max().orElse(0);
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
                        font.lineHeight * label.size() + padding * 2
                );
            }
            return bounds;
        }
    }
}
