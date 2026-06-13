package net.conczin.mca.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.DestinyMessage;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DestinyScreen extends VillagerEditorScreen {
    private static final ResourceLocation LOGO_TEXTURE = MCA.locate("textures/banner.png");
    private final LinkedList<Component> story = new LinkedList<>();
    private final boolean allowTeleportation;
    private String location;
    private boolean teleported = false;
    private ButtonWidget acceptWidget;

    public DestinyScreen(UUID playerUUID, boolean allowTeleportation) {
        super(playerUUID, playerUUID);

        this.allowTeleportation = allowTeleportation;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (!page.equals("general") && !page.equals("story")) {
            setPage("destiny");
        }
    }

    @Override
    protected String[] getPages() {
        LinkedList<String> pages = new LinkedList<>();
        pages.add("general");
        if (Config.getServerConfig().allowBodyCustomizationInDestiny) {
            pages.add("body");
            pages.add("head");
        }
        if (Config.getServerConfig().allowTraitCustomizationInDestiny) {
            pages.add("traits");
        }
        return pages.toArray(new String[]{});
    }

    private void drawScaledText(GuiGraphics context, Component text, int x, int y, float scale) {
        final PoseStack matrices = context.pose();
        matrices.pushPose();
        matrices.scale(scale, scale, scale);
        context.drawCenteredString(font, text, (int) (x / scale), (int) (y / scale), 0xffffffff);
        matrices.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
        this.renderPanorama(context, partialTick);
        this.renderBlurredBackground();
        this.renderMenuBackground(context);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        final PoseStack matrices = context.pose();

        switch (page) {
            case "general" -> {
                drawScaledText(context, Component.translatable("gui.destiny.whoareyou"), width / 2, height / 2 - 24, 1.5f);
                matrices.pushPose();
                matrices.scale(0.25f, 0.25f, 0.25f);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                context.blit(RenderType::guiTextured, LOGO_TEXTURE, width * 2 - 512, -40, 0, 0, 1024, 512, 1024, 512);
                matrices.popPose();
            }
            case "destiny" ->
                    drawScaledText(context, Component.translatable("gui.destiny.journey"), width / 2, height / 2 - 48, 1.5f);
            case "story" -> {
                List<Component> text = FlowingText.wrap(story.getFirst(), 256);
                int y = (int) (height / 2.0 - 20 - 7.5f * text.size());
                for (Component t : text) {
                    drawScaledText(context, t, width / 2, y, 1.25f);
                    y += 15;
                }
            }
        }
    }

    @Override
    protected boolean shouldDrawEntity() {
        return !page.equals("general") && !page.equals("destiny") && !page.equals("story") && super.shouldDrawEntity();
    }

    protected String getPath(String location) {
        String[] split = location.split(":");
        return split[split.length - 1];
    }

    @Override
    protected void setPage(String page) {
        if (page.equals("destiny") && !allowTeleportation) {
            Network.sendToServer(new DestinyMessage("", true));
            MCAClient.getDestinyManager().allowClosing();
            super.onClose();
            return;
        } else if (page.equals("destiny")) {
            //there is only one entry
            if (Config.getServerConfig().destinySpawnLocations.size() == 1) {
                selectStory(Config.getServerConfig().destinySpawnLocations.getFirst());
                return;
            }
        }

        this.page = page;
        clearWidgets();
        switch (page) {
            case "general" -> {
                drawName(width / 2 - DATA_WIDTH / 2, height / 2, name -> {
                    this.updateName(name);
                    if (acceptWidget != null) {
                        acceptWidget.active = !MCA.isBlankString(name);
                    }
                });
                drawGender(width / 2 - DATA_WIDTH / 2, height / 2 + 24);

                addModelSelectionWidgets(width / 2 - DATA_WIDTH / 2, height / 2 + 24 + 22);

                acceptWidget = addRenderableWidget(new ButtonWidget(width / 2 - 32, height / 2 + 60 + 22, 64, 20, Component.translatable("gui.button.accept"), sender -> {
                    if (Config.getServerConfig().allowBodyCustomizationInDestiny) {
                        setPage("body");
                    } else if (Config.getServerConfig().allowTraitCustomizationInDestiny) {
                        setPage("traits");
                    } else {
                        setPage("destiny");
                    }
                }));
            }
            case "destiny" -> {
                int x = 0;
                int y = 0;
                for (String location : Config.getServerConfig().destinySpawnLocations) {
                    int rows = (int) Math.ceil(Config.getServerConfig().destinySpawnLocations.size() / 3.0f);
                    float offsetX = (y + 1) == rows ? (2 - (Config.getServerConfig().destinySpawnLocations.size() - 1) % 3) / 2.0f : 0;
                    float offsetY = Math.max(0, 3 - rows) / 2.0f;
                    MutableComponent name = Component.translatable("gui.destiny." + getPath(location));
                    addRenderableWidget(new ButtonWidget((int) (width / 2.0f - 96 * 1.5f + (x + offsetX) * 96), (int) (height / 2.0f + (y + offsetY) * 20 - 16), 96, 20, name, sender -> {
                        selectStory(location);
                    }));
                    x++;
                    if (x >= 3) {
                        x = 0;
                        y++;
                    }
                }
            }
            case "story" ->
                    addRenderableWidget(new ButtonWidget(width / 2 - 48, height / 2 + 32, 96, 20, Component.translatable("gui.destiny.next"), sender -> {
                        //we teleport early here to avoid initial flickering
                        if (!teleported) {
                            Network.sendToServer(new DestinyMessage(location, false));
                            MCAClient.getDestinyManager().allowClosing();
                            teleported = true;
                        }
                        if (story.size() > 1) {
                            story.removeFirst();
                        } else {
                            Network.sendToServer(new DestinyMessage("", true));
                            super.onClose();
                        }
                    }));
            default -> super.setPage(page);
        }
    }

    private void selectStory(String location) {
        story.clear();
        story.add(Component.translatable("destiny.story.reason"));
        Map<String, String> map = Config.getServerConfig().destinyLocationsToTranslationMap;
        story.add(Component.translatable(map.getOrDefault(location, map.getOrDefault("default", "missing_default"))));
        story.add(Component.translatable("destiny.story." + getPath(location)));
        this.location = location;
        setPage("story");
    }
}
