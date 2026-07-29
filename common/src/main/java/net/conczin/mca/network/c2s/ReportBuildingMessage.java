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
import java.util.Optional;

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
        try {
            switch (action) {
                case ADD_ROOM, ADD_BUILDING, ADD_FLOOR, ADD_BASEMENT, UPDATE_ROOM ->
                        executeScanAction(manager, player, player.blockPosition(), null,
                                action, parseTargetBuildingId(data));
                case SET_MAIN_ROOM -> updateMainRoom(manager, player);
                case AUTO_SCAN -> manager.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
                case FULL_SCAN -> fullScan(manager, player);
                case FORCE_TYPE -> displayEditResult(player,
                        manager.forceRoomType(player.blockPosition(), data), null);
                case REMOVE_ROOM -> displayEditResult(player,
                        manager.removeRoom(player.blockPosition()), "blueprint.roomRemoved");
                case REMOVE -> displayEditResult(player,
                        manager.removeBuilding(player.blockPosition()), "blueprint.buildingRemoved");
                case SET_ROOM_INHERITANCE -> setRoomInheritance(manager, player, data);
            }
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    static void executeScanAction(VillageManager manager,
                                  ServerPlayer player,
                                  BlockPos source,
                                  String forcedType,
                                  Action action,
                                  int expectedTargetId) {
        switch (action) {
            case ADD_ROOM -> {
                BuildingScanResult scan = manager.analyzeRoom(source);
                if (scan.result() == Building.validationResult.IDENTICAL) {
                    player.displayClientMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
                    return;
                }
                commitRoomAddition(manager, player, scan, forcedType, action);
            }
            case ADD_BUILDING -> commitRoomAddition(manager, player,
                    manager.analyzeBuildingAddition(source), forcedType, action);
            case ADD_FLOOR, ADD_BASEMENT -> {
                if (expectedTargetId < 0) {
                    displayScanResult(player, Building.validationResult.NOT_IN_BUILDING);
                    return;
                }
                Village.RoomScanMode mode = action == Action.ADD_BASEMENT
                        ? Village.RoomScanMode.ADD_BASEMENT : Village.RoomScanMode.ADD_FLOOR;
                commitRoomAddition(manager, player,
                        manager.analyzeAttachedRoom(source, mode, expectedTargetId), forcedType, action);
            }
            case UPDATE_ROOM -> updateRoom(manager, player, source, forcedType, expectedTargetId);
            default -> MCA.LOGGER.warn("Ignoring invalid building scan action {} from {}", action, player);
        }
    }

    private static int parseTargetBuildingId(String value) {
        if (value == null) return -1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
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
        Building room = village.getFunctionalRoomAt(player.serverLevel(), player.blockPosition()).orElse(null);
        if (room == null) {
            player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
            return;
        }
        Structure structure = village.getStructureFor(room).orElse(null);
        if (structure == null) {
            player.displayClientMessage(Component.translatable("blueprint.mainRoomNoStructure"), true);
            return;
        }
        boolean changeToAutomatic = !village.isMainRoomAutomatic(structure);
        boolean changed = changeToAutomatic
                ? village.useAutomaticMainRoom(structure)
                : village.setMainRoom(room);
        if (changed) {
            player.displayClientMessage(Component.translatable(changeToAutomatic
                    ? "blueprint.mainRoomAutomatic" : "blueprint.mainRoomSet"), true);
        }
    }

    private static void setRoomInheritance(VillageManager manager, ServerPlayer player, String data) {
        if (!"true".equals(data) && !"false".equals(data)) return;
        boolean enabled = Boolean.parseBoolean(data);
        Village village = manager.findNearestVillage(player).orElse(null);
        if (village == null) return;
        Building room = village.getFunctionalRoomAt(player.serverLevel(), player.blockPosition()).orElse(null);
        if (room == null) return;
        if (room.isInheritanceEnabled() == enabled) return;
        room.setInheritanceEnabled(enabled);
        village.markDirty();
    }

    static void updateRoom(VillageManager manager, ServerPlayer player, BlockPos source, String forcedType) {
        updateRoom(manager, player, source, forcedType, -1);
    }

    static void updateRoom(VillageManager manager,
                           ServerPlayer player,
                           BlockPos source,
                           String forcedType,
                           int originalExpectedRoomId) {
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        Building existing = village == null ? null
                : village.getFunctionalRoomAt(player.serverLevel(), source).orElse(null);
        if (originalExpectedRoomId >= 0 && (existing == null || existing.getId() != originalExpectedRoomId)) {
            player.displayClientMessage(Component.translatable("blueprint.roomUpdateConflict"), true);
            return;
        }
        if (existing == null) {
            player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
            return;
        }

        int expectedRoomId = existing.getId();
        RegisteredRoomUpdate update = manager.analyzeRegisteredRoomUpdate(village, expectedRoomId, source);
        if (update.result() != Building.validationResult.SUCCESS) {
            displayScanResult(player, update.result());
            return;
        }
        if (forcedType == null && update.isAmbiguous()) {
            requestType(update.playerMatchingTypes(), update.source(), player,
                    Action.UPDATE_ROOM, expectedRoomId);
            return;
        }
        displayScanResult(player, manager.commitRegisteredRoomUpdate(update, forcedType),
                "blueprint.roomUpdated");
    }

    private static void commitRoomAddition(VillageManager manager,
                                           ServerPlayer player,
                                           BuildingScanResult scan,
                                           String forcedType,
                                           Action action) {
        if (forcedType == null && scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
            int expectedTarget = action == Action.ADD_FLOOR || action == Action.ADD_BASEMENT
                    ? scan.targetBuildingId() : -1;
            requestType(scan.matchingTypes(), scan.source(), player, action, expectedTarget);
            return;
        }
        String successKey = switch (action) {
            case ADD_BUILDING -> "blueprint.buildingAdded";
            case ADD_FLOOR -> "blueprint.floorAdded";
            case ADD_BASEMENT -> "blueprint.basementAdded";
            default -> "blueprint.roomAdded";
        };
        displayScanResult(player, manager.commitRoomAddition(scan, forcedType), successKey);
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
        ADD_BASEMENT;

        public static final Action[] VALUES = values();
    }
}
