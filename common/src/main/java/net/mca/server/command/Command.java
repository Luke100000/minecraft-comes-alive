package net.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.mca.Config;
import net.mca.MCA;
import net.mca.client.LanguageMap;
import net.mca.client.OnlineSpeechManager;
import net.mca.client.SpeechManager;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.ChatAI;
import net.mca.entity.ai.chatAI.GPT3;
import net.mca.entity.ai.relationship.Personality;
import net.mca.network.s2c.OpenGuiRequest;
import net.mca.server.ServerInteractionManager;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.LanguageDefinition;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class Command {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal(MCA.MOD_ID)
                .then(register("help", Command::displayHelp))
                .then(register("propose").then(CommandManager.argument("target", EntityArgumentType.player()).executes(Command::propose)))
                .then(register("accept").then(CommandManager.argument("target", EntityArgumentType.player()).executes(Command::accept)))
                .then(register("proposals", Command::displayProposal))
                .then(register("procreate", Command::procreate))
                .then(register("separate", Command::separate))
                .then(register("reject").then(CommandManager.argument("target", EntityArgumentType.player()).executes(Command::reject)))
                .then(register("editor", Command::editor))
                .then(register("destiny", Command::destiny))
                .then(register("mail", Command::mail))
                .then(register("verify").then(CommandManager.argument("email", StringArgumentType.greedyString()).executes(Command::verify)))
                .then(register("chatAI")
                        .requires(p -> p.hasPermissionLevel(2) || p.getServer().isSingleplayer())
                        .executes(Command::chatAIHelp)
                        .then(CommandManager.literal("disable")
                                .executes(Command::disableChatAI))
                        .then(CommandManager.argument("model", StringArgumentType.string())
                                .executes(c -> Command.chatAI(c.getArgument("model", String.class), (new Config()).villagerChatAIEndpoint, ""))
                                .then(CommandManager.argument("endpoint", StringArgumentType.string())
                                        .executes(c -> Command.chatAI(c.getArgument("model", String.class), c.getArgument("endpoint", String.class), ""))
                                        .then(CommandManager.argument("token", StringArgumentType.string())
                                                .executes(c -> Command.chatAI(c.getArgument("model", String.class), c.getArgument("endpoint", String.class), c.getArgument("token", String.class)))))))
                .then(register("tts")
                        .requires(p -> p.getServer().isSingleplayer())
                        .then(CommandManager.literal("enable").then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(Command::ttsEnable)))
                        .then(CommandManager.literal("scan").then(CommandManager.argument("language", StringArgumentType.string()).requires(p -> p.getPlayer() != null && p.getPlayer().getEntityName().contains("Player")).executes(Command::ttsScan))))
                .then(register("inworldAI")
                        .requires(p -> p.hasPermissionLevel(2) || p.getServer().isSingleplayer())
                        .then(register("keys")
                                .then(CommandManager.argument("api_key", StringArgumentType.string())
                                        .executes(c -> Command.inworldAIKey(c.getArgument("api_key", String.class)))))
                        .then(register("addCharacter")
                                .then(CommandManager.argument("villager_name", StringArgumentType.string())
                                        .then(CommandManager.argument("character_endpoint", StringArgumentType.string())
                                                .executes(c -> Command.inworldAICharacter(c, c.getArgument("villager_name", String.class), c.getArgument("character_endpoint", String.class)))
                                        )
                                )
                        )
                )
        );
    }

    private static int chatAIHelp(CommandContext<ServerCommandSource> context) {
        MutableText styled = (Text.translatable("mca.ai_help")).styled(s -> s
                .withColor(Formatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations")));
        sendMessage(context, styled);
        return chatAI((new Config()).villagerChatAIModel, (new Config()).villagerChatAIEndpoint, (new Config()).villagerChatAIToken);
    }

    private static int inworldAIKey(String apiKey) {
        Config.getInstance().inworldAIToken = apiKey;
        Config.getInstance().save();
        return 0;
    }

    private static int inworldAICharacter(CommandContext<ServerCommandSource> context, String name, String endpoint) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Optional<VillagerEntityMCA> optionalVillager = ChatAI.findVillagerInArea(player, name);
        optionalVillager.ifPresent(v -> {
            Config.getInstance().inworldAIResourceNames.put(v.getUuid(), endpoint);
            ChatAI.clearStrategy(v.getUuid());
            Config.getInstance().save();
        });
        return 0;
    }

    private static int chatAI(String model, String endpoint, String token) {
        Config.getInstance().enableVillagerChatAI = true;
        Config.getInstance().villagerChatAIModel = model;
        Config.getInstance().villagerChatAIEndpoint = endpoint;
        Config.getInstance().villagerChatAIToken = token;
        Config.getInstance().save();
        return 0;
    }

    private static int disableChatAI(CommandContext<ServerCommandSource> c) {
        Config.getInstance().enableVillagerChatAI = false;
        Config.getInstance().save();
        return 0;
    }

    private static int ttsEnable(CommandContext<ServerCommandSource> ctx) {
        Config.getInstance().enableOnlineTTS = BoolArgumentType.getBool(ctx, "enabled");
        Config.getInstance().save();
        return 0;
    }

    private static boolean couldBePersonalityRelated(String phrase) {
        for (Personality value : Personality.values()) {
            if (phrase.contains(value.name().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int ttsScan(CommandContext<ServerCommandSource> ctx) {
        String language = ctx.getArgument("language", String.class);
        for (Map.Entry<String, LanguageDefinition> definition : MinecraftClient.getInstance().getLanguageManager().getAllLanguages().entrySet()) {
            String ttsLang = LanguageMap.LANGUAGE_MAP.getOrDefault(definition.getKey(), "");
            if (!ttsLang.isEmpty() && (language.equals("all") || ttsLang.equals(language))) {
                MinecraftClient.getInstance().getLanguageManager().setLanguage(definition.getKey());
                try {
                    MinecraftClient.getInstance().reloadResources().get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }

                for (Map.Entry<String, String> text : MCA.translations.entrySet()) {
                    String key = text.getKey();
                    if ((key.contains("dialogue.") || key.contains("interaction.") || key.contains("villager.")) && !couldBePersonalityRelated(key)) {
                        String hash = OnlineSpeechManager.INSTANCE.getHash(text.getValue());
                        OnlineSpeechManager.INSTANCE.downloadAudio(ttsLang, "male_" + (SpeechManager.TOTAL_VOICES - 1), text.getValue(), hash, true);
                    }
                }
            }
        }

        return 0;
    }

    private static int editor(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            return 1;
        }
        if (ctx.getSource().hasPermissionLevel(2) || Config.getInstance().allowFullPlayerEditor) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.VILLAGER_EDITOR, player), player);
            return 0;
        } else if (Config.getInstance().allowLimitedPlayerEditor) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.LIMITED_VILLAGER_EDITOR, player), player);
            return 0;
        } else {
            sendMessage(ctx, Text.translatable("command.no_permission").formatted(Formatting.RED));
            return 1;
        }
    }

    private static int destiny(CommandContext<ServerCommandSource> ctx) {
        if (ctx.getSource().hasPermissionLevel(2) || Config.getInstance().allowDestinyCommandOnce) {
            ServerPlayerEntity player = ctx.getSource().getPlayer();
            if (player != null && !PlayerSaveData.get(player).isEntityDataSet() || Config.getInstance().allowDestinyCommandMoreThanOnce) {
                ServerInteractionManager.launchDestiny(player);
                return 0;
            } else {
                sendMessage(ctx, Text.translatable("command.only_one_destiny").formatted(Formatting.RED));
                return 1;
            }
        } else {
            sendMessage(ctx, Text.translatable("command.no_permission").formatted(Formatting.RED));
            return 1;
        }
    }

    private static int mail(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            return 1;
        }
        PlayerSaveData data = PlayerSaveData.get(player);
        if (data.hasMail()) {
            while (data.hasMail()) {
                player.getInventory().offerOrDrop(data.getMail());
            }
        } else {
            sendMessage(ctx, Text.translatable("command.no_mail"));
        }
        return 0;
    }

    private static int verify(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();

        CompletableFuture.runAsync(() -> {
            // build http request
            Map<String, String> params = new HashMap<>();
            params.put("email", StringArgumentType.getString(ctx, "email"));
            assert player != null;
            params.put("player", player.getName().getString());

            // encode and create url
            String encodedURL = params.keySet().stream()
                    .map(key -> key + "=" + URLEncoder.encode(params.get(key), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&", Config.getInstance().villagerChatAIEndpoint.replace("v1/mca/chat", "v1/mca/verify") + "?", ""));

            GPT3.Answer request = GPT3.request(encodedURL);

            if (request.answer().equals("success")) {
                sendMessage(ctx, Text.translatable("command.verify.success").formatted(Formatting.GREEN));
            } else if (request.answer().equals("failed")) {
                sendMessage(ctx, Text.translatable("command.verify.failed").formatted(Formatting.RED));
            } else {
                sendMessage(ctx, Text.translatable("command.verify.crashed").formatted(Formatting.RED));
            }
        });
        return 0;
    }

    private static int displayHelp(CommandContext<ServerCommandSource> ctx) {
        sendMessage(ctx, Formatting.DARK_RED + "--- " + Formatting.GOLD + "PLAYER COMMANDS" + Formatting.DARK_RED + " ---");
        sendMessage(ctx, Formatting.WHITE + " /mca editor" + Formatting.GOLD + " - Choose your genetics and stuff.");
        sendMessage(ctx, Formatting.WHITE + " /mca propose <PlayerName>" + Formatting.GOLD + " - Proposes marriage to the given player.");
        sendMessage(ctx, Formatting.WHITE + " /mca proposals " + Formatting.GOLD + " - Shows all active proposals.");
        sendMessage(ctx, Formatting.WHITE + " /mca accept <PlayerName>" + Formatting.GOLD + " - Accepts the player's marriage request.");
        sendMessage(ctx, Formatting.WHITE + " /mca reject <PlayerName>" + Formatting.GOLD + " - Rejects the player's marriage request.");
        sendMessage(ctx, Formatting.WHITE + " /mca procreate " + Formatting.GOLD + " - Starts procreation.");
        sendMessage(ctx, Formatting.WHITE + " /mca separate " + Formatting.GOLD + " - Ends your marriage.");
        sendMessage(ctx, Formatting.DARK_RED + "--- " + Formatting.GOLD + "GLOBAL COMMANDS" + Formatting.DARK_RED + " ---");
        sendMessage(ctx, Formatting.WHITE + " /mca help " + Formatting.GOLD + " - Shows this list of commands.");
        return 0;
    }

    private static int propose(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        ServerInteractionManager.getInstance().sendProposal(ctx.getSource().getPlayer(), target);

        return 0;
    }

    private static int accept(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        ServerInteractionManager.getInstance().acceptProposal(ctx.getSource().getPlayer(), target);
        return 0;
    }

    private static int displayProposal(CommandContext<ServerCommandSource> ctx) {
        ServerInteractionManager.getInstance().listProposals(ctx.getSource().getPlayer());

        return 0;
    }

    private static int procreate(CommandContext<ServerCommandSource> ctx) {
        ServerInteractionManager.getInstance().procreate(ctx.getSource().getPlayer());
        return 0;
    }

    private static int separate(CommandContext<ServerCommandSource> ctx) {
        ServerInteractionManager.getInstance().endMarriage(ctx.getSource().getPlayer());
        return 0;
    }

    private static int reject(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        ServerInteractionManager.getInstance().rejectProposal(ctx.getSource().getPlayer(), target);
        return 0;
    }


    private static ArgumentBuilder<ServerCommandSource, ?> register(String name, com.mojang.brigadier.Command<ServerCommandSource> cmd) {
        return CommandManager.literal(name).requires(cs -> cs.hasPermissionLevel(0)).executes(cmd);
    }

    private static ArgumentBuilder<ServerCommandSource, ?> register(String name) {
        return CommandManager.literal(name).requires(cs -> cs.hasPermissionLevel(0));
    }

    private static void sendMessage(CommandContext<ServerCommandSource> ctx, String message) {
        sendMessage(ctx, Text.literal(message));
    }

    private static void sendMessage(CommandContext<ServerCommandSource> ctx, Text message) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            player.sendMessage(message);
        }
    }
}
