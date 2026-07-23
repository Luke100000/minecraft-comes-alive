package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.widget.TooltipButtonWidget;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.ChatAIContextUpdateRequest;
import net.conczin.mca.network.s2c.ChatAIContextResponse;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

import static net.conczin.mca.network.c2s.ChatAIContextUpdateRequest.MAX_PROMPT_LENGTH;

public class ChatAIContextScreen extends Screen {
    private final ChatAIContextResponse context;
    private final Map<Tab, String> prompts = new EnumMap<>(Tab.class);
    private Tab selectedTab = Tab.VILLAGER;
    private MultiLineEditBox promptField;

    public ChatAIContextScreen(ChatAIContextResponse context) {
        super(Component.translatable("gui.chat_ai_context.title"));

        this.context = context;

        prompts.put(Tab.VILLAGER, context.villagerPrompt());
        prompts.put(Tab.PLAYER, context.playerPrompt());
        prompts.put(Tab.VILLAGE, context.villagePrompt());
        prompts.put(Tab.WORLD, context.worldPrompt());

        if (!selectedTab.available(context)) {
            selectedTab = Tab.PLAYER;
        }
    }

    @Override
    protected void init() {
        clearWidgets();

        int left = width / 2 - 150;
        int top = height / 2 - 120;

        int x = left + 12;
        for (Tab tab : Tab.values()) {
            ButtonWidget button = addRenderableWidget(new ButtonWidget(x, top + 30, 67, 20,
                    Component.translatable(tab.translationKey), ignored -> selectTab(tab)));
            button.active = tab != selectedTab && tab.available(context);
            button.setAlpha(tab.available(context) ? 1.0f : 0.25f);
            x += 70;
        }

        // Help button
        addRenderableWidget(new TooltipButtonWidget(left + 270, top + 6, 18, 18,
                Component.literal("?"), Component.translatable("gui.chat_ai_context.help.tooltip"), ignored -> openHelp()));

        // Prompt
        promptField = addRenderableWidget(new MultiLineEditBox(font, left + 12, top + 70, 276, 128,
                Component.translatable("gui.chat_ai_context.placeholder"), Component.translatable("gui.chat_ai_context.prompt")));
        promptField.setCharacterLimit(MAX_PROMPT_LENGTH);
        promptField.setValue(prompts.get(selectedTab));

        // Close
        addRenderableWidget(new ButtonWidget(width / 2 - 44, top + 205, 88, 20,
                Component.translatable("gui.chat_ai_context.close"), ignored -> onClose()));
    }

    private void selectTab(Tab tab) {
        if (!tab.available(context) || tab == selectedTab) {
            return;
        }
        saveCurrent();
        selectedTab = tab;
        init();
    }

    private void saveCurrent() {
        if (promptField != null && selectedTab.available(context)) {
            String prompt = promptField.getValue();
            if (prompt.equals(prompts.get(selectedTab))) {
                return;
            }
            prompts.put(selectedTab, prompt);
            Network.sendToServer(new ChatAIContextUpdateRequest(
                    selectedTab.target, context.dimension(), context.villagerUuid(), context.villageId(), prompt
            ));
        }
    }

    @Override
    public void onClose() {
        saveCurrent();
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = width / 2 - 150;
        int top = height / 2 - 120;

        graphics.fill(0, 0, width, height, 0x90000000);
        graphics.fill(left, top, left + 300, top + 232, 0xE0181B20);
        graphics.fill(left + 1, top + 1, left + 299, top + 231, 0xE02A2E36);

        // Title
        graphics.drawCenteredString(font, title, width / 2, top + 12, 0xFFFFFF);

        // Name
        graphics.drawString(font, Component.translatable(selectedTab.translationKey).append(": ").append(selectedTab.name(context)), left + 12, top + 59, 0xD0D0D0);
    }

    private void openHelp() {
        try {
            Util.getPlatform().openUri(URI.create("https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations"));
        } catch (Exception e) {
            MCA.LOGGER.error("Unable to open ChatAI help", e);
        }
    }

    private enum Tab {
        VILLAGER(ChatAIContextUpdateRequest.Target.VILLAGER, "gui.chat_ai_context.villager") {
            @Override
            boolean available(ChatAIContextResponse context) {
                return context.hasVillager();
            }

            @Override
            String name(ChatAIContextResponse context) {
                return context.villagerName();
            }
        },
        PLAYER(ChatAIContextUpdateRequest.Target.PLAYER, "gui.chat_ai_context.player") {
            @Override
            boolean available(ChatAIContextResponse context) {
                return true;
            }

            @Override
            String name(ChatAIContextResponse context) {
                return context.playerName();
            }
        },
        VILLAGE(ChatAIContextUpdateRequest.Target.VILLAGE, "gui.chat_ai_context.village") {
            @Override
            boolean available(ChatAIContextResponse context) {
                return context.hasVillage();
            }

            @Override
            String name(ChatAIContextResponse context) {
                return context.villageName();
            }
        },
        WORLD(ChatAIContextUpdateRequest.Target.WORLD, "gui.chat_ai_context.world") {
            @Override
            boolean available(ChatAIContextResponse context) {
                return true;
            }

            @Override
            String name(ChatAIContextResponse context) {
                return Component.translatable("gui.chat_ai_context.world_name").getString();
            }
        };

        private final ChatAIContextUpdateRequest.Target target;
        private final String translationKey;

        Tab(ChatAIContextUpdateRequest.Target target, String translationKey) {
            this.target = target;
            this.translationKey = translationKey;
        }

        abstract boolean available(ChatAIContextResponse context);

        abstract String name(ChatAIContextResponse context);
    }
}
