package net.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.mca.Config;
import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.ChatAI;
import net.mca.entity.ai.chatAI.OpenAIChatAI;
import net.mca.network.s2c.OpenGuiRequest;
import net.mca.server.ServerInteractionManager;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Command {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MCA.MOD_ID)
                .then(register("propose").then(Commands.argument("target", EntityArgument.player()).executes(Command::propose)))
                .then(register("accept").then(Commands.argument("target", EntityArgument.player()).executes(Command::accept)))
                .then(register("proposals", Command::displayProposal))
                .then(register("procreate", Command::procreate))
                .then(register("separate", Command::separate))
                .then(register("reject").then(Commands.argument("target", EntityArgument.player()).executes(Command::reject)))
                .then(register("editor", Command::editor))
                .then(register("destiny", Command::destiny))
                .then(register("mail", Command::mail))
                .then(register("verify").then(Commands.argument("email", StringArgumentType.greedyString()).executes(Command::verify)))
                .then(register("chatAI")
                        .requires(p -> p.hasPermission(2) || p.getServer().isSingleplayer())
                        .executes(Command::chatAIHelp)
                        .then(Commands.literal("disable")
                                .executes(Command::disableChatAI))
                        .then(Commands.literal("default")
                                .executes(c -> Command.enableChatAI(c, "default", (new Config()).villagerChatAIEndpoint, "")))
                        .then(Commands.literal("player2")
                                .executes(Command::setupPlayer2))
                        .then(register("inworldAI")
                                .requires(p -> p.hasPermission(2) || p.getServer().isSingleplayer())
                                .then(register("keys")
                                        .then(Commands.argument("api_key", StringArgumentType.string())
                                                .executes(c -> Command.inworldAIKey(c.getArgument("api_key", String.class)))))
                                .then(register("addCharacter")
                                        .then(Commands.argument("villager_name", StringArgumentType.string())
                                                .then(Commands.argument("character_endpoint", StringArgumentType.string())
                                                        .executes(c -> Command.inworldAICharacter(c, c.getArgument("villager_name", String.class), c.getArgument("character_endpoint", String.class)))
                                                )
                                        )
                                )
                        )
                        .then(Commands.argument("model", StringArgumentType.string())
                                .executes(c -> Command.enableChatAI(c, c.getArgument("model", String.class), (new Config()).villagerChatAIEndpoint, ""))
                                .then(Commands.argument("endpoint", StringArgumentType.string())
                                        .executes(c -> Command.enableChatAI(c, c.getArgument("model", String.class), c.getArgument("endpoint", String.class), ""))
                                        .then(Commands.argument("token", StringArgumentType.string())
                                                .executes(c -> Command.enableChatAI(c, c.getArgument("model", String.class), c.getArgument("endpoint", String.class), c.getArgument("token", String.class)))))))
                .then(register("tts")
                        .requires(p -> p.getServer().isSingleplayer())
                        .then(Commands.literal("default").executes(ctx -> ttsEnable(ctx, "default")))
                        .then(Commands.literal("elevenlabs").executes(ctx -> ttsEnable(ctx, "elevenlabs")))
                        .then(Commands.literal("realtime").executes(ctx -> ttsEnable(ctx, "realtime")))
                        .then(Commands.literal("disable").executes(Command::ttsDisable))
                )
        );
    }

    private static int chatAIHelp(CommandContext<CommandSourceStack> ctx) {
        return enableChatAI(ctx, (new Config()).villagerChatAIModel, (new Config()).villagerChatAIEndpoint, (new Config()).villagerChatAIToken);
    }

    private static int inworldAIKey(String apiKey) {
        Config.getInstance().inworldAIToken = apiKey;
        Config.getInstance().save();
        return 0;
    }

    private static int inworldAICharacter(CommandContext<CommandSourceStack> ctx, String name, String endpoint) {
        ServerPlayer player = ctx.getSource().getPlayer();
        Optional<VillagerEntityMCA> optionalVillager = ChatAI.findVillagerInArea(player, name);
        optionalVillager.ifPresent(v -> {
            Config.getInstance().inworldAIResourceNames.put(v.getUUID(), endpoint);
            ChatAI.clearStrategy(v.getUUID());
            Config.getInstance().save();
        });
        return 0;
    }

    private static int enableChatAI(CommandContext<CommandSourceStack> ctx, String model, String endpoint, String token) {
        Config.getInstance().enableVillagerChatAI = true;
        Config.getInstance().villagerChatAIModel = model;
        Config.getInstance().villagerChatAIEndpoint = endpoint;
        Config.getInstance().villagerChatAIToken = token;
        Config.getInstance().save();

        if (model.equals("default")) {
            sendMessage(ctx, Component.translatable("mca.ai_help").withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations"))
            ));
        } else {
            sendMessage(ctx, "command.chat_ai.enabled");
        }
        return 0;
    }

    private static int disableChatAI(CommandContext<CommandSourceStack> c) {
        Config.getInstance().enableVillagerChatAI = false;
        Config.getInstance().save();
        sendMessage(c, "command.chat_ai.disabled");
        return 0;
    }

    private static int setupPlayer2(CommandContext<CommandSourceStack> ctx) {
        // Use player2s endpoint
        Config.getInstance().enableVillagerChatAI = true;
        Config.getInstance().villagerChatAIModel = "player2";
        Config.getInstance().villagerChatAIEndpoint = "http://127.0.0.1:4315/v1/chat/completions";
        Config.getInstance().villagerChatAIToken = "";
        Config.getInstance().villagerChatAIUseTools = true;

        // And turn on TTS
        Config.getInstance().enableOnlineTTS = true;
        Config.getInstance().onlineTTSModel = "player2";

        Config.getInstance().save();

        sendMessage(ctx, Component.translatable("command.chat_ai.player2").withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://player2.game/"))));
        return 0;
    }

    private static int ttsEnable(CommandContext<CommandSourceStack> ctx, String model) {
        Config.getInstance().enableOnlineTTS = true;
        Config.getInstance().onlineTTSModel = model;
        Config.getInstance().save();
        sendMessage(ctx, Component.translatable("command.tts.enabled." + model));
        return 0;
    }

    private static int ttsDisable(CommandContext<CommandSourceStack> ctx) {
        Config.getInstance().enableOnlineTTS = false;
        Config.getInstance().save();

        return 0;
    }

    private static int editor(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 1;
        }
        if (ctx.getSource().hasPermission(2) || Config.getInstance().allowFullPlayerEditor) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.VILLAGER_EDITOR, player), player);
            return 0;
        } else if (Config.getInstance().allowLimitedPlayerEditor) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.LIMITED_VILLAGER_EDITOR, player), player);
            return 0;
        } else {
            sendMessage(ctx, Component.translatable("command.no_permission").withStyle(ChatFormatting.RED));
            return 1;
        }
    }

    private static int destiny(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().hasPermission(2) || Config.getInstance().allowDestinyCommandOnce) {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player != null && !PlayerSaveData.get(player).isEntityDataSet() || Config.getInstance().allowDestinyCommandMoreThanOnce) {
                ServerInteractionManager.launchDestiny(player);
                return 0;
            } else {
                sendMessage(ctx, Component.translatable("command.only_one_destiny").withStyle(ChatFormatting.RED));
                return 1;
            }
        } else {
            sendMessage(ctx, Component.translatable("command.no_permission").withStyle(ChatFormatting.RED));
            return 1;
        }
    }

    private static int mail(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 1;
        }
        PlayerSaveData data = PlayerSaveData.get(player);
        if (data.hasMail()) {
            while (data.hasMail()) {
                player.getInventory().placeItemBackInInventory(data.getMail());
            }
        } else {
            sendMessage(ctx, "command.no_mail");
        }
        return 0;
    }

    private static int verify(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();

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

            String request = OpenAIChatAI.verify(encodedURL);

            if (request.equals("success")) {
                sendMessage(ctx, Component.translatable("command.verify.success").withStyle(ChatFormatting.GREEN));
            } else if (request.equals("failed")) {
                sendMessage(ctx, Component.translatable("command.verify.failed").withStyle(ChatFormatting.RED));
            } else {
                sendMessage(ctx, Component.translatable("command.verify.crashed").withStyle(ChatFormatting.RED));
            }
        });
        return 0;
    }

    private static int propose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ServerInteractionManager.getInstance().sendProposal(ctx.getSource().getPlayer(), target);

        return 0;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ServerInteractionManager.getInstance().acceptProposal(ctx.getSource().getPlayer(), target);
        return 0;
    }

    private static int displayProposal(CommandContext<CommandSourceStack> ctx) {
        ServerInteractionManager.getInstance().listProposals(ctx.getSource().getPlayer());

        return 0;
    }

    private static int procreate(CommandContext<CommandSourceStack> ctx) {
        ServerInteractionManager.getInstance().procreate(ctx.getSource().getPlayer());
        return 0;
    }

    private static int separate(CommandContext<CommandSourceStack> ctx) {
        ServerInteractionManager.getInstance().endMarriage(ctx.getSource().getPlayer());
        return 0;
    }

    private static int reject(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ServerInteractionManager.getInstance().rejectProposal(ctx.getSource().getPlayer(), target);
        return 0;
    }


    private static ArgumentBuilder<CommandSourceStack, ?> register(String name, com.mojang.brigadier.Command<CommandSourceStack> cmd) {
        return Commands.literal(name).requires(cs -> cs.hasPermission(0)).executes(cmd);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> register(String name) {
        return Commands.literal(name).requires(cs -> cs.hasPermission(0));
    }

    private static void sendMessage(CommandContext<CommandSourceStack> ctx, String message) {
        sendMessage(ctx, Component.translatable(message));
    }

    private static void sendMessage(CommandContext<CommandSourceStack> ctx, Component message) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }
}
