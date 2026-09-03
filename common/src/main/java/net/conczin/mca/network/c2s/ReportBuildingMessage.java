package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BuildingPolymorphMessage;
import net.conczin.mca.server.world.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public record ReportBuildingMessage(Action action, String data) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ReportBuildingMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("report_building"));
    public static final StreamCodec<FriendlyByteBuf, ReportBuildingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.VALUES[i], Action::ordinal), ReportBuildingMessage::action,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(opt -> opt.orElse(null), java.util.Optional::ofNullable), ReportBuildingMessage::data,
            ReportBuildingMessage::new);

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager manager = VillageManager.get(player.serverLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, player.serverLevel());
        try {
            switch (action) {
                case ADD_ROOM, ADD_BUILDING, ADD_FLOOR, ADD_BASEMENT, UPDATE_ROOM ->
                        executeScanAction(workflow, player, player.blockPosition(), null,
                                action, parseTargetBuildingId(data));
                case SET_MAIN_ROOM -> updateMainRoom(manager, player);
                case AUTO_SCAN -> manager.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
                case FULL_SCAN -> fullScan(manager, player);
                case FORCE_TYPE -> displayEditResult(player,
                        manager.forceRoomType(player.blockPosition(), data), null);
                case REMOVE_ROOM -> displayEditResult(player,
                        manager.removeRoom(player.blockPosition()), "blueprint.roomRemoved");
                case REMOVE_FLOOR -> displayEditResult(player,
                        manager.removeFloor(player.blockPosition(), parseFloorNumber(data)), "blueprint.floorRemoved");
                case REMOVE -> displayEditResult(player,
                        manager.removeBuilding(player.blockPosition()), "blueprint.buildingRemoved");
                case SET_ROOM_INHERITANCE -> setRoomInheritance(workflow, player, data);
            }
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    static void executeScanAction(RoomWorkflow workflow,
                                  ServerPlayer player,
                                  BlockPos source,
                                  String forcedType,
                                  Action action,
                                  int expectedTargetId) {
        RoomWorkflow.Outcome outcome = switch (action) {
            case ADD_ROOM -> workflow.addRoom(source, forcedType);
            case ADD_BUILDING -> workflow.addBuilding(source, forcedType);
            case ADD_FLOOR -> workflow.addFloor(source, expectedTargetId, forcedType);
            case ADD_BASEMENT -> workflow.addBasement(source, expectedTargetId, forcedType);
            case UPDATE_ROOM -> workflow.updateRoom(source, expectedTargetId, forcedType);
            case SET_ROOM_INHERITANCE -> workflow.updateInheritance(
                    source, expectedTargetId, false, forcedType);
            default -> null;
        };
        if (outcome == null) {
            MCA.LOGGER.warn("Ignoring invalid building scan action {} from {}", action, player);
            return;
        }
        displayWorkflowOutcome(player, outcome, action);
    }

    private static int parseTargetBuildingId(String value) {
        return parseInt(value, -1);
    }

    private static int parseFloorNumber(String value) {
        return parseInt(value, Integer.MIN_VALUE);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void fullScan(VillageManager manager, ServerPlayer player) {
        Village village = manager.findNearestVillage(player).orElse(null);
        if (village == null) {
            player.displayClientMessage(Component.translatable("blueprint.noBuilding"), true);
            return;
        }
        displayScanResult(player, manager.fullScan(village), "blueprint.refreshed");
    }

    private static void updateMainRoom(VillageManager manager, ServerPlayer player) {
        Village village = manager.findNearestVillage(player).orElse(null);
        if (village == null) {
            player.displayClientMessage(Component.translatable("blueprint.noBuilding"), true);
            return;
        }
            Building room = village.findInteractionRoomAt(player.blockPosition()).orElse(null);
        if (room == null) {
            player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
            return;
        }
        if (village.getStructureFor(room).isEmpty()) {
            player.displayClientMessage(Component.translatable("blueprint.mainRoomNoStructure"), true);
            return;
        }
        boolean changed = village.setMainRoom(room);
        if (changed) {
            player.displayClientMessage(Component.translatable("blueprint.mainRoomSet"), true);
        }
    }

    private static void setRoomInheritance(RoomWorkflow workflow, ServerPlayer player, String data) {
        if (!"true".equals(data) && !"false".equals(data)) return;
        boolean enabled = Boolean.parseBoolean(data);
        RoomWorkflow.Outcome outcome = workflow.updateInheritance(
                player.blockPosition(), -1, enabled, null);
        if (outcome.status() == RoomWorkflow.Status.FAILED
                && outcome.result() == Building.validationResult.NOT_IN_BUILDING) {
            return;
        }
        displayWorkflowOutcome(player, outcome, Action.SET_ROOM_INHERITANCE);
    }

    private static void displayWorkflowOutcome(ServerPlayer player,
                                               RoomWorkflow.Outcome outcome,
                                               Action action) {
        if (outcome.status() == RoomWorkflow.Status.REQUIRES_TYPE_SELECTION) {
            requestType(outcome.matchingTypes(), outcome.source(), player, action, outcome.expectedTargetId());
            return;
        }
        if (outcome.status() == RoomWorkflow.Status.FAILED) {
            if (action == Action.ADD_ROOM && outcome.result() == Building.validationResult.IDENTICAL) {
                player.displayClientMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
                return;
            }
            if ((action == Action.UPDATE_ROOM || action == Action.SET_ROOM_INHERITANCE)
                    && outcome.result() == Building.validationResult.NOT_IN_BUILDING) {
                if (outcome.expectedTargetId() >= 0) {
                    player.displayClientMessage(Component.translatable("blueprint.roomUpdateConflict"), true);
                } else if (action == Action.UPDATE_ROOM) {
                    player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
                }
                return;
            }
            displayScanResult(player, outcome.result());
            return;
        }

        String successKey = switch (action) {
            case ADD_BUILDING -> "blueprint.buildingAdded";
            case ADD_FLOOR -> "blueprint.floorAdded";
            case ADD_BASEMENT -> "blueprint.basementAdded";
            case UPDATE_ROOM -> "blueprint.roomUpdated";
            case ADD_ROOM -> "blueprint.roomAdded";
            default -> null;
        };
        if (successKey != null) displayScanResult(player, Building.validationResult.SUCCESS, successKey);
    }


    private static void requestType(java.util.List<String> matchingTypes,
                                    BlockPos source,
                                    ServerPlayer player,
                                    Action action,
                                    int expectedTargetId) {
        Network.sendToPlayer(new BuildingPolymorphMessage(
                matchingTypes, source, action, expectedTargetId), player);
    }

    private static void displayScanResult(ServerPlayer player, Building.validationResult result) {
        displayScanResult(player, result, null);
    }

    private static void displayScanResult(ServerPlayer player,
                                          Building.validationResult result,
                                          String successKey) {
        String key = result == Building.validationResult.SUCCESS && successKey != null
                ? successKey
                : "blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH);
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static void displayEditResult(ServerPlayer player,
                                          VillageManager.BuildingEditResult result,
                                          String successKey) {
        String key = switch (result) {
            case SUCCESS -> successKey;
            case NO_BUILDING -> "blueprint.noBuilding";
            case NO_ROOM -> "blueprint.noRoomOnFloor";
            case NO_FLOOR -> "blueprint.noFloor";
            case MAIN_ROOM -> "blueprint.cannotRemoveMainRoom";
        };
        if (key != null) player.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    public CustomPacketPayload.Type<ReportBuildingMessage> type() {
        return TYPE;
    }

    public enum Action {
        AUTO_SCAN,
        ADD_ROOM,
        REMOVE,
        FORCE_TYPE,
        FULL_SCAN,
        REMOVE_ROOM,
        UPDATE_ROOM,
        SET_MAIN_ROOM,
        SET_ROOM_INHERITANCE,
        ADD_BUILDING,
        ADD_FLOOR,
        ADD_BASEMENT,
        REMOVE_FLOOR;

        public static final Action[] VALUES = values();
    }
}
