package net.conczin.mca.server.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.conczin.mca.Config;
import net.conczin.mca.entity.EntitiesMCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.server.SpawnQueue;
import net.conczin.mca.server.world.data.*;
import net.mca.server.world.data.*;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.stream.Stream;

import static net.minecraft.ChatFormatting.*;

public class AdminCommand {
    private static final List<CompoundTag> storedVillagers = new ArrayList<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mca-admin")
                .then(register("help", AdminCommand::displayHelp))
                .then(register("clearLoadedVillagers", AdminCommand::clearLoadedVillagers))
                .then(register("restoreClearedVillagers", AdminCommand::restoreClearedVillagers))
                .then(register("forceBuildingType").then(Commands.argument("type", StringArgumentType.string()).executes(AdminCommand::forceBuildingType)).executes(AdminCommand::clearForcedBuildingType))
                .then(register("forceFullHearts", AdminCommand::forceFullHearts))
                .then(register("forceBabyGrowth", AdminCommand::forceBabyGrowth))
                .then(register("forceChildGrowth", AdminCommand::forceChildGrowth))
                .then(register("incrementHearts", AdminCommand::incrementHearts))
                .then(register("decrementHearts", AdminCommand::decrementHearts))
                .then(register("resetPlayerData", AdminCommand::resetPlayerData))
                .then(register("overrideVillageRequirements")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(AdminCommand::overrideVillageRequirements))))
                .then(register("resetMarriage", AdminCommand::resetMarriage))
                .then(register("listVillages", AdminCommand::listVillages))
                .then(register("assumeNameDead").then(Commands.argument("name", StringArgumentType.string()).executes(AdminCommand::assumeNameDead)))
                .then(register("assumeUuidDead").then(Commands.argument("uuid", UuidArgument.uuid()).executes(AdminCommand::assumeUuidDead)))
                .then(register("removeVillageWithId").then(Commands.argument("id", IntegerArgumentType.integer()).executes(AdminCommand::removeVillageWithId)))
                .then(register("convertVanillaVillagers").then(Commands.argument("radius", IntegerArgumentType.integer()).executes(AdminCommand::convertVanillaVillagers)))
                .then(register("removeVillage").then(Commands.argument("name", StringArgumentType.string()).executes(AdminCommand::removeVillage)))
                .then(register("buildingProcessingRate").then(Commands.argument("cooldown", IntegerArgumentType.integer()).executes(AdminCommand::buildingProcessingRate)))
                .requires((serverCommandSource) -> serverCommandSource.hasPermission(2))
        );
    }

    private static int listVillages(CommandContext<CommandSourceStack> ctx) {
        //remove from villages
        for (Village village : VillageManager.get(ctx.getSource().getLevel())) {
            final BlockPos pos = village.getBox().getCenter();
            success(String.format(Locale.ROOT, "%d: %s with %d buildings and %d/%d villager(s)",
                            village.getId(),
                            village.getName(),
                            village.getBuildings().size(),
                            village.getPopulation(),
                            village.getMaxPopulation()
                    ), ctx,
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip")),
                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + pos.getX() + " ~ " + pos.getZ()));
        }
        return 0;
    }

    private static int assumeNameDead(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        //remove spouse too
        FamilyTree tree = FamilyTree.get(ctx.getSource().getLevel());
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

    private static int assumeUuidDead(CommandContext<CommandSourceStack> ctx) {
        UUID uuid = UuidArgument.getUuid(ctx, "uuid");
        FamilyTree tree = FamilyTree.get(ctx.getSource().getLevel());
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

    private static void assumeDead(CommandContext<CommandSourceStack> ctx, UUID uuid) {
        for (Village village : VillageManager.get(ctx.getSource().getLevel())) {
            village.removeResident(uuid);
        }

        FamilyTree tree = FamilyTree.get(ctx.getSource().getLevel());
        Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
        node.filter(n -> n.partner() != null).ifPresent(n -> n.updatePartner(null, RelationshipState.WIDOW));

        //remove from player spouse
        ctx.getSource().getLevel().players().forEach(player -> {
            PlayerSaveData playerData = PlayerSaveData.get(player);
            if (playerData.getPartnerUUID().orElse(Util.NIL_UUID).equals(uuid)) {
                playerData.endRelationShip(RelationshipState.SINGLE);
            }
        });
    }

    private static int removeVillageWithId(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        if (VillageManager.get(ctx.getSource().getLevel()).removeVillage(id)) {
            success("Village deleted.", ctx);
        } else {
            fail("Village with this ID does not exist.", ctx);
        }
        return 0;
    }

    private static int convertVanillaVillagers(CommandContext<CommandSourceStack> ctx) {
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        ServerLevel world = ctx.getSource().getLevel();
        world.getEntities(EntityType.VILLAGER, x -> true).stream().map(Villager.class::cast).forEach(v -> {
            if (v.distanceTo(ctx.getSource().getEntity()) < radius) {
                SpawnQueue.getInstance().convert(v);
            }
        });
        return 0;
    }

    private static int setBuildingType(CommandContext<CommandSourceStack> ctx, String type) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        VillageManager villages = VillageManager.get(ctx.getSource().getLevel());
        Optional<Village> village = villages.findNearestVillage(player);

        Optional<Building> building = village.flatMap(v -> v.getBuildings().values().stream().filter((b) ->
                b.containsPos(player.blockPosition())).findAny());
        if (building.isPresent()) {
            if (building.get().getType().equals(type)) {
                building.get().setTypeForced(false);
                building.get().determineType();
            } else {
                building.get().setTypeForced(true);
                building.get().setType(type);
            }
        } else {
            fail(Component.translatable("blueprint.noBuilding").getString(), ctx);
        }
        return 0;
    }

    private static int forceBuildingType(CommandContext<CommandSourceStack> ctx) {
        return setBuildingType(ctx, StringArgumentType.getString(ctx, "type"));
    }

    private static int clearForcedBuildingType(CommandContext<CommandSourceStack> ctx) {
        return setBuildingType(ctx, null);
    }

    private static int removeVillage(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        List<Village> collect = VillageManager.get(ctx.getSource().getLevel()).findVillages(v -> v.getName().equals(name)).toList();
        if (collect.isEmpty()) {
            fail("No village with this name exists.", ctx);
        } else if (collect.size() > 1) {
            success("Village deleted.", ctx);
            fail("No village with this name exists.", ctx);
        } else if (VillageManager.get(ctx.getSource().getLevel()).removeVillage(collect.get(0).getId())) {
            success("Village deleted.", ctx);
        } else {
            fail("Unknown error.", ctx);
        }
        return 0;
    }

    private static int buildingProcessingRate(CommandContext<CommandSourceStack> ctx) {
        int cooldown = IntegerArgumentType.getInteger(ctx, "cooldown");
        VillageManager.get(ctx.getSource().getLevel()).setBuildingCooldown(cooldown);
        return 0;
    }

    private static int resetPlayerData(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.reset();
        success("Player data reset.", ctx);
        return 0;
    }

    private static int overrideVillageRequirements(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        PlayerSaveData.get(target).setOverrideVillageRequirements(value);
        success("Village requirement override for " + target.getGameProfile().getName() + " set to " + value + ".", ctx);
        return 0;
    }

    private static int resetMarriage(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.endRelationShip(RelationshipState.SINGLE);
        success("Marriage reset.", ctx);
        return 0;
    }

    private static int decrementHearts(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        getLoadedVillagers(ctx).forEach(v -> v.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-10));
        return 0;
    }

    private static int incrementHearts(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        getLoadedVillagers(ctx).forEach(v -> v.getVillagerBrain().getMemoriesForPlayer(player).modHearts(10));
        return 0;
    }

    private static int forceChildGrowth(CommandContext<CommandSourceStack> ctx) {
        getLoadedVillagers(ctx).forEach(v -> v.setAge(0));
        return 0;
    }

    private static int forceBabyGrowth(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        ItemStack heldStack;
        if (player != null) {
            heldStack = player.getMainHandItem();

            if (heldStack.getItem() instanceof BabyItem) {
                CompoundTag nbt = BabyItem.getBabyNbt(heldStack);
                nbt.putInt("age", Config.getInstance().babyItemGrowUpTime);
                success("Baby is old enough to place now.", ctx);
            } else {
                fail("Hold a baby first.", ctx);
            }
        }
        return 0;
    }

    private static int forceFullHearts(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player != null) {
            getLoadedVillagers(ctx).forEach(v -> {
                v.getVillagerBrain().getMemoriesForPlayer(player).setHearts(1000);
            });
        }
        return 0;
    }

    private static int restoreClearedVillagers(CommandContext<CommandSourceStack> ctx) {
        storedVillagers.forEach(tag ->
                EntityType.create(tag, ctx.getSource().getLevel()).ifPresent(v ->
                        ctx.getSource().getLevel().addFreshEntity(v)
                )
        );
        storedVillagers.clear();
        success("Restored cleared villagers.", ctx);
        return 0;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> register(String name, Command<CommandSourceStack> cmd) {
        return Commands.literal(name).requires(cs -> cs.hasPermission(2)).executes(cmd);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> register(String name) {
        return Commands.literal(name).requires(cs -> cs.hasPermission(2));
    }

    private static int clearLoadedVillagers(final CommandContext<CommandSourceStack> ctx) {
        storedVillagers.clear();
        getLoadedVillagers(ctx).forEach(v -> {
            CompoundTag tag = new CompoundTag();
            if (v.saveAsPassenger(tag)) {
                storedVillagers.add(tag);
                v.discard();
            }
        });

        success("Removed loaded villagers.", ctx);
        return 0;
    }

    private static Stream<VillagerEntityMCA> getLoadedVillagers(final CommandContext<CommandSourceStack> ctx) {
        ServerLevel world = ctx.getSource().getLevel();
        return Stream.concat(world.getEntities(EntitiesMCA.FEMALE_VILLAGER.get(), x -> true).stream(), world.getEntities(EntitiesMCA.MALE_VILLAGER.get(), x -> true).stream()).map(VillagerEntityMCA.class::cast);
    }

    private static void success(String message, CommandContext<CommandSourceStack> ctx, Object... events) {
        ctx.getSource().sendSuccess(() -> message(message, GREEN, events), true);
    }

    private static void fail(String message, CommandContext<CommandSourceStack> ctx, Object... events) {
        ctx.getSource().sendFailure(message(message, RED, events));
    }

    private static Component message(String message, ChatFormatting red, Object[] events) {
        MutableComponent data = Component.literal(message).withStyle(red);
        for (Object evt : events) {
            if (evt instanceof ClickEvent clickEvent) {
                data.withStyle((style -> style.withClickEvent(clickEvent)));
            }
            if (evt instanceof HoverEvent hoverEvent) {
                data.withStyle((style -> style.withHoverEvent(hoverEvent)));
            }
        }
        return data;
    }

    private static int displayHelp(CommandContext<CommandSourceStack> ctx) {
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
        commandSender.sendSystemMessage(Component.literal(GOLD + "[MCA] " + RESET + message));
    }
}
