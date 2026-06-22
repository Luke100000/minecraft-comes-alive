package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.c2s.CallToPlayerMessage;
import net.mca.network.c2s.GetFamilyRequest;
import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class WhistleScreen extends Screen {
    private List<String> keys = new ArrayList<>();
    private NbtCompound villagerData = new NbtCompound();

    private VillagerEntityMCA dummy;

    private ButtonWidget selectionLeftButton;
    private ButtonWidget selectionRightButton;
    private ButtonWidget villagerNameButton;
    private ButtonWidget callButton;
    private int loadingAnimationTicks;
    private int selectedIndex;

    public WhistleScreen() {
        super(Text.translatable("gui.whistle.title"));
    }

    @Override
    public void tick() {
        super.tick();

        if (loadingAnimationTicks != -1) {
            loadingAnimationTicks++;
        }

        if (loadingAnimationTicks >= 20) {
            loadingAnimationTicks = 0;
        }
    }

    @Override
    public void init() {
        NetworkHandler.sendToServer(new GetFamilyRequest());

        selectionLeftButton = addDrawableChild(new ButtonWidget(width / 2 - 123, height / 2 + 65, 20, 20, Text.literal("<<"), b -> {
            if (selectedIndex == 0) {
                selectedIndex = keys.size() - 1;
            } else {
                selectedIndex--;
            }
            setVillagerData(selectedIndex);
        }));
        selectionRightButton = addDrawableChild(new ButtonWidget(width / 2 + 103, height / 2 + 65, 20, 20, Text.literal(">>"), b -> {
            if (selectedIndex == keys.size() - 1) {
                selectedIndex = 0;
            } else {
                selectedIndex++;
            }
            setVillagerData(selectedIndex);
        }));
        villagerNameButton = addDrawableChild(new ButtonWidget(width / 2 - 100, height / 2 + 65, 200, 20, Text.literal(""), b -> {
        }));

        callButton = addDrawableChild(new ButtonWidget(width / 2 - 100, height / 2 + 90, 60, 20, Text.translatable("gui.button.call"), (b) -> {
            NetworkHandler.sendToServer(new CallToPlayerMessage(UUID.fromString(keys.get(selectedIndex))));
            Objects.requireNonNull(this.client).setScreen(null);
        }));

        addDrawableChild(new ButtonWidget(width / 2 + 40, height / 2 + 90, 60, 20, Text.translatable("gui.button.exit"), b -> Objects.requireNonNull(this.client).setScreen(null)));

        toggleButtons(false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int sizeX, int sizeY, float offset) {
        renderBackground(context);

        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.whistle.title"), width / 2, height / 2 - 100, 0xffffff);

        if (loadingAnimationTicks != -1) {
            String loadingMsg = new String(new char[(loadingAnimationTicks / 5) % 4]).replace("\0", ".");
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.loading").append(Text.literal(loadingMsg)), width / 2 - 20, height / 2 - 10, 0xffffff);
        } else {
            if (keys.size() == 0) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.whistle.noFamily"), width / 2, height / 2 + 50, 0xffffff);
            } else {
                context.drawCenteredTextWithShadow(textRenderer, (selectedIndex + 1) + " / " + keys.size(), width / 2, height / 2 + 50, 0xffffff);
            }
        }

        drawDummy(context);

        super.render(context, sizeX, sizeY, offset);
    }

    private void drawDummy(DrawContext context) {
        final int posX = width / 2;
        int posY = height / 2 + 45;
        if (dummy != null) {
            InventoryScreen.drawEntity(context, posX, posY, 60, 0, 0, dummy);
        }
    }

    public void setVillagerData(@NotNull NbtCompound data) {
        villagerData = data;
        keys = new ArrayList<>(data.getKeys());
        loadingAnimationTicks = -1;
        selectedIndex = 0;

        setVillagerData(0);
    }

    private void setVillagerData(int index) {
        if (keys.size() > 0) {
            NbtCompound firstData = villagerData.getCompound(keys.get(index));

            dummy = EntitiesMCA.MALE_VILLAGER.get().create(MinecraftClient.getInstance().world);
            dummy.readCustomDataFromNbt(firstData);

            villagerNameButton.setMessage(dummy.getDisplayName());

            toggleButtons(true);
        } else {
            toggleButtons(false);
        }
    }

    private void toggleButtons(boolean enabled) {
        selectionLeftButton.active = enabled;
        selectionRightButton.active = enabled;
        callButton.active = enabled;
    }
}
