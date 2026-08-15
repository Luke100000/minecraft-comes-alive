package net.conczin.mca.client.gui;

import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.network.c2s.BabyNameRequest;
import net.conczin.mca.network.c2s.BabyNamingVillagerMessage;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
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

    public NameBabyScreen(Player player, ItemStack baby) {
        super(Component.translatable("gui.nameBaby.title"));
        this.baby = baby;
        this.player = player;
    }

    @Override
    public void tick() {
        super.tick();

        babyNameTextField.tick();
    }

    @Override
    public void init() {
        addRenderableWidget(new ButtonWidget(width / 2 - 40, height / 2 + 20, 80, 20, Component.translatable("gui.button.done"), (b) -> {
            NetworkHandler.sendToServer(new BabyNamingVillagerMessage(player.getInventory().selected, babyNameTextField.getValue().trim()));
            Objects.requireNonNull(this.minecraft).setScreen(null);
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + 105, height / 2 - 20, 60, 20, Component.translatable("gui.button.random"), (b) -> {
            NetworkHandler.sendToServer(new BabyNameRequest(((BabyItem)baby.getItem()).getGender()));
        }));

        babyNameTextField = new EditBox(this.font, width / 2 - 100, height / 2 - 20, 200, 20, Component.translatable("structure_block.structure_name"));
        babyNameTextField.setMaxLength(32);

        setInitialFocus(babyNameTextField);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics context, int w, int h, float scale) {
        renderBackground(context);

        setFocused(babyNameTextField);

        context.drawCenteredString(this.font, this.title, this.width / 2, 70, 16777215);

        babyNameTextField.render(context, width / 2 - 100, height / 2 - 20, scale);

        super.render(context, w, h, scale);
    }

    public void setBabyName(String name) {
        babyNameTextField.setValue(name);
    }
}
