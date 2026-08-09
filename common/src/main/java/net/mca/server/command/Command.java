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
import net.mca.entity.ai.chatAI.ChatAIContext;
import net.mca.entity.ai.chatAI.OpenAIChatAI;
import net.mca.network.s2c.ChatAIContextResponse;
import net.mca.network.s2c.OpenGuiRequest;
import net.mca.server.ServerInteractionManager;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Command {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal(MCA.MOD_ID)
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
                        .executes(Command::chatAIHelp)
                        .then(CommandManager.literal("context")
                                .requires(ChatAIContext::canEdit)
                                .executes(Command::openChatAIContext))
                        .then(CommandManager.literal("disable")
                                .requires(Command::hasChatAIAdminPermission)
                                .executes(Command::disableChatAI))
                        .then(CommandManager.literal("default")
                                .requires(Command::hasChatAIAdminPermission)
                                .executes(c -> Command.enableChatAI(c, "default", (new Config()).villagerChatAIEndpoint, "")))
                        .then(CommandManager.literal("player2")
                                .requires(Command::hasChatAIAdminPermission)
                                .executes(Command::setupPlayer2))
                        .then(register("inworldAI")
                                .requires(Command::hasChatAIAdminPermission)
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
                        .then(CommandManager.argument("model", StringArgumentType.string())
                                .requires(Command::hasChatAIAdminPermission)
                                .executes(c -> Command.enableChatAI(c, c.getArgument("model", String.class), (new Config()).villagerChatAIEndpoint, ""))
                                .then(CommandManager.argument("endpoint", StringArgumentType.string())
                                        .executes(c -> Command.enableChatAI(c, c.getArgument("model", String.class), c.getArgument("endpoint", String.class), ""))
                                        .then(CommandManager.argument("token", StringArgumentType.string())
                                                .executes(c -> Command.enableChatAI(c, c.getArgument("model", String.class), c.getArgument("endpoint", String.class), c.getArgument("token", String.class)))))))
                .then(register("tts")
                        .requires(p -> p.getServer().isSingleplayer())
                        .then(CommandManager.literal("default").executes(ctx -> ttsEnable(ctx, "default")))
                        .then(CommandManager.literal("elevenlabs").executes(ctx -> ttsEnable(ctx, "elevenlabs")))
                        .then(CommandManager.literal("realtime").executes(ctx -> ttsEnable(ctx, "realtime")))
                        .then(CommandManager.literal("disable").executes(Command::ttsDisable))
                )
        );
    }

    private static boolean hasChatAIAdminPermission(ServerCommandSource source) {
        return source.hasPermissionLevel(2) || source.getServer().isSingleplayer();
    }

    private static int openChatAIContext(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }

        Optional<VillagerEntityMCA> villager = ChatAI.findClosestVillager(player);
        Optional<Village> village = villager.flatMap(Village::findNearest);
        if (village.isEmpty()) {
            village = VillageManager.get((net.minecraft.server.world.ServerWorld) player.getWorld()).findNearestVillage(player);
        }

        NetworkHandler.sendToPlayer(new ChatAIContextResponse(
                player.getWorld().getRegistryKey().getValue().toString(),
                villager.isPresent(),
                villager.map(VillagerEntityMCA::getUuid).orElse(new UUID(0L, 0L)),
                villager.map(v -> v.getName().getString()).orElse(""),
                villager.map(VillagerEntityMCA::getChatAIPrompt).orElse(""),
                villager.map(v -> v.getNickname(player.getUuid())).orElse(""),
                player.getName().getString(),
                PlayerSaveData.get(player).getChatAIPrompt(),
                village.isPresent(),
                village.map(Village::getId).orElse(-1),
                village.map(Village::getName).orElse(""),
                village.map(Village::getChatAIPrompt).orElse(""),
                Config.getInstance().villagerChatAISystemPrompt
        ), player);
        return 1;
    }

    private static int chatAIHelp(CommandContext<ServerCommandSource> ctx) {
        if (!hasChatAIAdminPermission(ctx.getSource())) {
            sendMessage(ctx, Text.translatable("command.no_permission").formatted(Formatting.RED));
            return 0;
        }
        return enableChatAI(ctx, (new Config()).villagerChatAIModel, (new Config()).villagerChatAIEndpoint, (new Config()).villagerChatAIToken);
    }

    private static int inworldAIKey(String apiKey) {
        Config.getInstance().inworldAIToken = apiKey;
        Config.getInstance().save();
        return 0;
    }

    private static int inworldAICharacter(CommandContext<ServerCommandSource> ctx, String name, String endpoint) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        Optional<VillagerEntityMCA> optionalVillager = ChatAI.findVillagerInArea(player, name);
        optionalVillager.ifPresent(v -> {
            Config.getInstance().inworldAIResourceNames.put(v.getUuid(), endpoint);
            ChatAI.clearStrategy(v.getUuid());
            Config.getInstance().save();
        });
        return 0;
    }

    private static int enableChatAI(CommandContext<ServerCommandSource> ctx, String model, String endpoint, String token) {
        Config.getInstance().enableVillagerChatAI = true;
        Config.getInstance().villagerChatAIModel = model;
        Config.getInstance().villagerChatAIEndpoint = endpoint;
        Config.getInstance().villagerChatAIToken = token;
        Config.getInstance().save();

        if (model.equals("default")) {
            sendMessage(ctx, Text.translatable("mca.ai_help").styled(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations"))
            ));
        } else {
            sendMessage(ctx, "command.chat_ai.enabled");
        }
        return 0;
    }

    private static int disableChatAI(CommandContext<ServerCommandSource> c) {
        Config.getInstance().enableVillagerChatAI = false;
        Config.getInstance().save();
        sendMessage(c, "command.chat_ai.disabled");
        return 0;
    }

    private static int setupPlayer2(CommandContext<ServerCommandSource> ctx) {
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

        sendMessage(ctx, Text.translatable("command.chat_ai.player2").styled(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://player2.game/"))));
        return 0;
    }

    private static int ttsEnable(CommandContext<ServerCommandSource> ctx, String model) {
        Config.getInstance().enableOnlineTTS = true;
        Config.getInstance().onlineTTSModel = model;
        Config.getInstance().save();
        sendMessage(ctx, Text.translatable("command.tts.enabled." + model));
        return 0;
    }

    private static int ttsDisable(CommandContext<ServerCommandSource> ctx) {
        Config.getInstance().enableOnlineTTS = false;
        Config.getInstance().save();

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
            sendMessage(ctx, "command.no_mail");
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

            String request = OpenAIChatAI.verify(encodedURL);

            if (request.equals("success")) {
                sendMessage(ctx, Text.translatable("command.verify.success").formatted(Formatting.GREEN));
            } else if (request.equals("failed")) {
                sendMessage(ctx, Text.translatable("command.verify.failed").formatted(Formatting.RED));
            } else {
                sendMessage(ctx, Text.translatable("command.verify.crashed").formatted(Formatting.RED));
            }
        });
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
        sendMessage(ctx, Text.translatable(message));
    }

    private static void sendMessage(CommandContext<ServerCommandSource> ctx, Text message) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            player.sendMessage(message);
        }
    }
}
