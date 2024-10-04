package net.mca.entity.ai.chatAI;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.gpt3Modules.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import org.apache.commons.io.IOUtils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GPT3 implements ChatAIStrategy {
    private static final int MIN_MEMORY = 100;
    private static final int MAX_MEMORY = 600;
    private static final int MAX_MEMORY_TIME = 20 * 60 * 45;

    private static final Map<UUID, List<Pair<String, String>>> memory = new HashMap<>();
    private static final Map<UUID, Long> lastInteractions = new HashMap<>();

    public static String translate(String phrase) {
        return phrase.replace("_", " ").toLowerCase(Locale.ROOT).replace("mca.", "");
    }

    public record Answer(String answer, String error) {
    }

    private static HttpURLConnection getHttpURLConnection(String url, String token) throws IOException {
        HttpURLConnection con = (HttpURLConnection) (new URL(url)).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.toString());
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + token);

        // Enable input and output streams
        con.setDoOutput(true);
        return con;
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

            // parse json
            JsonObject map = JsonParser.parseString(body).getAsJsonObject();
            String message = map.has("choices") ? map.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").getAsJsonPrimitive("content").getAsString() : null;
            String error = map.has("error") ? map.get("error").getAsString().trim().replace("\n", " ") : null;

            message = message == null ? null : cleanupAnswer(message);

            return new Answer(message, error);
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return new Answer(null, "Unknown error, check log!");
        }
    }

    public static Answer request(String encodedURL) {
        try {
            // receive
            HttpURLConnection con = (HttpURLConnection) (new URL(encodedURL)).openConnection();
            con.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.toString());
            InputStream response = con.getInputStream();
            String body = IOUtils.toString(response, StandardCharsets.UTF_8);

            // parse json
            JsonObject map = JsonParser.parseString(body).getAsJsonObject();
            String answer = map.has("answer") ? map.get("answer").getAsString().trim().replace("\n", " ") : "";
            String error = map.has("error") ? map.get("error").getAsString().trim().replace("\n", " ") : null;

            return new Answer(answer, error);
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return new Answer(null, "unknown");
        }
    }

    public Optional<String> answer(ServerPlayerEntity player, VillagerEntityMCA villager, String msg) {
        try {
            Config config = Config.getInstance();
            boolean isInhouse = config.villagerChatAIEndpoint.contains("conczin.net");

            String playerName = player.getName().getString();
            String villagerName = villager.getName().getString();

            // forgot about last conversation if it's too long ago
            long time = villager.getWorld().getTime();
            if (time > lastInteractions.getOrDefault(villager.getUuid(), 0L) + MAX_MEMORY_TIME) {
                memory.remove(villager.getUuid());
            }
            lastInteractions.put(villager.getUuid(), time);

            // remember phrase
            List<Pair<String, String>> pastDialogue = memory.computeIfAbsent(villager.getUuid(), key -> new LinkedList<>());
            int memory = MIN_MEMORY + Math.min(5, config.villagerChatAIIntelligence) * (MAX_MEMORY - MIN_MEMORY) / 5;
            while (pastDialogue.stream().mapToInt(v -> (v.getRight().length() / 4)).sum() > memory) {
                pastDialogue.remove(0);
            }

            // construct context
            List<String> input = new LinkedList<>();
            PersonalityModule.apply(input, villager, player);
            TraitsModule.apply(input, villager, player);
            RelationModule.apply(input, villager, player);
            VillageModule.apply(input, villager, player);
            EnvironmentModule.apply(input, villager, player);
            PlayerModule.apply(input, villager, player);
            // todo add family

            // gather variables
            Map<String, String> variables = Map.of(
                    "player", playerName,
                    "villager", villagerName
            );

            // construct system message
            StringBuilder sb = new StringBuilder();

            // add control variables
            if (isInhouse || config.villagerChatAIIncludeSessionInformation) {
                long seed = player.getServerWorld().getSeed();
                sb.append("[world_id:").append(seed).append("]");

                sb.append("[player_id:").append(player.getUuid()).append("]");
                sb.append("[character_id:").append(villager.getUuid()).append("]");

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
            } else if (!isInhouse) {
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

            String system = sb.toString();

            // construct body
            StringBuilder body = new StringBuilder();
            body.append("{");
            body.append("\"model\": \"").append(config.villagerChatAIModel).append("\",");
            body.append("\"messages\": [");
            body.append("{\"role\": \"system\", \"content\": ").append(jsonStringQuote(system)).append("},");
            for (Pair<String, String> pair : pastDialogue) {
                String role = pair.getLeft();
                String content = pair.getRight();
                String name = role.equals("user") ? playerName : villagerName;
                body.append("{\"role\": \"").append(role)
                        .append("\", \"name\": \"").append(name)
                        .append("\", \"content\": ").append(jsonStringQuote(content)).append("},");
            }
            body.append("{\"role\": \"user\", \"name\": \"").append(playerName).append("\", \"content\": ").append(jsonStringQuote(msg)).append("}");
            body.append("]}");

            // get access token
            String token = config.villagerChatAIToken;
            if (token.isEmpty() || config.villagerChatAIEndpoint.contains("conczin.net")) {
                token = variables.get("player");
            }

            // encode and create url
            Answer message = post(config.villagerChatAIEndpoint, body.toString(), token);

            if (message.error == null) {
                // remember
                if (message.answer != null) {
                    pastDialogue.add(new Pair<>("user", msg));
                    pastDialogue.add(new Pair<>("assistant", message.answer));
                }
                return Optional.ofNullable(message.answer);
            } else if (message.error.equals("invalid_model")) {
                player.sendMessage(Text.literal("Invalid model!").formatted(Formatting.RED), false);
            } else if (message.error.equals("limit")) {
                MutableText styled = (Text.translatable("mca.limit.patreon")).styled(s -> s
                        .withColor(Formatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations#increase-conversation-limit"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("mca.limit.patreon.hover"))));

                player.sendMessage(styled, false);
            } else if (message.error.equals("limit_premium")) {
                player.sendMessage(Text.translatable("mca.limit.premium").formatted(Formatting.RED), false);
            } else {
                player.sendMessage(Text.literal(message.error).formatted(Formatting.RED), false);
            }
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            player.sendMessage(Text.translatable("mca.ai_broken").formatted(Formatting.RED), false);
        }

        return Optional.empty();
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
        answer = answer.replace("\"", "");
        answer = answer.replace("\n", " ");
        String[] parts = answer.split(":", 2);
        return parts[parts.length - 1].strip();
    }
}
