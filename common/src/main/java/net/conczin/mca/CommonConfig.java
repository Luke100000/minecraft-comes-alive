package net.conczin.mca;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {
    public final ModConfigSpec.ConfigValue<String> _read_this_before_using_villager_ai;
    public final ModConfigSpec.ConfigValue<String> villagerChatAIEndpoint;
    public final ModConfigSpec.ConfigValue<String> villagerChatAIToken;
    public final ModConfigSpec.ConfigValue<String> villagerChatAISystemPrompt;
    public final ModConfigSpec.ConfigValue<Boolean> villagerChatAIFuseSystemPrompt;
    public final ModConfigSpec.ConfigValue<Boolean> villagerChatAIUseLongTermMemory;
    public final ModConfigSpec.ConfigValue<Boolean> villagerChatAIUseSharedLongTermMemory;
    public final ModConfigSpec.ConfigValue<Boolean> villagerChatAIIncludeSessionInformation;
    public final ModConfigSpec.ConfigValue<String> inworldAIToken;

    CommonConfig(ModConfigSpec.Builder builder) {
        builder.translation("mca.configuration.section.ai").push("ai");
        _read_this_before_using_villager_ai = builder

                .translation("mca.configuration._read_this_before_using_villager_ai")
                .define("_read_this_before_using_villager_ai", "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations");
        villagerChatAIEndpoint = builder

                .comment("Chat completion endpoint for villager AI chat requests.")
                .translation("mca.configuration.villagerChatAIEndpoint")
                .define("villagerChatAIEndpoint", "https://api.conczin.net/v1/mca/chat");
        villagerChatAIToken = builder

                .comment("Optional API token.")
                .translation("mca.configuration.villagerChatAIToken")
                .define("villagerChatAIToken", "");
        villagerChatAISystemPrompt = builder

                .comment("System prompt used to guide global villager AI behavior.")
                .translation("mca.configuration.villagerChatAISystemPrompt")
                .define("villagerChatAISystemPrompt", "");
        villagerChatAIFuseSystemPrompt = builder

                .comment("Prepends the system prompt to the user message for endpoints that ignore the system role.")
                .translation("mca.configuration.villagerChatAIFuseSystemPrompt")
                .define("villagerChatAIFuseSystemPrompt", false);
        villagerChatAIUseLongTermMemory = builder

                .comment("If true, AI uses long-term memory for persistent conversations.")
                .translation("mca.configuration.villagerChatAIUseLongTermMemory")
                .define("villagerChatAIUseLongTermMemory", false);
        villagerChatAIUseSharedLongTermMemory = builder

                .comment("If false, villager will have separate memories per player.")
                .translation("mca.configuration.villagerChatAIUseSharedLongTermMemory")
                .define("villagerChatAIUseSharedLongTermMemory", false);
        villagerChatAIIncludeSessionInformation = builder

                .comment("If true, session-specific information is included in AI requests. Only relevant if writing a custom backend.")
                .translation("mca.configuration.villagerChatAIIncludeSessionInformation")
                .define("villagerChatAIIncludeSessionInformation", false);
        inworldAIToken = builder

                .comment("Inworld API token.")
                .translation("mca.configuration.inworldAIToken")
                .define("inworldAIToken", "");
        builder.pop();
    }
}
