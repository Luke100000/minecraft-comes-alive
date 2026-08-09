package net.mca.client.gui;

import net.mca.MCA;
import net.mca.client.gui.widget.TooltipButtonWidget;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.ChatAIContextUpdateRequest;
import net.mca.network.s2c.ChatAIContextResponse;
import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

import static net.mca.entity.VillagerEntityMCA.MAX_NICKNAME_LENGTH;
import static net.mca.network.c2s.ChatAIContextUpdateRequest.MAX_PROMPT_LENGTH;

public class ChatAIContextScreen extends Screen {
    private static final int CONTEXT_TEXT_COLOR = 0xD0D0D0;

    private final ChatAIContextResponse context;
    private final Map<Tab, String> prompts = new EnumMap<>(Tab.class);
    private Tab selectedTab = Tab.VILLAGER;
    private EditBoxWidget promptField;
    private TextFieldWidget nicknameField;
    private String nickname;

    public ChatAIContextScreen(ChatAIContextResponse context) {
        super(Text.translatable("gui.chat_ai_context.title"));
        this.context = context;

        prompts.put(Tab.VILLAGER, context.villagerPrompt());
        prompts.put(Tab.PLAYER, context.playerPrompt());
        prompts.put(Tab.VILLAGE, context.villagePrompt());
        prompts.put(Tab.WORLD, context.worldPrompt());
        nickname = context.villagerNickname();

        if (!selectedTab.available(context)) {
            selectedTab = Tab.PLAYER;
        }
    }

    @Override
    protected void init() {
        super.init();

        int left = width / 2 - 150;
        int top = height / 2 - 120;

        int x = left + 12;
        for (Tab tab : Tab.values()) {
            ButtonWidget button = addDrawableChild(new ButtonWidget(
                    x, top + 30, 67, 20,
                    Text.translatable(tab.translationKey),
                    ignored -> selectTab(tab)
            ));
            button.active = tab != selectedTab && tab.available(context);
            x += 70;
        }

        addDrawableChild(new TooltipButtonWidget(
                left + 270, top + 6, 18, 18,
                Text.literal("?"),
                Text.translatable("gui.chat_ai_context.help.tooltip"),
                ignored -> openHelp()
        ));

        nicknameField = null;
        int promptY = top + 70;
        int promptHeight = 128;
        if (selectedTab == Tab.VILLAGER) {
            nicknameField = addDrawableChild(new TextFieldWidget(
                    textRenderer, left + 80, top + 70, 208, 15,
                    Text.translatable("gui.chat_ai_context.nickname_placeholder")
            ));
            nicknameField.setMaxLength(MAX_NICKNAME_LENGTH);
            nicknameField.setPlaceholder(Text.translatable("gui.chat_ai_context.nickname_placeholder"));
            nicknameField.setText(nickname);
            promptY += 20;
            promptHeight -= 20;
        }

        promptField = addDrawableChild(new EditBoxWidget(
                textRenderer, left + 12, promptY, 276, promptHeight,
                Text.translatable("gui.chat_ai_context.placeholder"),
                Text.translatable("gui.chat_ai_context.prompt")
        ));
        promptField.setMaxLength(MAX_PROMPT_LENGTH);
        promptField.setText(prompts.get(selectedTab));

        addDrawableChild(new ButtonWidget(
                width / 2 - 44, top + 205, 88, 20,
                Text.translatable("gui.chat_ai_context.close"), ignored -> close()
        ));
    }

    private void selectTab(Tab tab) {
        if (!tab.available(context) || tab == selectedTab) {
            return;
        }
        saveCurrent();
        selectedTab = tab;
        clearChildren();
        init();
    }

    private void saveCurrent() {
        if (promptField == null || !selectedTab.available(context)) {
            return;
        }

        String prompt = promptField.getText();
        String updatedNickname = nicknameField == null ? nickname : nicknameField.getText().strip();
        if (prompt.equals(prompts.get(selectedTab)) && updatedNickname.equals(nickname)) {
            return;
        }

        prompts.put(selectedTab, prompt);
        nickname = updatedNickname;
        NetworkHandler.sendToServer(new ChatAIContextUpdateRequest(
                selectedTab.target,
                context.dimension(),
                context.villagerUuid(),
                context.villageId(),
                prompt,
                nickname
        ));
    }

    @Override
    public void close() {
        saveCurrent();
        super.close();
    }

    @Override
    public void tick() {
        super.tick();
        if (promptField != null) {
            promptField.tick();
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        renderBackground(drawContext);

        int left = width / 2 - 150;
        int top = height / 2 - 120;
        drawContext.fill(0, 0, width, height, 0x90000000);
        drawContext.fill(left, top, left + 300, top + 232, 0xE0181B20);
        drawContext.fill(left + 1, top + 1, left + 299, top + 231, 0xE02A2E36);
        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 12, 0xFFFFFF);

        Text contextName = Text.translatable(selectedTab.translationKey)
                .append(": ")
                .append(selectedTab.name(context));
        drawContext.drawTextWithShadow(textRenderer, contextName, left + 12, top + 59, CONTEXT_TEXT_COLOR);
        if (selectedTab == Tab.VILLAGER) {
            drawContext.drawTextWithShadow(textRenderer, Text.translatable("gui.chat_ai_context.nickname"), left + 12, top + 73, CONTEXT_TEXT_COLOR);
        }

        super.render(drawContext, mouseX, mouseY, delta);
    }

    private void openHelp() {
        try {
            Util.getOperatingSystem().open(URI.create("https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations"));
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
                return Text.translatable("gui.chat_ai_context.world_name").getString();
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
