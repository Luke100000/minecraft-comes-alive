package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BuildingPolymorphMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingFloorRegion;
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
        if (action == Action.ADD || action == Action.ADD_ROOM || action == Action.UPDATE_ROOM) {
            BlockPos playerPos = player.blockPosition();
            Village nearestVillage = villages.findNearestVillage(player).orElse(null);
            Village.StructuralLookup structuralLookup = nearestVillage == null
                    ? null
                    : nearestVillage.getStructuralLookup(playerPos);
            MCA.LOGGER.info("[FloorRoomDebug] side=server stage=receive-request action={} pos={} villageId={} lookup={} lookupBuilding={}",
                    action, playerPos, nearestVillage == null ? -1 : nearestVillage.getId(),
                    structuralLookup == null ? Village.StructuralPosition.OUTSIDE : structuralLookup.position(),
                    structuralLookup == null ? "none" : describeBuilding(structuralLookup.building().orElse(null)));
        }
        try {
            switch (action) {
            case ADD -> addBuildingAndCurrentRoom(villages, player);
            case ADD_ROOM -> addRoom(villages, player);
            case UPDATE_ROOM -> updateRoom(villages, player, player.blockPosition(), null);
            case AUTO_SCAN -> villages.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
            case FULL_SCAN -> villages.findNearestVillage(player).ifPresent(village -> {
                villages.ensureStructureHierarchy(village);
                List<Integer> ids = village.getBuildings().keySet().stream().sorted().toList();
                ids.forEach(id -> villages.rescanBuilding(village, id));
            });
            case FORCE_TYPE, REMOVE, REMOVE_ROOM -> {
                BlockPos playerPos = player.blockPosition();
                Optional<Village> village = villages.findNearestVillage(player);
                MCA.LOGGER.debug("[BuildingRemove] stage=received action={} source={} villageId={}",
                        action, playerPos, village.map(Village::getId).orElse(-1));

                if (action == Action.REMOVE_ROOM
                        && village.flatMap(v -> v.getFunctionalRoomAt(playerPos)
                                .filter(v::isStructuralGroundFloor)).isPresent()) {
                    MCA.LOGGER.debug("[BuildingRemove] stage=blocked-ground-floor source={}", playerPos);
                    player.displayClientMessage(Component.translatable("blueprint.cannot_remove_ground_floor"), true);
                    return;
                }

                Building targetBuilding = null;
                Village targetVillage = null;
                boolean targetExact = false;
                double targetDistance = Double.MAX_VALUE;

                if (village.isPresent()) {
                    Village v = village.get();
                    villages.ensureStructureHierarchy(v);
                    if (action == Action.REMOVE_ROOM) {
                        targetBuilding = v.getFunctionalRoomAt(playerPos).orElse(null);
                        if (targetBuilding != null) {
                            targetVillage = v;
                            targetExact = targetBuilding.containsRawPos(playerPos);
                        }
                    } else if (action == Action.FORCE_TYPE) {
                        targetBuilding = v.getFunctionalRoomAt(playerPos).orElse(null);
                        if (targetBuilding != null) {
                            targetVillage = v;
                            targetExact = targetBuilding.containsRawPos(playerPos);
                        }
                    } else {
                        for (Building b : v.getBuildings().values()) {
                            boolean exact = b.containsPos(playerPos);
                            boolean lenient = b.containsPositionWithMargin(
                                    playerPos,
                                    Building.PLAYER_POSITION_HORIZONTAL_MARGIN,
                                    Building.PLAYER_POSITION_VERTICAL_MARGIN);
                            if (!exact && !lenient) {
                                continue;
                            }

                            double distance = b.getCenter().distSqr(playerPos);
                            if (targetBuilding == null
                                    || (exact && !targetExact)
                                    || (exact == targetExact && distance < targetDistance)) {
                                targetBuilding = b;
                                targetVillage = v;
                                targetExact = exact;
                                targetDistance = distance;
                            }
                        }
                    }
                }

                // Remove Building may recover a remaining structure from X/Z after an
                // upstairs room was removed, but never from arbitrarily far above/below.
                if (targetBuilding == null && action == Action.REMOVE && village.isPresent()) {
                    targetVillage = village.get();
                    for (Building b : targetVillage.getBuildings().values()) {
                        if (b.getBuildingType().grouped()
                                || !containsHorizontally(b, playerPos)
                                || b.getVerticalDistanceTo(playerPos) > 16) {
                            continue;
                        }

                        double distance = b.getCenter().distSqr(playerPos);
                        if (targetBuilding == null || distance < targetDistance) {
                            targetBuilding = b;
                            targetDistance = distance;
                        }
                    }
                }

                if (targetBuilding != null && targetVillage != null) {
                    MCA.LOGGER.debug(
                            "[BuildingRemove] stage=target action={} source={} targetId={} structureId={} strict={} root={} exact={}",
                            action, playerPos, targetBuilding.getId(), targetBuilding.getEffectiveStructureId(),
                            targetBuilding.isStrictScan(), targetBuilding.isStructureRoot(), targetExact);
                    if (action == Action.FORCE_TYPE) {
                        if (targetBuilding.getType().equals(data)) {
                            targetBuilding.setTypeForced(false);
                            targetBuilding.determineType();
                        } else {
                            targetBuilding.setTypeForced(true);
                            targetBuilding.setType(data);
                        }
                        targetVillage.markDirty();
                    } else if (action == Action.REMOVE_ROOM) {
                        if (targetVillage.isStructuralGroundFloor(targetBuilding)) {
                            player.displayClientMessage(Component.translatable("blueprint.cannot_remove_ground_floor"), true);
                            return;
                        }
                        targetVillage.removeBuilding(targetBuilding.getId());
                        MCA.LOGGER.debug("[BuildingRemove] stage=room-removed source={} targetId={}",
                                playerPos, targetBuilding.getId());
                        player.displayClientMessage(Component.translatable("blueprint.roomRemoved"), true);
                        if (targetVillage.getBuildings().isEmpty()) {
                            villages.removeVillage(targetVillage.getId());
                        }
                    } else if (targetBuilding.getBuildingType().grouped()) {
                        targetVillage.removeBuilding(targetBuilding.getId());
                        MCA.LOGGER.debug("[BuildingRemove] stage=building-removed source={} targetId={}",
                                playerPos, targetBuilding.getId());
                    } else {
                        int structureId = targetBuilding.getEffectiveStructureId();
                        villages.removeStructure(targetVillage, structureId);
                        MCA.LOGGER.debug("[BuildingRemove] stage=structure-removed source={} structureId={}",
                                playerPos, structureId);
                    }
                } else {
                    MCA.LOGGER.debug("[BuildingRemove] stage=no-target action={} source={} insideStructure={}",
                            action, playerPos,
                            village.map(v -> v.hasStructuralBuildingAt(playerPos)).orElse(false));
                    if (action == Action.REMOVE_ROOM
                            && village.filter(v -> v.hasStructuralBuildingAt(playerPos)).isPresent()) {
                        player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("blueprint.noBuilding"), true);
                    }
                }
            }
            }
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    private static void addBuildingAndCurrentRoom(VillageManager villages, ServerPlayer player) {
        InitialStructureScan scan = villages.analyzeInitialStructure(player.blockPosition());
        logAddScan("building-scan", scan.root());
        logAddScan("initial-room-scan", scan.room());
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
            logAddScan("add-overlap-room-recovery-scan", roomScan);
            MCA.LOGGER.info("[BuildingAdd] stage=add-overlap-room-recovery source={} roomResult={} existing={} merged={}",
                    roomScan.source(), roomScan.result(), roomScan.existingBuildingId(), roomScan.mergedBuildingIds());
            if (roomScan.result() == Building.validationResult.SUCCESS) {
                commitRoom(villages, player, roomScan, null, false);
                return;
            }
        }
        if (scan.isRoomAmbiguous()) {
            requestType(scan.room(), player, Action.ADD);
            return;
        }
        commitBuildingAndCurrentRoom(villages, player, scan, null);
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
        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=add-room-entry source={} villageId={} lookup={} lookupBuilding={}",
                source, nearestVillage == null ? -1 : nearestVillage.getId(), structuralPosition,
                structuralLookup == null ? "none" : describeBuilding(structuralLookup.building().orElse(null)));
        if (structuralPosition == Village.StructuralPosition.REGISTERED_ROOM) {
            player.displayClientMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
            return;
        }
        BuildingScanResult scan = villages.analyzeRoom(player.blockPosition());
        logAddScan("add-room-scan", scan);
        commitRoom(villages, player, scan, null, false);
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
        BuildingScanResult scan = villages.analyzeRegisteredRoom(village, expectedRoomId, source);
        logAddScan("update-room-scan", scan);
        if (scan.result() == Building.validationResult.SUCCESS
                && (scan.existingBuildingId() != expectedRoomId || !scan.mergedBuildingIds().isEmpty())) {
            player.displayClientMessage(Component.translatable("blueprint.roomUpdateConflict"), true);
            return;
        }
        commitRoom(villages, player, scan, forcedType, true);
    }

    static void commitBuildingAndCurrentRoom(VillageManager villages,
                                             ServerPlayer player,
                                             InitialStructureScan scan,
                                             String forcedType) {
        Building.validationResult result = villages.commitInitialStructure(scan, forcedType);
        MCA.LOGGER.debug("[BuildingAdd] stage=building-commit result={} source={} existing={}",
                result, scan.root().source(), scan.root().existingBuildingId());
        if (result != Building.validationResult.SUCCESS) {
            displayScanResult(player, result);
            return;
        }
        player.displayClientMessage(Component.translatable("blueprint.buildingAddedRoomAdded"), true);
    }

    static void commitRoom(VillageManager villages,
                           ServerPlayer player,
                           BuildingScanResult scan,
                           String forcedType,
                           boolean updating) {
        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=room-commit-before updating={} source={} scanResult={} candidate={} scanVillageId={} existing={} merged={} forcedType={}",
                updating, scan.source(), scan.result(), describeBuilding(scan.building()),
                scan.village() == null ? -1 : scan.village().getId(), scan.existingBuildingId(),
                scan.mergedBuildingIds(), forcedType);
        MCA.LOGGER.debug("[BuildingAdd] stage={} result={} source={} existing={}",
                updating ? "update-room-commit" : "add-room-commit",
                scan.result(), scan.source(), scan.existingBuildingId());
        if (!updating && scan.village() != null
                && scan.village().getStructuralPosition(scan.source()) == Village.StructuralPosition.REGISTERED_ROOM) {
            player.displayClientMessage(Component.translatable("blueprint.roomAlreadyAdded"), true);
            return;
        }
        if (updating
                && scan.result() == Building.validationResult.SUCCESS
                && !scan.hasExistingBuilding()) {
            player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
            return;
        }
        if (forcedType == null && scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
            Action action = updating ? Action.UPDATE_ROOM : Action.ADD_ROOM;
            requestType(scan, player, action, updating ? scan.existingBuildingId() : -1);
            return;
        }

        Building.validationResult result = villages.commitBuilding(scan, forcedType);
        Village persistedVillage = villages.findNearestVillage(scan.source(), Village.MERGE_MARGIN)
                .orElse(scan.village());
        Village.StructuralLookup persistedLookup = persistedVillage == null
                ? null
                : persistedVillage.getStructuralLookup(scan.source());
        Building persistedRoom = persistedVillage == null
                ? null
                : persistedVillage.getFunctionalRoomAt(scan.source()).orElse(null);
        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=room-commit-after updating={} source={} result={} candidate={} persistedVillageId={} persistedLookup={} persistedLookupBuilding={} persistedRoom={} buildingCount={}",
                updating, scan.source(), result, describeBuilding(scan.building()),
                persistedVillage == null ? -1 : persistedVillage.getId(),
                persistedLookup == null ? Village.StructuralPosition.OUTSIDE : persistedLookup.position(),
                persistedLookup == null ? "none" : describeBuilding(persistedLookup.building().orElse(null)),
                describeBuilding(persistedRoom), persistedVillage == null ? 0 : persistedVillage.getBuildings().size());
        logVillageBuildings("room-commit-after", persistedVillage);
        MCA.LOGGER.debug("[BuildingAdd] stage={} result={} source={} existing={}",
                updating ? "update-room-commit-result" : "add-room-commit-result",
                result, scan.source(), scan.existingBuildingId());
        if (result == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable(updating ? "blueprint.roomUpdated" : "blueprint.roomAdded"), true);
        } else {
            displayScanResult(player, result);
        }
    }

    private static void logAddScan(String stage, BuildingScanResult scan) {
        Building building = scan.building();
        MCA.LOGGER.debug(
                "[BuildingAdd] stage={} result={} source={} strict={} ambiguous={} types={} existing={} merged={} floorY={} groundFloorY={} floorRegions={} bounds={}..{}",
                stage, scan.result(), scan.source(), scan.strictScan(), scan.isAmbiguous(), scan.matchingTypes(),
                scan.existingBuildingId(), scan.mergedBuildingIds(), building.getFloorY(), building.getGroundFloorY(),
                building.getFloorRegions().stream().map(BuildingFloorRegion::anchorY).toList(),
                building.getPos0(), building.getPos1());
    }

    private static void logVillageBuildings(String stage, Village village) {
        if (village == null) {
            return;
        }
        village.getBuildings().values().stream()
                .sorted(java.util.Comparator.comparingInt(Building::getId))
                .forEach(building -> MCA.LOGGER.info(
                        "[FloorRoomDebug] side=server stage={} villageId={} {}",
                        stage, village.getId(), describeBuilding(building)));
    }

    private static String describeBuilding(Building building) {
        if (building == null) {
            return "none";
        }
        return "id=" + building.getId()
                + ",structure=" + building.getEffectiveStructureId()
                + ",root=" + building.isStructureRoot()
                + ",strict=" + building.isStrictScan()
                + ",functional=" + building.isFunctionalRoom()
                + ",floorY=" + building.getFloorY()
                + ",groundFloorY=" + building.getGroundFloorY()
                + ",floorRegions=" + building.getFloorRegions().stream().map(BuildingFloorRegion::anchorY).toList()
                + ",source=" + building.getSourceBlock()
                + ",bounds=" + building.getPos0() + ".." + building.getPos1();
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

    private static Village.StructuralPosition getStructuralPosition(VillageManager villages, ServerPlayer player) {
        return villages.findNearestVillage(player)
                .map(village -> village.getStructuralPosition(player.blockPosition()))
                .orElse(Village.StructuralPosition.OUTSIDE);
    }

    private static boolean containsHorizontally(Building building, BlockPos pos) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();

        return pos.getX() >= p0.getX() - Building.PLAYER_POSITION_HORIZONTAL_MARGIN
                && pos.getX() <= p1.getX() + Building.PLAYER_POSITION_HORIZONTAL_MARGIN
                && pos.getZ() >= p0.getZ() - Building.PLAYER_POSITION_HORIZONTAL_MARGIN
                && pos.getZ() <= p1.getZ() + Building.PLAYER_POSITION_HORIZONTAL_MARGIN;
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
        UPDATE_ROOM
    }
}
