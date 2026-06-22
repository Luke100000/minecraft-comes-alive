package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.item.BabyItem;
import net.mca.network.c2s.BabyNameRequest;
import net.mca.network.c2s.BabyNamingVillagerMessage;
import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Objects;

public class NameBabyScreen extends Screen {
    private final ItemStack baby;
    private final PlayerEntity player;
    private TextFieldWidget babyNameTextField;

    public NameBabyScreen(PlayerEntity player, ItemStack baby) {
        super(Text.translatable("gui.nameBaby.title"));
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
        addDrawableChild(new ButtonWidget(width / 2 - 40, height / 2 + 20, 80, 20, Text.translatable("gui.button.done"), (b) -> {
            NetworkHandler.sendToServer(new BabyNamingVillagerMessage(player.getInventory().selectedSlot, babyNameTextField.getText().trim()));
            Objects.requireNonNull(this.client).setScreen(null);
        }));
        addDrawableChild(new ButtonWidget(width / 2 + 105, height / 2 - 20, 60, 20, Text.translatable("gui.button.random"), (b) -> {
            NetworkHandler.sendToServer(new BabyNameRequest(((BabyItem)baby.getItem()).getGender()));
        }));

        babyNameTextField = new TextFieldWidget(this.textRenderer, width / 2 - 100, height / 2 - 20, 200, 20, Text.translatable("structure_block.structure_name"));
        babyNameTextField.setMaxLength(32);

        setInitialFocus(babyNameTextField);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int w, int h, float scale) {
        renderBackground(context);

        setFocused(babyNameTextField);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 70, 16777215);

        babyNameTextField.render(context, width / 2 - 100, height / 2 - 20, scale);

        super.render(context, w, h, scale);
    }

    public void setBabyName(String name) {
        babyNameTextField.setText(name);
    }
}
