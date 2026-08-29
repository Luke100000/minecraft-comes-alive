package net.conczin.mca.entity.ai.chatAI;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Messenger;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.chatAI.modules.*;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OpenAIChatAI extends AbstractChatAIStrategy {
    private static final int MAX_MEMORY = 768;
    private static final int MAX_MEMORY_TIME = 20 * 60 * 45;
    private static final int HTTP_CONNECT_TIMEOUT_MS = 15_000;
    private static final int HTTP_READ_TIMEOUT_MS = 120_000;

    private static final Map<UUID, List<DialogueEntry>> memory = new HashMap<>();
    private static final Map<UUID, Long> lastInteractions = new HashMap<>();

    public static String translate(String phrase) {
        return phrase.replace("_", " ").toLowerCase(Locale.ROOT).replace("mca.", "");
    }

    private static HttpURLConnection getHttpURLConnection(String url, String token) throws IOException {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.toString());
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + token);
        con.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
        con.setReadTimeout(HTTP_READ_TIMEOUT_MS);
        con.setDoOutput(true);
        return con;
    }

    private static Answer parseAnswer(String body) {
        JsonObject map = JsonParser.parseString(body).getAsJsonObject();
        String message = map.has("choices")
                ? map.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").getAsJsonPrimitive("content").getAsString()
                : null;
        String error = map.has("error") ? map.get("error").getAsString().trim().replace("\n", " ") : null;

        if (message != null) {
            message = stripCodeFence(message);
        }

        StructuredResponse structuredReply;
        if (message == null) {
            structuredReply = null;
        } else if (!message.stripLeading().startsWith("{")) {
            structuredReply = new StructuredResponse(cleanupAnswer(message), "");
        } else {
            try {
                structuredReply = new Gson().fromJson(message, StructuredResponse.class);
            } catch (JsonSyntaxException e) {
                MCA.LOGGER.warn("Error parsing answer: {} ({})", message, e.getMessage());
                structuredReply = new StructuredResponse(cleanupAnswer(message), "");
            }
        }

        return new Answer(structuredReply, error);
    }

    private static String stripCodeFence(String message) {
        String trimmed = message.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring("```json".length()).stripLeading();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring("```".length()).stripLeading();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - "```".length()).stripTrailing();
        }
        return trimmed;
    }

    public static Answer post(String url, String requestBody, String token) {
        HttpURLConnection con = null;
        try {
            con = getHttpURLConnection(url, token);
            try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
                wr.write(requestBody.getBytes(StandardCharsets.UTF_8));
                wr.flush();
            }

            String body;
            try (InputStream response = con.getInputStream()) {
                body = IOUtils.toString(response, StandardCharsets.UTF_8);
            }
            return parseAnswer(body);
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return new Answer(null, "Unknown error, check log!");
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    public static String verify(String encodedURL) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) URI.create(encodedURL).toURL().openConnection();
            con.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.toString());
            con.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            con.setReadTimeout(HTTP_READ_TIMEOUT_MS);

            String body;
            try (InputStream response = con.getInputStream()) {
                body = IOUtils.toString(response, StandardCharsets.UTF_8);
            }

            JsonObject map = JsonParser.parseString(body).getAsJsonObject();
            return map.has("answer") ? map.get("answer").getAsString().trim().replace("\n", " ") : "";
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return "error";
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    static String jsonStringQuote(String string) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : string.toCharArray()) {
            sb.append(switch (c) {
                case '\\', '"', '/' -> "\\" + c;
                case '\b' -> "\\b";
                case '\t' -> "\\t";
                case '\n' -> "\\n";
                case '\f' -> "\\f";
                case '\r' -> "\\r";
                default -> c < ' ' ? String.format(Locale.ROOT, "\\u%04x", (int)c) : c;
            });
        }
        return sb.append('"').toString();
    }

    static String cleanupAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        answer = answer.replace("\"", "");
        answer = answer.replace("\n", " ");
        String[] parts = answer.split(":", 2);
        return parts[parts.length - 1].strip();
    }

    @Override
    protected CompletableFuture<Optional<String>> requestAndApply(ServerPlayer player, VillagerEntityMCA villager, String msg, MinecraftServer server) {
        final PreparedRequest request;
        try {
            request = prepareRequest(player, villager, msg);
        } catch (Exception e) {
            handleFailure(player, "Failed to prepare LLM request!", e);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture
                .supplyAsync(() -> post(request.url(), request.body(), request.token()))
                .thenApplyAsync(message -> {
                    if (!isConversationValid(player, villager)) {
                        return Optional.empty();
                    }

                    try {
                        return applyAnswer(player, villager, msg, message);
                    } catch (Exception e) {
                        handleFailure(player, "Failed to apply LLM response!", e);
                        return Optional.empty();
                    }
                }, server);
    }

    private PreparedRequest prepareRequest(ServerPlayer player, VillagerEntityMCA villager, String msg) {
        Config config = Config.getInstance();
        boolean isInHouse = config.villagerChatAIEndpoint.contains("conczin.net");

        String playerName = Messenger.getName(player);
        String villagerName = villager.getName().getString();
        UUID memoryKey = villager.getUUID();

        long time = villager.level().getGameTime();
        if (time > lastInteractions.getOrDefault(memoryKey, 0L) + MAX_MEMORY_TIME) {
            memory.remove(memoryKey);
        }
        lastInteractions.put(memoryKey, time);

        List<DialogueEntry> pastDialogue = memory.computeIfAbsent(memoryKey, key -> new LinkedList<>());
        int estimatedTokens = pastDialogue.stream().mapToInt(entry -> entry.content().length() / 4).sum();
        while (estimatedTokens > MAX_MEMORY && !pastDialogue.isEmpty()) {
            estimatedTokens -= pastDialogue.removeFirst().content().length() / 4;
        }

        List<String> input = new LinkedList<>();
        PersonalityModule.apply(input, villager, player);
        TraitsModule.apply(input, villager, player);
        RelationModule.apply(input, villager, player);
        VillageModule.apply(input, villager, player);
        EnvironmentModule.apply(input, villager, player);
        PlayerModule.apply(input, villager, player);

        Map<String, String> variables = Map.of(
                "player", playerName,
                "villager", villagerName
        );

        StringBuilder sb = new StringBuilder();
        if (isInHouse || config.villagerChatAIIncludeSessionInformation) {
            sb.append("[world_id:").append(player.level().getSeed()).append("]");
            sb.append("[player_id:").append(player.getUUID()).append("]");
            sb.append("[character_id:").append(villager.getUUID()).append("]");
            if (config.villagerChatAIUseLongTermMemory) {
                sb.append("[use_memory:true]");
            }
            if (config.villagerChatAIUseSharedLongTermMemory) {
                sb.append("[shared_memory:true]");
            }
        }

        if (!config.villagerChatAISystemPrompt.isEmpty()) {
            sb.append(config.villagerChatAISystemPrompt).append("\n");
        } else if (!isInHouse) {
            sb.append("You are a Minecraft villager, fully immersed in their virtual world, unaware of its artificial nature. You respond based on your description, your role, and your knowledge of the world. You have no knowledge of the real world, and do not realize that you are within Minecraft. You are no assistant! You can be sarcastic, funny, or even rude when appropriate.\n");
        }

        ChatAIContext.appendPrompts(sb, player, villager, Village.findNearest(villager).orElse(null));

        for (String prompt : input) {
            String resolved = prompt;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                resolved = resolved.replace("$" + entry.getKey(), entry.getValue());
            }
            sb.append(resolved);
        }

        if (villager.getAgeState() == AgeState.BABY || villager.getAgeState() == AgeState.TODDLER || villager.getAgeState() == AgeState.CHILD) {
            sb.append("You are a child/baby and MUST NOT flirt with the player or use any romantic or suggestive language. Keep your responses innocent, child-like, and age-appropriate.\n");
        } else if (Relationship.IS_RELATIVE.test(villager, player)) {
            sb.append("You are related to the player and MUST NOT flirt with them or use romantic/suggestive language. Keep your responses strictly familial.\n");
        }

        if (MCA.language != null) {
            sb.append("Match the language of the player, and use ").append(MCA.language).append(" when unsure.");
        }

        List<TriggerCommandInfo> validCommands;
        if (config.villagerChatAIUseTools) {
            validCommands = TriggerCommandInfos.triggerCommands.stream()
                    .filter(command -> command.isActive == null || command.isActive.test(player, villager))
                    .toList();
            MCA.LOGGER.info("Valid commands: {}", validCommands.stream().map(command -> command.command).toList());
        } else {
            validCommands = List.of();
        }
        if (!validCommands.isEmpty()) {
            String structureExample = new Gson().toJson(new StructuredResponse("example message to say", validCommands.getFirst().command));
            sb.append("\n\n");
            sb.append("The reply MUST be in this JSON format: ").append(structureExample).append("\n");
            sb.append("The following commands are valid:\n");
            for (TriggerCommandInfo command : validCommands) {
                sb.append("  * ").append(command.command).append(": ").append(command.description).append("\n");
            }
            sb.append("Only use a command when the player asks for it.");
        }

        String system = sb.toString();
        StringBuilder body = new StringBuilder();
        body.append("{");
        body.append("\"model\": \"").append(config.villagerChatAIModel).append("\",");
        body.append("\"messages\": [");
        if (!config.villagerChatAIFuseSystemPrompt) {
            body.append("{\"role\": \"system\", \"content\": ").append(jsonStringQuote(system)).append("},");
        }
        for (DialogueEntry entry : pastDialogue) {
            body.append("{\"role\": \"").append(entry.role())
                    .append("\", \"name\": \"").append(entry.speakerName())
                    .append("\", \"content\": ").append(jsonStringQuote(entry.content())).append("},");
        }
        String userContent = config.villagerChatAIFuseSystemPrompt ? system + "\n\n" + msg : msg;
        body.append("{\"role\": \"user\", \"name\": \"").append(playerName)
                .append("\", \"content\": ").append(jsonStringQuote(userContent)).append("}");
        body.append("]");
        body.append("}");

        String token = config.villagerChatAIToken;
        if (token.isEmpty() || config.villagerChatAIEndpoint.contains("conczin.net")) {
            token = player.getName().getString();
        }

        return new PreparedRequest(config.villagerChatAIEndpoint, body.toString(), token);
    }

    private Optional<String> applyAnswer(ServerPlayer player, VillagerEntityMCA villager, String msg, Answer message) {
        if (message.error == null) {
            if (message.answer != null) {
                List<DialogueEntry> pastDialogue = memory.computeIfAbsent(villager.getUUID(), key -> new LinkedList<>());
                pastDialogue.add(new DialogueEntry("user", Messenger.getName(player), msg));
                pastDialogue.add(new DialogueEntry("assistant", villager.getName().getString(), message.answer.message != null ? message.answer.message : "..."));

                if (message.answer.optionalCommand() != null && !message.answer.optionalCommand().isEmpty()) {
                    TriggerCommandInfos.findCommand(message.answer.optionalCommand(), player, villager)
                            .ifPresent(command -> command.call.accept(player, villager));
                }
            }
            return Optional.ofNullable(message.answer != null ? message.answer.message : null);
        }

        if (message.error.equals("invalid_model")) {
            player.sendSystemMessage(Component.literal("Invalid model!").withStyle(ChatFormatting.RED));
        } else if (message.error.equals("limit")) {
            MutableComponent styled = Component.translatable("mca.limit.patreon").withStyle(style -> style
                    .withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations#increase-conversation-limit")))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("mca.limit.patreon.hover"))));
            player.sendSystemMessage(styled);
        } else if (message.error.equals("limit_premium")) {
            player.sendSystemMessage(Component.translatable("mca.limit.premium").withStyle(ChatFormatting.RED));
        } else {
            player.sendSystemMessage(Component.literal(message.error).withStyle(ChatFormatting.RED));
        }
        return Optional.empty();
    }

    private void handleFailure(ServerPlayer player, String logMessage, Exception exception) {
        MCA.LOGGER.error(logMessage, exception);
        if (!player.isRemoved()) {
            player.sendSystemMessage(Component.translatable("mca.ai_broken").withStyle(ChatFormatting.RED));
        }
    }

    private record PreparedRequest(String url, String body, String token) {
    }

    private record DialogueEntry(String role, String speakerName, String content) {
    }

    public record StructuredResponse(@Nullable String message, String optionalCommand) {
    }

    public record Answer(StructuredResponse answer, String error) {
    }
}
