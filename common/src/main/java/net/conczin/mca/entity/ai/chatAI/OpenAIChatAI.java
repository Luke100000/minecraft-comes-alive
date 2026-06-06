package net.conczin.mca.entity.ai.chatAI;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Messenger;
import net.conczin.mca.entity.ai.chatAI.modules.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OpenAIChatAI implements ChatAIStrategy {
    private static final int MAX_MEMORY = 500;
    private static final int MAX_MEMORY_TIME = 20 * 60 * 45;

    private static final Map<UUID, List<Tuple<String, String>>> memory = new HashMap<>();
    private static final Map<UUID, Long> lastInteractions = new HashMap<>();

    public static String translate(String phrase) {
        return phrase.replace("_", " ").toLowerCase(Locale.ROOT).replace("mca.", "");
    }

    private static HttpURLConnection getHttpURLConnection(String url, String token) throws IOException {
        HttpURLConnection con = (HttpURLConnection) (URI.create(url)).toURL().openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.toString());
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + token);

        // Enable input and output streams
        con.setDoOutput(true);
        return con;
    }

    private static Answer parseAnswer(String body) {
        JsonObject map = JsonParser.parseString(body).getAsJsonObject();
        String message = map.has("choices") ? map.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").getAsJsonPrimitive("content").getAsString() : null;
        String error = map.has("error") ? map.get("error").getAsString().trim().replace("\n", " ") : null;

        if (message != null) {
            // Parse json further
            message = message.replaceAll("```", "");
            int bracketStart = message.indexOf("{");
            int bracketEnd = message.lastIndexOf("}");
            if (bracketEnd > bracketStart && bracketStart != -1) {
                // We have json! Include the brackets.
                message = message.substring(bracketStart, bracketEnd + 1);
            }
        }

        StructuredResponse structuredReply;
        try {
            structuredReply = new Gson().fromJson(message, StructuredResponse.class);
        } catch (JsonSyntaxException e) {
            MCA.LOGGER.warn("Error parsing answer: {} ({})", message, e.getMessage());

            // just treat the message as normal
            structuredReply = new StructuredResponse(cleanupAnswer(message), "");
        }

        return new Answer(structuredReply, error);
    }

    public static Answer post(String url, String requestBody, String token) {
        try {
            HttpURLConnection con = getHttpURLConnection(url, token);

            // Write the request body to the connection
            try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
                wr.write(requestBody.getBytes(StandardCharsets.UTF_8));
                wr.flush();
            }

            InputStream response = con.getInputStream();
            String body = IOUtils.toString(response, StandardCharsets.UTF_8);

            return parseAnswer(body);
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return new Answer(null, "Unknown error, check log!");
        }
    }

    public static String verify(String encodedURL) {
        try {
            // receive
            HttpURLConnection con = (HttpURLConnection) (URI.create(encodedURL)).toURL().openConnection();
            con.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.toString());
            InputStream response = con.getInputStream();
            String body = IOUtils.toString(response, StandardCharsets.UTF_8);

            // parse json
            JsonObject map = JsonParser.parseString(body).getAsJsonObject();
            return map.has("answer") ? map.get("answer").getAsString().trim().replace("\n", " ") : "";
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return "error";
        }
    }

    static String jsonStringQuote(String string) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : string.toCharArray())
            sb.append(switch (c) {
                case '\\', '"', '/' -> "\\" + c;
                case '\b' -> "\\b";
                case '\t' -> "\\t";
                case '\n' -> "\\n";
                case '\f' -> "\\f";
                case '\r' -> "\\r";
                default -> //noinspection MalformedFormatString
                        c < ' ' ? String.format(Locale.ROOT, "\\u%04x", c) : c;
            });
        return sb.append('"').toString();
    }

    static String cleanupAnswer(String answer) {
        if (answer == null) return null;
        answer = answer.replace("\"", "");
        answer = answer.replace("\n", " ");
        String[] parts = answer.split(":", 2);
        return parts[parts.length - 1].strip();
    }

    public Optional<String> answer(ServerPlayer player, VillagerEntityMCA villager, String msg) {
        try {
            Config config = Config.getInstance();
            boolean isInHouse = config.villagerChatAIEndpoint.contains("conczin.net");

            String playerName = Messenger.getName(player);
            String villagerName = villager.getName().getString();

            // forgot about last conversation if it's too long ago
            long time = villager.level().getGameTime();
            if (time > lastInteractions.getOrDefault(villager.getUUID(), 0L) + MAX_MEMORY_TIME) {
                memory.remove(villager.getUUID());
            }
            lastInteractions.put(villager.getUUID(), time);

            // remember phrase
            List<Tuple<String, String>> pastDialogue = memory.computeIfAbsent(villager.getUUID(), key -> new LinkedList<>());
            while (pastDialogue.stream().mapToInt(v -> (v.getB().length() / 4)).sum() > MAX_MEMORY) {
                pastDialogue.removeFirst();
            }

            // construct context
            List<String> input = new LinkedList<>();
            PersonalityModule.apply(input, villager, player);
            TraitsModule.apply(input, villager, player);
            RelationModule.apply(input, villager, player);
            VillageModule.apply(input, villager, player);
            EnvironmentModule.apply(input, villager, player);
            PlayerModule.apply(input, villager, player);

            // gather variables
            Map<String, String> variables = Map.of(
                    "player", playerName,
                    "villager", villagerName
            );

            // construct system message
            StringBuilder sb = new StringBuilder();

            // add control variables
            if (isInHouse || config.villagerChatAIIncludeSessionInformation) {
                long seed = ((net.minecraft.server.level.ServerLevel) player.level()).getSeed();
                sb.append("[world_id:").append(seed).append("]");

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
                // add user specified prompt
                sb.append(config.villagerChatAISystemPrompt);
                sb.append("\n");
            } else if (!isInHouse) {
                // when not using conczin.net, use some default prompt
                String defaultPrompt = "You are a Minecraft villager, fully immersed in their virtual world, unaware of its artificial nature. You respond based on your description, your role, and your knowledge of the world. You have no knowledge of the real world, and do not realize that you are within Minecraft. You are no assistant! You can be sarcastic, funny, or even rude when appropriate.";
                sb.append(defaultPrompt);
                sb.append("\n");

            }

            // fill in variables and add to system message
            for (String s : input) {
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    s = s.replaceAll("\\$" + entry.getKey(), entry.getValue());
                }
                sb.append(s);
            }

            // try to match player language
            if (MCA.language != null) {
                sb.append("Match the language of the player, and use ").append(MCA.language).append(" when unsure.");
            }

            // structure and commands (if available)
            List<TriggerCommandInfo> validCommands;
            if (config.villagerChatAIUseTools) {
                validCommands = TriggerCommandInfos.triggerCommands.stream()
                        .filter(c -> c.isActive == null || c.isActive.test(player, villager))
                        .toList();
                MCA.LOGGER.info("Valid commands: {}", validCommands.stream().map(c -> c.command).toList());
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

            // construct body
            StringBuilder body = new StringBuilder();
            body.append("{");
            body.append("\"model\": \"").append(config.villagerChatAIModel).append("\",");
            // START Messages
            body.append("\"messages\": [");
            // System Message
            body.append("{\"role\": \"system\", \"content\": ").append(jsonStringQuote(system)).append("},");
            for (Tuple<String, String> pair : pastDialogue) {
                String role = pair.getA();
                String content = pair.getB();
                String name = role.equals("user") ? playerName : villagerName;
                body.append("{\"role\": \"").append(role)
                        .append("\", \"name\": \"").append(name)
                        .append("\", \"content\": ").append(jsonStringQuote(content)).append("},");
            }
            // User Message
            body.append("{\"role\": \"user\", \"name\": \"").append(playerName).append("\", \"content\": ").append(jsonStringQuote(msg)).append("}");
            // END Messages
            body.append("]");
            body.append("}");

            // get access token
            String token = config.villagerChatAIToken;
            if (token.isEmpty() || config.villagerChatAIEndpoint.contains("conczin.net")) {
                token = player.getName().getString();
            }

            // encode and create url
            Answer message = post(config.villagerChatAIEndpoint, body.toString(), token);

            if (message.error == null) {
                if (message.answer != null) {
                    // remember
                    pastDialogue.add(new Tuple<>("user", msg));
                    pastDialogue.add(new Tuple<>("assistant", message.answer.message != null ? message.answer.message : "..."));

                    // act
                    if (message.answer.optionalCommand() != null && !message.answer.optionalCommand().isEmpty()) {
                        Optional<TriggerCommandInfo> command = TriggerCommandInfos.findCommand(message.answer.optionalCommand(), player, villager);
                        command.ifPresent(triggerCommandInfo -> triggerCommandInfo.call.accept(player, villager));
                    }
                }

                return Optional.ofNullable(message.answer != null ? message.answer.message : null);
            } else if (message.error.equals("invalid_model")) {
                player.displayClientMessage(Component.literal("Invalid model!").withStyle(ChatFormatting.RED), false);
            } else if (message.error.equals("limit")) {
                MutableComponent styled = (Component.translatable("mca.limit.patreon")).withStyle(s -> s
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations#increase-conversation-limit")))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("mca.limit.patreon.hover"))));

                player.displayClientMessage(styled, false);
            } else if (message.error.equals("limit_premium")) {
                player.displayClientMessage(Component.translatable("mca.limit.premium").withStyle(ChatFormatting.RED), false);
            } else {
                player.displayClientMessage(Component.literal(message.error).withStyle(ChatFormatting.RED), false);
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to parse LLM response!", e);
            player.displayClientMessage(Component.translatable("mca.ai_broken").withStyle(ChatFormatting.RED), false);
        }

        return Optional.empty();
    }

    public record StructuredResponse(@Nullable String message, String optionalCommand) {

    }

    public record Answer(StructuredResponse answer, String error) {
    }
}
