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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public record ReportBuildingMessage(Action action, String data, int expectedTargetId) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ReportBuildingMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("report_building"));
    public static final StreamCodec<FriendlyByteBuf, ReportBuildingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.VALUES[i], Action::ordinal), ReportBuildingMessage::action,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(opt -> opt.orElse(null), java.util.Optional::ofNullable), ReportBuildingMessage::data,
            ByteBufCodecs.VAR_INT, ReportBuildingMessage::expectedTargetId,
            ReportBuildingMessage::new);

    public ReportBuildingMessage(Action action) {
        this(action, null, -1);
    }

    public ReportBuildingMessage(Action action, String data) {
        this(action, data, -1);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        VillageManager manager = VillageManager.get(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        try {
            switch (action) {
                case SCAN_ROOM, ADD_BUILDING, ADD_ATTACHMENT ->
                        executeScanAction(workflow, player, player.blockPosition(), null,
                                action, expectedTargetId >= 0 ? expectedTargetId : parseTargetBuildingId(data));
                case SET_MAIN_ROOM -> displayEditResult(player,
                        manager.setMainRoom(player.blockPosition(), expectedTargetId), "blueprint.mainRoomSet");
                case AUTO_SCAN -> manager.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
                case FULL_SCAN -> fullScan(manager, player);
                case FORCE_TYPE -> displayEditResult(player,
                        manager.forceRoomType(player.blockPosition(), data, expectedTargetId), null);
                case REMOVE_ROOM -> displayEditResult(player,
                        manager.removeRoom(player.blockPosition(), expectedTargetId), "blueprint.roomRemoved");
                case REMOVE_FLOOR -> {
                    FloorRemovalTarget target = parseFloorRemovalTarget(data);
                    displayEditResult(player,
                            manager.removeFloor(player.blockPosition(), target.floorNumber(), expectedTargetId,
                                    target.structureId(), target.floorId()),
                            "blueprint.floorRemoved");
                }
                case REMOVE -> displayEditResult(player,
                        manager.removeBuilding(player.blockPosition(), expectedTargetId), "blueprint.buildingRemoved");
                case SET_ROOM_INHERITANCE -> setRoomInheritance(workflow, player, data, expectedTargetId);
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
            case SCAN_ROOM -> workflow.scanRoom(source, expectedTargetId, forcedType);
            case ADD_BUILDING -> workflow.addBuilding(source, expectedTargetId, forcedType);
            case ADD_ATTACHMENT -> workflow.addAttachment(source, expectedTargetId, forcedType);
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

    private static FloorRemovalTarget parseFloorRemovalTarget(String value) {
        if (value == null) return FloorRemovalTarget.invalid();
        String[] parts = value.split(":", -1);
        int floorNumber = parseInt(parts[0], Integer.MIN_VALUE);
        if (parts.length != 3) return new FloorRemovalTarget(floorNumber, -1, -1);
        return new FloorRemovalTarget(
                floorNumber,
                parseInt(parts[1], -1),
                parseInt(parts[2], -1));
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record FloorRemovalTarget(int floorNumber, int structureId, int floorId) {
        private static FloorRemovalTarget invalid() {
            return new FloorRemovalTarget(Integer.MIN_VALUE, -1, -1);
        }
    }

    private static void fullScan(VillageManager manager, ServerPlayer player) {
        Village village = manager.findNearestVillage(player).orElse(null);
        if (village == null) {
            player.sendSystemMessage(Component.translatable("blueprint.noBuilding"), true);
            return;
        }
        displayScanResult(player, manager.fullScan(village), "blueprint.refreshed");
    }

    private static void setRoomInheritance(RoomWorkflow workflow,
                                           ServerPlayer player,
                                           String data,
                                           int expectedRoomId) {
        if (!"true".equals(data) && !"false".equals(data)) return;
        boolean enabled = Boolean.parseBoolean(data);
        RoomWorkflow.Outcome outcome = workflow.updateInheritance(
                player.blockPosition(), expectedRoomId, enabled, null);
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
            if (action == Action.SCAN_ROOM
                    && outcome.expectedTargetId() < 0
                    && outcome.result() == Building.validationResult.IDENTICAL) {
                player.sendSystemMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
                return;
            }
            if ((action == Action.SCAN_ROOM || action == Action.SET_ROOM_INHERITANCE)
                    && outcome.result() == Building.validationResult.NOT_IN_BUILDING) {
                if (outcome.expectedTargetId() >= 0) {
                    player.sendSystemMessage(Component.translatable("blueprint.roomUpdateConflict"), true);
                }
                return;
            }
            displayScanResult(player, outcome.result());
            return;
        }

        String successKey = switch (action) {
            case ADD_BUILDING -> outcome.prospectiveFloorNumber() == Integer.MIN_VALUE
                    ? "blueprint.buildingAdded"
                    : outcome.prospectiveFloorNumber() < 0 ? "blueprint.basementAdded" : "blueprint.floorAdded";
            case ADD_ATTACHMENT -> outcome.prospectiveFloorNumber() < 0
                    ? "blueprint.basementAdded" : "blueprint.floorAdded";
            case SCAN_ROOM -> outcome.expectedTargetId() >= 0
                    ? "blueprint.roomUpdated" : "blueprint.roomAdded";
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
        player.sendSystemMessage(Component.translatable(key), true);
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
            case NO_STRUCTURE -> "blueprint.mainRoomNoStructure";
            case TARGET_CHANGED -> "blueprint.targetChanged";
        };
        if (key != null) player.sendSystemMessage(Component.translatable(key), true);
    }

    @Override
    public CustomPacketPayload.Type<ReportBuildingMessage> type() {
        return TYPE;
    }

    public enum Action {
        AUTO_SCAN,
        SCAN_ROOM,
        REMOVE,
        FORCE_TYPE,
        FULL_SCAN,
        REMOVE_ROOM,
        SET_MAIN_ROOM,
        SET_ROOM_INHERITANCE,
        ADD_BUILDING,
        ADD_ATTACHMENT,
        REMOVE_FLOOR;

        public static final Action[] VALUES = values();
    }
}

