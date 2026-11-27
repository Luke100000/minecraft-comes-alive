package net.conczin.mca.network.c2s;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BuildingPolymorphMessage;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomWorkflow;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.util.MaxSizeHashMap;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.*;

public record ReportBuildingMessage(Action action, String data, int expectedTargetId) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ReportBuildingMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("report_building"));
    public static final int MAX_DATA_LENGTH = 64;
    static final long FULL_SCAN_COOLDOWN_MS = 2_000L;
    private static final Map<UUID, Long> FULL_SCAN_COOLDOWNS = Collections.synchronizedMap(new MaxSizeHashMap<>(256));

    static final StreamCodec<ByteBuf, Action> ACTION_CODEC = ByteBufCodecs.idMapper(id -> {
        if (id < 0 || id >= Action.VALUES.length) {
            throw new DecoderException("Invalid blueprint action ordinal: " + id);
        }
        return Action.VALUES[id];
    }, Action::ordinal);

    public static final StreamCodec<FriendlyByteBuf, ReportBuildingMessage> STREAM_CODEC = StreamCodec.composite(
            ACTION_CODEC, ReportBuildingMessage::action,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_DATA_LENGTH))
                    .map(opt -> opt.orElse(null), Optional::ofNullable), ReportBuildingMessage::data,
            ByteBufCodecs.VAR_INT, ReportBuildingMessage::expectedTargetId,
            ReportBuildingMessage::new);

    public ReportBuildingMessage(Action action) {
        this(action, null, -1);
    }

    public ReportBuildingMessage(Action action, String data) {
        this(action, data, -1);
    }

    static boolean canUseBlueprint(ServerPlayer player) {
        return player != null && !player.isSpectator() && player.isHolding(ItemsMCA.BLUEPRINT);
    }

    static boolean hasVillagePermission(ServerPlayer player, Village village, Rank required) {
        if (player == null) return false;
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || village == null) return true;
        if (Tasks.getInstance() == null || Tasks.getInstance().tasks.isEmpty()) return true;
        return Tasks.getRank(village, player).isAtLeast(required);
    }

    static Village editTarget(VillageManager manager, BlockPos source, Action action) {
        return switch (action) {
            case SCAN_ROOM, ADD_BUILDING, ADD_ATTACHMENT, SET_ROOM_INHERITANCE ->
                    RoomWorkflow.findTargetVillage(manager, source);
            default -> manager.findNearestVillage(source, Village.PLAYER_BORDER_MARGIN).orElse(null);
        };
    }

    @Override
    public void handleServer(ServerPlayer player) {
        if (!canUseBlueprint(player)) {
            return;
        }
        ServerLevel level = player.level();
        VillageManager manager = VillageManager.get(level);
        Village nearestVillage = editTarget(manager, player.blockPosition(), action);
        if (!hasVillagePermission(player, nearestVillage, Rank.PEASANT)) {
            player.sendSystemMessage(Component.translatable("gui.blueprint.rankTooLow"), true);
            return;
        }

        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        try {
            switch (action) {
                case SCAN_ROOM, ADD_BUILDING, ADD_ATTACHMENT ->
                        executeScanAction(workflow, player, player.blockPosition(), null,
                                action, expectedTargetId >= 0 ? expectedTargetId : parseTargetBuildingId(data));
                case SET_MAIN_ROOM -> displayEditResult(player,
                        manager.setMainRoom(player.blockPosition(), expectedTargetId), "blueprint.mainRoomSet");
                case AUTO_SCAN -> {
                    if (nearestVillage != null) nearestVillage.toggleAutoScan();
                }
                case FULL_SCAN -> fullScan(manager, player, nearestVillage);
                case FORCE_TYPE -> {
                    if (isAllowedForcedType(data)) {
                        displayEditResult(player,
                                manager.forceRoomType(player.blockPosition(), data, expectedTargetId), null);
                    } else {
                        MCA.LOGGER.warn("Ignoring invalid forced building type '{}' from {}", data, player);
                    }
                }
                case REMOVE_ROOM -> displayEditResult(player,
                        manager.removeRoom(player.blockPosition(), expectedTargetId), "blueprint.roomRemoved");
                case REMOVE_FLOOR -> {
                    FloorRemovalTarget target = parseFloorRemovalTarget(data).orElse(null);
                    if (target != null) {
                        displayEditResult(player,
                                manager.removeFloor(player.blockPosition(), target.floorNumber(), expectedTargetId,
                                        target.structureId(), target.floorId()),
                                "blueprint.floorRemoved");
                    }
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
        Action effectiveAction = action == Action.ADD_BUILDING && outcome.prospectiveFloorNumber() != Integer.MIN_VALUE
                ? Action.ADD_ATTACHMENT : action;
        displayWorkflowOutcome(player, outcome, effectiveAction, action);
    }

    static boolean isAllowedForcedType(String type) {
        return type == null || "blocked".equals(type);
    }

    private static int parseTargetBuildingId(String value) {
        return parseInt(value, -1);
    }

    static Optional<FloorRemovalTarget> parseFloorRemovalTarget(String value) {
        if (value == null) return Optional.empty();
        String[] parts = value.split(":", -1);
        if (parts.length != 3) return Optional.empty();

        Integer floorNumber = parseInt(parts[0]);
        Integer structureId = parseInt(parts[1]);
        Integer floorId = parseInt(parts[2]);
        if (floorNumber == null || floorNumber == Integer.MIN_VALUE
                || structureId == null || structureId < 0
                || floorId == null || floorId < 0) {
            return Optional.empty();
        }
        return Optional.of(new FloorRemovalTarget(floorNumber, structureId, floorId));
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Integer parseInt(String value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    record FloorRemovalTarget(int floorNumber, int structureId, int floorId) {
    }

    private static void fullScan(VillageManager manager, ServerPlayer player, Village village) {
        long now = Util.getMillis();
        Long nextAllowed = FULL_SCAN_COOLDOWNS.get(player.getUUID());
        if (nextAllowed != null && now < nextAllowed) {
            return;
        }
        FULL_SCAN_COOLDOWNS.put(player.getUUID(), now + FULL_SCAN_COOLDOWN_MS);
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
        displayWorkflowOutcome(player, outcome, Action.SET_ROOM_INHERITANCE, Action.SET_ROOM_INHERITANCE);
    }

    private static void displayWorkflowOutcome(ServerPlayer player,
                                               RoomWorkflow.Outcome outcome,
                                               Action polymorphAction,
                                               Action feedbackAction) {
        if (outcome.status() == RoomWorkflow.Status.REQUIRES_TYPE_SELECTION) {
            requestType(outcome.matchingTypes(), outcome.source(), player, polymorphAction, outcome.expectedTargetId());
            return;
        }
        String key = workflowFeedbackKey(outcome, feedbackAction);
        if (key != null) player.sendSystemMessage(Component.translatable(key), true);
    }

    static String workflowFeedbackKey(RoomWorkflow.Outcome outcome, Action action) {
        if (outcome.status() == RoomWorkflow.Status.REQUIRES_TYPE_SELECTION) return null;
        if (outcome.status() == RoomWorkflow.Status.FAILED) {
            if (action == Action.SCAN_ROOM
                    && outcome.expectedTargetId() < 0
                    && outcome.result() == Building.validationResult.IDENTICAL) {
                return "blueprint.roomAlreadyAdded";
            }
            if (outcome.result() == Building.validationResult.TARGET_CHANGED) return "blueprint.targetChanged";
            return "blueprint.scan." + outcome.result().name().toLowerCase(Locale.ENGLISH);
        }

        return switch (action) {
            case ADD_BUILDING -> outcome.prospectiveFloorNumber() == Integer.MIN_VALUE
                    ? "blueprint.buildingAdded"
                    : outcome.prospectiveFloorNumber() < 0 ? "blueprint.basementAdded" : "blueprint.floorAdded";
            case ADD_ATTACHMENT -> outcome.prospectiveFloorNumber() < 0
                    ? "blueprint.basementAdded" : "blueprint.floorAdded";
            case SCAN_ROOM -> outcome.expectedTargetId() >= 0
                    ? "blueprint.roomUpdated" : "blueprint.roomAdded";
            default -> null;
        };
    }

    private static void requestType(List<String> matchingTypes,
                                    BlockPos source,
                                    ServerPlayer player,
                                    Action action,
                                    int expectedTargetId) {
        Network.sendToPlayer(new BuildingPolymorphMessage(
                matchingTypes, source, action, expectedTargetId), player);
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
            case INVALID_TYPE -> "blueprint.scan.invalid_type";
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

