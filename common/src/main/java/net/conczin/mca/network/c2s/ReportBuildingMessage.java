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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record ReportBuildingMessage(Action action, String data) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ReportBuildingMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("report_building"));
    public static final StreamCodec<FriendlyByteBuf, ReportBuildingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ReportBuildingMessage::action,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(
                    optional -> optional.orElse(null), value -> value == null ? Optional.empty() : Optional.of(value)),
            ReportBuildingMessage::data,
            ReportBuildingMessage::new
    );

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager villages = VillageManager.get(player.serverLevel());
        try {
            switch (action) {
            case ADD -> addBuildingAndCurrentRoom(villages, player);
            case ADD_ROOM -> addRoom(villages, player);
            case UPDATE_ROOM -> updateRoom(villages, player, player.blockPosition(), null);
            case SET_GROUND_ANCHOR -> setGroundAnchor(villages, player);
            case AUTO_SCAN -> villages.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
            case FULL_SCAN -> villages.findNearestVillage(player).ifPresent(village -> {
                villages.ensureStructureHierarchy(village);
                List<Integer> ids = village.getBuildings().keySet().stream().sorted().toList();
                ids.forEach(id -> villages.rescanBuilding(village, id));
            });
            case FORCE_TYPE -> displayEditResult(player,
                    villages.forceRoomType(player.blockPosition(), data), null);
            case REMOVE_ROOM -> displayEditResult(player,
                    villages.removeRoom(player.blockPosition()), "blueprint.roomRemoved");
            case REMOVE -> displayEditResult(player,
                    villages.removeBuilding(player.blockPosition()), null);
            }
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    private static void addBuildingAndCurrentRoom(VillageManager villages, ServerPlayer player) {
        InitialStructureScan scan = villages.analyzeInitialStructure(player.blockPosition());
        if (scan.root().result() == Building.validationResult.OVERLAP) {
            /*
             * The client decides between Add Building and Add Room from the last
             * persisted village geometry. After a player opens a basement/stairwell,
             * that cached structure can briefly classify the player as OUTSIDE even
             * though a strict room scan can attach to exactly one existing structure.
             * Recover only through the normal Add Room path; assignNewRoom still
             * rejects no-structure, cross-structure, and ambiguous attachments.
             */
            BuildingScanResult roomScan = villages.analyzeRoom(player.blockPosition());
            if (roomScan.result() == Building.validationResult.SUCCESS) {
                commitNewRoom(villages, player, roomScan, null);
                return;
            }
        }
        if (scan.isRoomAmbiguous()) {
            requestType(scan.room(), player, Action.ADD);
            return;
        }
        commitBuildingAndCurrentRoom(villages, player, scan, null);
    }

    private static void setGroundAnchor(VillageManager villages, ServerPlayer player) {
        BlockPos source = player.blockPosition();
        Village village = villages.findNearestVillage(player).orElse(null);
        if (village == null) {
            player.displayClientMessage(Component.translatable("blueprint.noBuilding"), true);
            return;
        }

        villages.ensureStructureHierarchy(village);
        Building room = village.getFunctionalRoomAt(source).orElse(null);
        if (room == null) {
            player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
            return;
        }
        if (village.isStructuralGroundFloor(room)) {
            player.displayClientMessage(Component.translatable("blueprint.groundAnchorAlreadySet"), true);
            return;
        }
        if (!village.setStructureGroundFloorAnchor(room)) {
            player.displayClientMessage(Component.translatable("blueprint.groundAnchorNoStructure"), true);
            return;
        }

        player.displayClientMessage(Component.translatable("blueprint.groundAnchorSet"), true);
    }

    private static void addRoom(VillageManager villages, ServerPlayer player) {
        BlockPos source = player.blockPosition();
        Village nearestVillage = villages.findNearestVillage(player).orElse(null);
        Village.StructuralLookup structuralLookup = nearestVillage == null
                ? null
                : nearestVillage.getStructuralLookup(source);
        Village.StructuralPosition structuralPosition = structuralLookup == null
                ? Village.StructuralPosition.OUTSIDE
                : structuralLookup.position();
        if (structuralPosition == Village.StructuralPosition.REGISTERED_ROOM) {
            player.displayClientMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
            return;
        }
        BuildingScanResult scan = villages.analyzeRoom(player.blockPosition());
        commitNewRoom(villages, player, scan, null);
    }

    static void updateRoom(VillageManager villages, ServerPlayer player, BlockPos source, String forcedType) {
        updateRoom(villages, player, source, forcedType, -1);
    }

    static void updateRoom(VillageManager villages,
                           ServerPlayer player,
                           BlockPos source,
                           String forcedType,
                           int originalExpectedRoomId) {
        Village village = villages.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        Building existing = village == null ? null : village.getFunctionalRoomAt(source).orElse(null);
        if (originalExpectedRoomId >= 0
                && (existing == null || existing.getId() != originalExpectedRoomId)) {
            player.displayClientMessage(Component.translatable("blueprint.roomUpdateConflict"), true);
            return;
        }
        if (existing == null) {
            player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
            return;
        }

        int expectedRoomId = existing.getId();
        VillageManager.RoomUpdatePlan update = villages.analyzeRegisteredRoomUpdate(
                village, expectedRoomId, source);
        if (update.kind() == VillageManager.RoomUpdatePlan.Kind.CONFLICT) {
            player.displayClientMessage(Component.translatable("blueprint.roomUpdateConflict"), true);
            return;
        }
        if (update.kind() == VillageManager.RoomUpdatePlan.Kind.FAILURE) {
            displayScanResult(player, update.result());
            return;
        }
        if (forcedType == null && update.isAmbiguous()) {
            requestType(update.requested(), player, Action.UPDATE_ROOM, expectedRoomId);
            return;
        }

        Building.validationResult result = villages.commitRegisteredRoomUpdate(update, forcedType);
        if (result == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable("blueprint.roomUpdated"), true);
        } else {
            displayScanResult(player, result);
        }
    }

    static void commitBuildingAndCurrentRoom(VillageManager villages,
                                             ServerPlayer player,
                                             InitialStructureScan scan,
                                             String forcedType) {
        Building.validationResult result = villages.commitInitialStructure(scan, forcedType);
        if (result != Building.validationResult.SUCCESS) {
            displayScanResult(player, result);
            return;
        }
        player.displayClientMessage(Component.translatable("blueprint.buildingAddedRoomAdded"), true);
    }

    static void commitNewRoom(VillageManager villages,
                              ServerPlayer player,
                              BuildingScanResult scan,
                              String forcedType) {
        if (scan.village() != null
                && scan.village().getStructuralPosition(scan.source()) == Village.StructuralPosition.REGISTERED_ROOM) {
            player.displayClientMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
            return;
        }
        if (forcedType == null && scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
            requestType(scan, player, Action.ADD_ROOM);
            return;
        }

        Building.validationResult result = villages.commitBuilding(scan, forcedType);
        if (result == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable("blueprint.roomAdded"), true);
        } else {
            displayScanResult(player, result);
        }
    }

    private static void requestType(BuildingScanResult scan,
                                    ServerPlayer player,
                                    Action action) {
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
            case GROUND_FLOOR -> "blueprint.cannot_remove_ground_floor";
        };
        if (key != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
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
