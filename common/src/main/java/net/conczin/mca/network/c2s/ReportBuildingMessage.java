package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BuildingPolymorphMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingScanResult;
import net.conczin.mca.server.world.data.InitialStructureScan;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
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
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ReportBuildingMessage::action,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(
                    optional -> optional.orElse(null), value -> value == null ? Optional.empty() : Optional.of(value)),
            ReportBuildingMessage::data,
            ReportBuildingMessage::new);

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager manager = VillageManager.get(player.serverLevel());
        try {
            switch (action) {
                case ADD -> addBuildingAndCurrentRoom(manager, player);
                case ADD_ROOM -> addRoom(manager, player);
                case UPDATE_ROOM -> updateRoom(manager, player, player.blockPosition(), null);
                case SET_GROUND_ANCHOR -> setGroundAnchor(manager, player);
                case AUTO_SCAN -> manager.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
                case FULL_SCAN -> manager.findNearestVillage(player).ifPresent(manager::fullScan);
                case FORCE_TYPE -> displayEditResult(player,
                        manager.forceRoomType(player.blockPosition(), data), null);
                case REMOVE_ROOM -> displayEditResult(player,
                        manager.removeRoom(player.blockPosition()), "blueprint.roomRemoved");
                case REMOVE -> displayEditResult(player,
                        manager.removeBuilding(player.blockPosition()), null);
            }
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    private static void addBuildingAndCurrentRoom(VillageManager manager, ServerPlayer player) {
        InitialStructureScan scan = manager.analyzeInitialStructure(player.blockPosition());
        if (scan.isRoomAmbiguous()) {
            requestType(scan.room(), player, Action.ADD);
            return;
        }
        commitBuildingAndCurrentRoom(manager, player, scan, null);
    }

    private static void addRoom(VillageManager manager, ServerPlayer player) {
        Village village = manager.findNearestVillage(player).orElse(null);
        if (village != null
                && village.getStructuralPosition(player.serverLevel(), player.blockPosition())
                == Village.StructuralPosition.REGISTERED_ROOM) {
            player.displayClientMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
            return;
        }
        commitNewRoom(manager, player, manager.analyzeRoom(player.blockPosition()), null);
    }

    private static void setGroundAnchor(VillageManager manager, ServerPlayer player) {
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
        if (village.isRootRoom(room)) {
            player.displayClientMessage(Component.translatable("blueprint.groundAnchorAlreadySet"), true);
            return;
        }
        if (!village.setStructureGroundFloorAnchor(room)) {
            player.displayClientMessage(Component.translatable("blueprint.groundAnchorNoStructure"), true);
            return;
        }
        player.displayClientMessage(Component.translatable("blueprint.groundAnchorSet"), true);
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
        BuildingScanResult scan = manager.analyzeRegisteredRoomUpdate(village, expectedRoomId, source);
        if (scan.result() != Building.validationResult.SUCCESS) {
            displayScanResult(player, scan.result());
            return;
        }
        if (forcedType == null && scan.isAmbiguous()) {
            requestType(scan, player, Action.UPDATE_ROOM, expectedRoomId);
            return;
        }
        Building.validationResult result = manager.commitRegisteredRoomUpdate(scan, expectedRoomId, forcedType);
        if (result == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable("blueprint.roomUpdated"), true);
        } else {
            displayScanResult(player, result);
        }
    }

    static void commitBuildingAndCurrentRoom(VillageManager manager,
                                              ServerPlayer player,
                                              InitialStructureScan scan,
                                              String forcedType) {
        Building.validationResult result = manager.commitInitialStructure(scan, forcedType);
        if (result == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable("blueprint.buildingAddedRoomAdded"), true);
        } else {
            displayScanResult(player, result);
        }
    }

    static void commitNewRoom(VillageManager manager,
                              ServerPlayer player,
                              BuildingScanResult scan,
                              String forcedType) {
        if (forcedType == null && scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
            requestType(scan, player, Action.ADD_ROOM);
            return;
        }
        Building.validationResult result = manager.commitBuilding(scan, forcedType);
        if (result == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable("blueprint.roomAdded"), true);
        } else {
            displayScanResult(player, result);
        }
    }

    private static void requestType(BuildingScanResult scan, ServerPlayer player, Action action) {
        requestType(scan, player, action, -1);
    }

    private static void requestType(BuildingScanResult scan,
                                    ServerPlayer player,
                                    Action action,
                                    int expectedRoomId) {
        Network.sendToPlayer(new BuildingPolymorphMessage(
                scan.matchingTypes(), scan.source(), action, expectedRoomId), player);
    }

    private static void displayScanResult(ServerPlayer player, Building.validationResult result) {
        player.displayClientMessage(Component.translatable(
                "blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
    }

    private static void displayEditResult(ServerPlayer player,
                                          VillageManager.BuildingEditResult result,
                                          String successKey) {
        String key = switch (result) {
            case SUCCESS -> successKey;
            case NO_BUILDING -> "blueprint.noBuilding";
            case NO_ROOM -> "blueprint.noRoomOnFloor";
            case ROOT_ROOM -> "blueprint.cannot_remove_ground_floor";
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
        ADD,
        REMOVE,
        FORCE_TYPE,
        FULL_SCAN,
        REMOVE_ROOM,
        UPDATE_ROOM,
        SET_GROUND_ANCHOR
    }
}
