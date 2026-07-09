package net.mca.server.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.mca.Config;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.item.BabyItem;
import net.mca.server.SpawnQueue;
import net.mca.server.world.data.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.stream.Stream;

import static net.minecraft.util.Formatting.*;

public class AdminCommand {
    private static final List<NbtCompound> storedVillagers = new ArrayList<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("mca-admin")
                .then(register("help", AdminCommand::displayHelp))
                .then(register("clearLoadedVillagers", AdminCommand::clearLoadedVillagers))
                .then(register("restoreClearedVillagers", AdminCommand::restoreClearedVillagers))
                .then(register("forceBuildingType").then(CommandManager.argument("type", StringArgumentType.string()).executes(AdminCommand::forceBuildingType)).executes(AdminCommand::clearForcedBuildingType))
                .then(register("forceFullHearts", AdminCommand::forceFullHearts))
                .then(register("forceBabyGrowth", AdminCommand::forceBabyGrowth))
                .then(register("forceChildGrowth", AdminCommand::forceChildGrowth))
                .then(register("incrementHearts", AdminCommand::incrementHearts))
                .then(register("decrementHearts", AdminCommand::decrementHearts))
                .then(register("resetPlayerData", AdminCommand::resetPlayerData))
                .then(register("overrideVillageRequirements")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(AdminCommand::overrideVillageRequirements))))
                .then(register("resetMarriage", AdminCommand::resetMarriage))
                .then(register("listVillages", AdminCommand::listVillages))
                .then(register("assumeNameDead").then(CommandManager.argument("name", StringArgumentType.string()).executes(AdminCommand::assumeNameDead)))
                .then(register("assumeUuidDead").then(CommandManager.argument("uuid", UuidArgumentType.uuid()).executes(AdminCommand::assumeUuidDead)))
                .then(register("removeVillageWithId").then(CommandManager.argument("id", IntegerArgumentType.integer()).executes(AdminCommand::removeVillageWithId)))
                .then(register("convertVanillaVillagers").then(CommandManager.argument("radius", IntegerArgumentType.integer()).executes(AdminCommand::convertVanillaVillagers)))
                .then(register("removeVillage").then(CommandManager.argument("name", StringArgumentType.string()).executes(AdminCommand::removeVillage)))
                .then(register("buildingProcessingRate").then(CommandManager.argument("cooldown", IntegerArgumentType.integer()).executes(AdminCommand::buildingProcessingRate)))
                .requires((serverCommandSource) -> serverCommandSource.hasPermissionLevel(2))
        );
    }

    private static int listVillages(CommandContext<ServerCommandSource> ctx) {
        //remove from villages
        for (Village village : VillageManager.get(ctx.getSource().getWorld())) {
            final BlockPos pos = village.getBox().getCenter();
            success(String.format(Locale.ROOT, "%d: %s with %d buildings and %d/%d villager(s)",
                            village.getId(),
                            village.getName(),
                            village.getBuildings().size(),
                            village.getPopulation(),
                            village.getMaxPopulation()
                    ), ctx,
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.coordinates.tooltip")),
                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + pos.getX() + " ~ " + pos.getZ()));
        }
        return 0;
    }

    private static int assumeNameDead(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        //remove spouse too
        FamilyTree tree = FamilyTree.get(ctx.getSource().getWorld());
        List<FamilyTreeNode> collect = tree.getAllWithName(name).filter(n -> !n.isDeceased()).toList();
        if (collect.isEmpty()) {
            fail("Villager does not exist.", ctx);
        } else if (collect.size() == 1) {
            collect.get(0).setDeceased(true);
            assumeDead(ctx, collect.get(0).id());
            success("Villager has been marked as deceased", ctx);
        } else {
            fail("Villager not unique, use uuid!", ctx);
        }
        return 0;
    }

    private static int assumeUuidDead(CommandContext<ServerCommandSource> ctx) {
        UUID uuid = UuidArgumentType.getUuid(ctx, "uuid");
        FamilyTree tree = FamilyTree.get(ctx.getSource().getWorld());
        Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
        if (node.isPresent()) {
            node.get().setDeceased(true);
            assumeDead(ctx, uuid);
            success("Villager has been marked as deceased", ctx);
        } else {
            fail("Villager does not exist.", ctx);
        }
        return 0;
    }

    private static void assumeDead(CommandContext<ServerCommandSource> ctx, UUID uuid) {
        for (Village village : VillageManager.get(ctx.getSource().getWorld())) {
            village.removeResident(uuid);
        }

        FamilyTree tree = FamilyTree.get(ctx.getSource().getWorld());
        Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
        node.filter(n -> n.partner() != null).ifPresent(n -> n.updatePartner(null, RelationshipState.WIDOW));

        //remove from player spouse
        ctx.getSource().getWorld().getPlayers().forEach(player -> {
            PlayerSaveData playerData = PlayerSaveData.get(player);
            if (playerData.getPartnerUUID().orElse(Util.NIL_UUID).equals(uuid)) {
                playerData.endRelationShip(RelationshipState.SINGLE);
            }
        });
    }

    private static int removeVillageWithId(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        if (VillageManager.get(ctx.getSource().getWorld()).removeVillage(id)) {
            success("Village deleted.", ctx);
        } else {
            fail("Village with this ID does not exist.", ctx);
        }
        return 0;
    }

    private static int convertVanillaVillagers(CommandContext<ServerCommandSource> ctx) {
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        ServerWorld world = ctx.getSource().getWorld();
        world.getEntitiesByType(EntityType.VILLAGER, x -> true).stream().map(VillagerEntity.class::cast).forEach(v -> {
            if (v.distanceTo(ctx.getSource().getEntity()) < radius) {
                SpawnQueue.getInstance().convert(v);
            }
        });
        return 0;
    }

    private static int setBuildingType(CommandContext<ServerCommandSource> ctx, String type) {
        PlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        VillageManager villages = VillageManager.get(ctx.getSource().getWorld());
        Optional<Village> village = villages.findNearestVillage(player);

        Optional<Building> building = village.flatMap(v -> v.getBuildings().values().stream().filter((b) ->
                b.containsPos(player.getBlockPos())).findAny());
        if (building.isPresent()) {
            if (building.get().getType().equals(type)) {
                building.get().setTypeForced(false);
                building.get().determineType();
            } else {
                building.get().setTypeForced(true);
                building.get().setType(type);
            }
        } else {
            fail(Text.translatable("blueprint.noBuilding").getString(), ctx);
        }
        return 0;
    }

    private static int forceBuildingType(CommandContext<ServerCommandSource> ctx) {
        return setBuildingType(ctx, StringArgumentType.getString(ctx, "type"));
    }

    private static int clearForcedBuildingType(CommandContext<ServerCommandSource> ctx) {
        return setBuildingType(ctx, null);
    }

    private static int removeVillage(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        List<Village> collect = VillageManager.get(ctx.getSource().getWorld()).findVillages(v -> v.getName().equals(name)).toList();
        if (collect.isEmpty()) {
            fail("No village with this name exists.", ctx);
        } else if (collect.size() > 1) {
            success("Village deleted.", ctx);
            fail("No village with this name exists.", ctx);
        } else if (VillageManager.get(ctx.getSource().getWorld()).removeVillage(collect.get(0).getId())) {
            success("Village deleted.", ctx);
        } else {
            fail("Unknown error.", ctx);
        }
        return 0;
    }

    private static int buildingProcessingRate(CommandContext<ServerCommandSource> ctx) {
        int cooldown = IntegerArgumentType.getInteger(ctx, "cooldown");
        VillageManager.get(ctx.getSource().getWorld()).setBuildingCooldown(cooldown);
        return 0;
    }

    private static int resetPlayerData(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.reset();
        success("Player data reset.", ctx);
        return 0;
    }

    private static int overrideVillageRequirements(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        PlayerSaveData.get(target).setOverrideVillageRequirements(value);
        success("Village requirement override for " + target.getGameProfile().getName() + " set to " + value + ".", ctx);
        return 0;
    }

    private static int resetMarriage(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.endRelationShip(RelationshipState.SINGLE);
        success("Marriage reset.", ctx);
        return 0;
    }

    private static int decrementHearts(CommandContext<ServerCommandSource> ctx) {
        PlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        getLoadedVillagers(ctx).forEach(v -> v.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-10));
        return 0;
    }

    private static int incrementHearts(CommandContext<ServerCommandSource> ctx) {
        PlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        getLoadedVillagers(ctx).forEach(v -> v.getVillagerBrain().getMemoriesForPlayer(player).modHearts(10));
        return 0;
    }

    private static int forceChildGrowth(CommandContext<ServerCommandSource> ctx) {
        getLoadedVillagers(ctx).forEach(v -> v.setBreedingAge(0));
        return 0;
    }

    private static int forceBabyGrowth(CommandContext<ServerCommandSource> ctx) {
        PlayerEntity player = ctx.getSource().getPlayer();
        ItemStack heldStack;
        if (player != null) {
            heldStack = player.getMainHandStack();

            if (heldStack.getItem() instanceof BabyItem) {
                NbtCompound nbt = BabyItem.getBabyNbt(heldStack);
                nbt.putInt("age", Config.getInstance().babyItemGrowUpTime);
                success("Baby is old enough to place now.", ctx);
            } else {
                fail("Hold a baby first.", ctx);
            }
        }
        return 0;
    }

    private static int forceFullHearts(CommandContext<ServerCommandSource> ctx) {
        PlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            getLoadedVillagers(ctx).forEach(v -> {
                v.getVillagerBrain().getMemoriesForPlayer(player).setHearts(1000);
            });
        }
        return 0;
    }

    private static int restoreClearedVillagers(CommandContext<ServerCommandSource> ctx) {
        storedVillagers.forEach(tag ->
                EntityType.getEntityFromNbt(tag, ctx.getSource().getWorld()).ifPresent(v ->
                        ctx.getSource().getWorld().spawnEntity(v)
                )
        );
        storedVillagers.clear();
        success("Restored cleared villagers.", ctx);
        return 0;
    }

    private static ArgumentBuilder<ServerCommandSource, ?> register(String name, Command<ServerCommandSource> cmd) {
        return CommandManager.literal(name).requires(cs -> cs.hasPermissionLevel(2)).executes(cmd);
    }

    private static ArgumentBuilder<ServerCommandSource, ?> register(String name) {
        return CommandManager.literal(name).requires(cs -> cs.hasPermissionLevel(2));
    }

    private static int clearLoadedVillagers(final CommandContext<ServerCommandSource> ctx) {
        storedVillagers.clear();
        getLoadedVillagers(ctx).forEach(v -> {
            NbtCompound tag = new NbtCompound();
            if (v.saveSelfNbt(tag)) {
                storedVillagers.add(tag);
                v.discard();
            }
        });

        success("Removed loaded villagers.", ctx);
        return 0;
    }

    private static Stream<VillagerEntityMCA> getLoadedVillagers(final CommandContext<ServerCommandSource> ctx) {
        ServerWorld world = ctx.getSource().getWorld();
        return Stream.concat(world.getEntitiesByType(EntitiesMCA.FEMALE_VILLAGER.get(), x -> true).stream(), world.getEntitiesByType(EntitiesMCA.MALE_VILLAGER.get(), x -> true).stream()).map(VillagerEntityMCA.class::cast);
    }

    private static void success(String message, CommandContext<ServerCommandSource> ctx, Object... events) {
        ctx.getSource().sendFeedback(() -> message(message, GREEN, events), true);
    }

    private static void fail(String message, CommandContext<ServerCommandSource> ctx, Object... events) {
        ctx.getSource().sendError(message(message, RED, events));
    }

    private static Text message(String message, Formatting red, Object[] events) {
        MutableText data = Text.literal(message).formatted(red);
        for (Object evt : events) {
            if (evt instanceof ClickEvent clickEvent) {
                data.styled((style -> style.withClickEvent(clickEvent)));
            }
            if (evt instanceof HoverEvent hoverEvent) {
                data.styled((style -> style.withHoverEvent(hoverEvent)));
            }
        }
        return data;
    }

    private static int displayHelp(CommandContext<ServerCommandSource> ctx) {
        Entity player = ctx.getSource().getEntity();
        if (player == null) {
            return 0;
        }

        sendMessage(player, DARK_RED + "--- " + GOLD + "OP COMMANDS" + DARK_RED + " ---");
        sendMessage(player, WHITE + " /mca-admin forceBuildingType id " + GOLD + " - Force a building's type. " + RED + "(Must be a valid building type)");
        sendMessage(player, WHITE + " /mca-admin forceFullHearts " + GOLD + " - Force all hearts on all villagers.");
        sendMessage(player, WHITE + " /mca-admin forceBabyGrowth " + GOLD + " - Force your baby to grow up.");
        sendMessage(player, WHITE + " /mca-admin forceChildGrowth " + GOLD + " - Force nearby children to grow.");
        sendMessage(player, WHITE + " /mca-admin clearLoadedVillagers " + GOLD + " - Clear all loaded villagers. " + RED + "(IRREVERSIBLE)");
        sendMessage(player, WHITE + " /mca-admin restoreClearedVillagers " + GOLD + " - Restores cleared villagers. ");
        sendMessage(player, WHITE + " /mca-admin overrideVillageRequirements true|false " + GOLD + " - Override village requirements for this player.");

        sendMessage(player, WHITE + " /mca-admin listVillages " + GOLD + " - Prints a list of all villages.");
        sendMessage(player, WHITE + " /mca-admin removeVillage id" + GOLD + " - Removed a village with given ID.");

        sendMessage(player, WHITE + " /mca-admin convertVanillaVillagers radius" + GOLD + " - Convert vanilla villagers in the given radius");

        sendMessage(player, WHITE + " /mca-admin incrementHearts " + GOLD + " - Increase hearts by 10.");
        sendMessage(player, WHITE + " /mca-admin decrementHearts " + GOLD + " - Decrease hearts by 10.");
        sendMessage(player, WHITE + " /mca-admin resetPlayerData " + GOLD + " - Resets genetics.");
        sendMessage(player, WHITE + " /mca-admin resetMarriage " + GOLD + " - Resets your marriage.");

        sendMessage(player, WHITE + " /mca-admin listVillages " + GOLD + " - List all known villages.");
        sendMessage(player, WHITE + " /mca-admin removeVillage " + GOLD + " - Remove a given village.");

        sendMessage(player, DARK_RED + "--- " + GOLD + "GLOBAL COMMANDS" + DARK_RED + " ---");
        sendMessage(player, WHITE + " /mca-admin help " + GOLD + " - Shows this list of commands.");

        return 0;
    }


    private static void sendMessage(Entity commandSender, String message) {
        commandSender.sendMessage(Text.literal(GOLD + "[MCA] " + RESET + message));
    }
}
