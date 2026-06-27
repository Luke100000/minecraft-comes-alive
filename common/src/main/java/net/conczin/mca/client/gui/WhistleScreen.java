package net.conczin.mca.client.gui;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.CallToPlayerMessage;
import net.conczin.mca.network.c2s.GetFamilyRequest;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class WhistleScreen extends Screen {
    private List<String> keys = new ArrayList<>();
    private CompoundTag villagerData = new CompoundTag();

    private VillagerEntityMCA dummy;

    private ButtonWidget selectionLeftButton;
    private ButtonWidget selectionRightButton;
    private ButtonWidget villagerNameButton;
    private ButtonWidget callButton;
    private int loadingAnimationTicks;
    private int selectedIndex;

    public WhistleScreen() {
        super(Component.translatable("gui.whistle.title"));
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
        Network.sendToServer(new GetFamilyRequest());

        selectionLeftButton = addRenderableWidget(new ButtonWidget(width / 2 - 123, height / 2 + 65, 20, 20, Component.literal("<<"), b -> {
            if (selectedIndex == 0) {
                selectedIndex = keys.size() - 1;
            } else {
                selectedIndex--;
            }
            setVillagerData(selectedIndex);
        }));
        selectionRightButton = addRenderableWidget(new ButtonWidget(width / 2 + 103, height / 2 + 65, 20, 20, Component.literal(">>"), b -> {
            if (selectedIndex == keys.size() - 1) {
                selectedIndex = 0;
            } else {
                selectedIndex++;
            }
            setVillagerData(selectedIndex);
        }));
        villagerNameButton = addRenderableWidget(new ButtonWidget(width / 2 - 100, height / 2 + 65, 200, 20, Component.literal(""), b -> {
        }));

        callButton = addRenderableWidget(new ButtonWidget(width / 2 - 100, height / 2 + 90, 60, 20, Component.translatable("gui.button.call"), (b) -> {
            Network.sendToServer(new CallToPlayerMessage(UUID.fromString(keys.get(selectedIndex))));
            Objects.requireNonNull(this.minecraft).gui.setScreen(null);
        }));

        addRenderableWidget(new ButtonWidget(width / 2 + 40, height / 2 + 90, 60, 20, Component.translatable("gui.button.exit"), b -> Objects.requireNonNull(this.minecraft).gui.setScreen(null)));

        toggleButtons(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float offset) {
        super.extractRenderState(context, mouseX, mouseY, offset);

        context.centeredText(font, Component.translatable("gui.whistle.title"), width / 2, height / 2 - 100, 0xffffff);

        if (loadingAnimationTicks != -1) {
            String loadingMsg = new String(new char[(loadingAnimationTicks / 5) % 4]).replace("\0", ".");
            context.text(font, Component.translatable("gui.loading").append(Component.literal(loadingMsg)), width / 2 - 20, height / 2 - 10, 0xffffff);
        } else {
            if (keys.isEmpty()) {
                context.centeredText(font, Component.translatable("gui.whistle.noFamily"), width / 2, height / 2 + 50, 0xffffff);
            } else {
                context.centeredText(font, (selectedIndex + 1) + " / " + keys.size(), width / 2, height / 2 + 50, 0xffffff);
            }
        }

        if (dummy != null) {
            int posX = width / 2;
            int posY = height / 2;
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, posX - 30, posY - 70, posX + 30, posY + 45, 60, 0, mouseX, mouseY, dummy);
        }
    }

    public void setVillagerData(@NotNull CompoundTag data) {
        villagerData = data;
        keys = new ArrayList<>(data.keySet());
        loadingAnimationTicks = -1;
        selectedIndex = 0;

        setVillagerData(0);
    }

    private void setVillagerData(int index) {
        if (!keys.isEmpty()) {
            CompoundTag firstData = villagerData.getCompound(keys.get(index)).orElseGet(CompoundTag::new);

            dummy = EntitiesMCA.MALE_VILLAGER.create(Minecraft.getInstance().level, EntitySpawnReason.LOAD);
            dummy.load(TagValueInput.create(ProblemReporter.DISCARDING, dummy.registryAccess(), firstData));

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
