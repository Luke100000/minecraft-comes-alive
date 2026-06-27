package net.conczin.mca.client.gui;

import net.conczin.mca.item.BabyItem;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.BabyNameRequest;
import net.conczin.mca.network.c2s.BabyNamingVillagerMessage;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public class NameBabyScreen extends Screen {
    private final ItemStack baby;
    private final Player player;
    private EditBox babyNameTextField;
    private boolean restoreHudHidden;

    public NameBabyScreen(Player player, ItemStack baby) {
        super(Component.translatable("gui.nameBaby.title"));
        this.baby = baby;
        this.player = player;
    }

    @Override
    public void init() {
        addRenderableWidget(new ButtonWidget(width / 2 - 40, height / 2 + 20, 80, 20, Component.translatable("gui.button.done"), b -> {
            Network.sendToServer(new BabyNamingVillagerMessage(player.getInventory().getSelectedSlot(), babyNameTextField.getValue().trim()));
            Objects.requireNonNull(this.minecraft).gui.setScreen(null);
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + 105, height / 2 - 20, 60, 20, Component.translatable("gui.button.random"), b -> Network.sendToServer(new BabyNameRequest(((BabyItem) baby.getItem()).getGender()))));

        babyNameTextField = new EditBox(this.font, width / 2 - 100, height / 2 - 20, 200, 20, Component.translatable("structure_block.structure_name"));
        babyNameTextField.setMaxLength(32);
        addRenderableWidget(babyNameTextField);
        setInitialFocus(babyNameTextField);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void added() {
        super.added();
        restoreHudHidden = Objects.requireNonNull(this.minecraft).gui.hud.isHidden();
        if (!restoreHudHidden) {
            this.minecraft.gui.hud.toggle();
        }
    }

    @Override
    public void removed() {
        if (this.minecraft != null && this.minecraft.gui.hud.isHidden() != restoreHudHidden) {
            this.minecraft.gui.hud.toggle();
        }
        super.removed();
    }

    @Override
    public boolean showsActiveEffects() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int w, int h, float scale) {
        super.extractRenderState(context, w, h, scale);
        setFocused(babyNameTextField);
        context.centeredText(this.font, this.title, this.width / 2, 70, 16777215);
    }

    public void setBabyName(String name) {
        babyNameTextField.setValue(name);
    }
}
