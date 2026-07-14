package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BuildingPolymorphMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingScanResult;
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
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(o -> o.orElse(null), o -> o == null ? java.util.Optional.empty() : java.util.Optional.of(o)), ReportBuildingMessage::data,
            ReportBuildingMessage::new
    );

    private static final int BUILDING_LOOKUP_HORIZONTAL_MARGIN = 1;
    private static final int BUILDING_LOOKUP_VERTICAL_MARGIN = 2;

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager villages = VillageManager.get(player.serverLevel());
        MCA.LOGGER.info("[BuildingSelection] request action={} player={} pos={}",
                action, player.getName().getString(), player.blockPosition());
        switch (action) {
            case ADD -> addBuildingAndCurrentRoom(villages, player);
            case ADD_ROOM -> addRoom(villages, player);
            case AUTO_SCAN -> villages.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
            case FULL_SCAN -> villages.findNearestVillage(player).ifPresent(village -> {
                villages.ensureStructureHierarchy(village);
                List<Integer> ids = village.getBuildings().keySet().stream().sorted().toList();
                ids.forEach(id -> villages.rescanBuilding(village, id));
            });
            case FORCE_TYPE, REMOVE, REMOVE_ROOM -> {
                BlockPos playerPos = player.blockPosition();
                Optional<Village> village = villages.findNearestVillage(player);

                if (action == Action.REMOVE_ROOM
                        && village.filter(v -> isOnStructuralGroundFloor(v, playerPos)).isPresent()) {
                    MCA.LOGGER.info("[BuildingSelection] rejected ground-floor room removal player={} pos={}",
                            player.getName().getString(), playerPos);
                    return;
                }

                Building targetBuilding = null;
                Village targetVillage = null;
                boolean targetExact = false;
                double targetDistance = Double.MAX_VALUE;

                if (village.isPresent()) {
                    Village v = village.get();
                    villages.ensureStructureHierarchy(v);
                    for (Building b : v.getBuildings().values()) {
                        if ((action == Action.FORCE_TYPE && b.getBuildingType().grouped())
                                || (action == Action.REMOVE_ROOM
                                && (b.getBuildingType().grouped() || !b.isStrictScan()))) {
                            continue;
                        }

                        boolean exact = action == Action.REMOVE_ROOM
                                ? b.containsFloorPosition(playerPos)
                                : b.containsPos(playerPos);
                        boolean lenient = containsLenient(b, playerPos)
                                && (action != Action.REMOVE_ROOM
                                || b.getFloorDistanceTo(playerPos) <= BUILDING_LOOKUP_VERTICAL_MARGIN);

                        MCA.LOGGER.info(
                                "[BuildingSelection] candidate action={} village={} id={} structure={} root={} strict={} grouped={} complete={} exact={} lenient={} bounds={}..{}",
                                action, v.getId(), b.getId(), b.getEffectiveStructureId(), b.isStructureRoot(),
                                b.isStrictScan(), b.getBuildingType().grouped(), b.isComplete(), exact, lenient,
                                b.getPos0(), b.getPos1());

                        if (!exact && !lenient) {
                            continue;
                        }

                        double distance = action == Action.REMOVE_ROOM
                                ? b.getFloorDistanceTo(playerPos)
                                : b.getCenter().distSqr(playerPos);
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
                    MCA.LOGGER.info(
                            "[BuildingSelection] selected action={} village={} id={} structure={} root={} strict={} exact={} memberCount={}",
                            action, targetVillage.getId(), targetBuilding.getId(),
                            targetBuilding.getEffectiveStructureId(), targetBuilding.isStructureRoot(),
                            targetBuilding.isStrictScan(), targetExact,
                            villages.getStructureMemberCount(targetVillage, targetBuilding.getEffectiveStructureId()));
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
                        int structureId = targetBuilding.getEffectiveStructureId();
                        if (isStructuralGroundFloor(targetVillage, targetBuilding)) {
                            MCA.LOGGER.info("[BuildingSelection] rejected ground-floor room removal player={} village={} id={}",
                                    player.getName().getString(), targetVillage.getId(), targetBuilding.getId());
                            return;
                        }
                        if (targetBuilding.isStructureRoot()
                                && villages.getStructureMemberCount(targetVillage, structureId) > 1) {
                            player.displayClientMessage(
                                    Component.translatable("blueprint.cannot_remove_root_room"), true);
                            return;
                        }

                        targetVillage.removeBuilding(targetBuilding.getId());
                        MCA.LOGGER.info(
                                "[BuildingSelection] removed room village={} id={} structure={} remainingBuildings={} remainingMembers={}",
                                targetVillage.getId(), targetBuilding.getId(), structureId,
                                targetVillage.getBuildings().size(),
                                villages.getStructureMemberCount(targetVillage, structureId));
                        if (targetVillage.getBuildings().isEmpty()) {
                            villages.removeVillage(targetVillage.getId());
                        }
                    } else if (targetBuilding.getBuildingType().grouped()) {
                        targetVillage.removeBuilding(targetBuilding.getId());
                        MCA.LOGGER.info(
                                "[BuildingSelection] removed grouped building village={} id={} remainingBuildings={}",
                                targetVillage.getId(), targetBuilding.getId(), targetVillage.getBuildings().size());
                    } else {
                        int structureId = targetBuilding.getEffectiveStructureId();
                        villages.removeStructure(targetVillage, structureId);
                        MCA.LOGGER.info(
                                "[BuildingSelection] removed structure village={} structure={} remainingBuildings={} remainingMembers={}",
                                targetVillage.getId(), structureId, targetVillage.getBuildings().size(),
                                villages.getStructureMemberCount(targetVillage, structureId));
                    }
                } else {
                    MCA.LOGGER.info("[BuildingSelection] no target action={} player={} pos={} village={}",
                            action, player.getName().getString(), playerPos,
                            village.map(Village::getId).orElse(-1));
                    if (action == Action.REMOVE_ROOM
                            && village.filter(v -> isInsideStructuralBuilding(v, playerPos)).isPresent()) {
                        MCA.LOGGER.info("[BuildingSelection] no room on current floor player={} pos={} village={}",
                                player.getName().getString(), playerPos, village.get().getId());
                        player.displayClientMessage(Component.translatable("blueprint.noRoomOnFloor"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("blueprint.noBuilding"), true);
                    }
                }
            }
        }
    }

    private static void addBuildingAndCurrentRoom(VillageManager villages, ServerPlayer player) {
        BuildingScanResult buildingScan = villages.analyzeBuilding(player.blockPosition(), false);
        if (buildingScan.result() == Building.validationResult.SUCCESS && buildingScan.isAmbiguous()) {
            Network.sendToPlayer(new BuildingPolymorphMessage(
                    buildingScan.matchingTypes(), buildingScan.source(), buildingScan.strictScan()), player);
            return;
        }

        Building.validationResult buildingResult = villages.commitBuilding(buildingScan, null);
        if (buildingResult != Building.validationResult.SUCCESS) {
            displayScanResult(player, buildingResult);
            return;
        }

        BuildingScanResult roomScan = villages.analyzeRoom(player.blockPosition());
        if (roomScan.result() == Building.validationResult.SUCCESS && roomScan.isAmbiguous()) {
            Network.sendToPlayer(new BuildingPolymorphMessage(roomScan.matchingTypes(), roomScan.source(), true), player);
            player.displayClientMessage(Component.translatable("blueprint.buildingAddedChooseRoomType"), true);
            return;
        }

        Building.validationResult roomResult = villages.commitBuilding(roomScan, null);
        if (roomResult == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable(
                    roomScan.hasExistingBuilding() ? "blueprint.buildingUpdatedRoomExists" : "blueprint.buildingAddedRoomAdded"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "blueprint.buildingAddedRoomFailed",
                    Component.translatable("blueprint.scan." + roomResult.name().toLowerCase(Locale.ENGLISH))), true);
        }
    }

    private static void addRoom(VillageManager villages, ServerPlayer player) {
        BuildingScanResult scan = villages.analyzeRoom(player.blockPosition());
        if (scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
            Network.sendToPlayer(new BuildingPolymorphMessage(scan.matchingTypes(), scan.source(), true), player);
            return;
        }

        Building.validationResult result = villages.commitBuilding(scan, null);
        if (result == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable(
                    scan.hasExistingBuilding() ? "blueprint.roomAlreadyAdded" : "blueprint.roomAdded"), true);
        } else {
            displayScanResult(player, result);
        }
    }

    private static void displayScanResult(ServerPlayer player, Building.validationResult result) {
        player.displayClientMessage(Component.translatable(
                "blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
    }

    private static boolean isInsideStructuralBuilding(Village village, BlockPos pos) {
        return village.getBuildings().values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .anyMatch(building -> building.containsFloorPosition(pos) || containsLenient(building, pos));
    }

    private static boolean isStructuralGroundFloor(Village village, Building room) {
        return village.getBuildings().values().stream()
                .filter(Building::isStructureRoot)
                .filter(root -> root.getEffectiveStructureId() == room.getEffectiveStructureId())
                .anyMatch(root -> Math.abs(lowestFloorY(root) - room.getFloorY()) <= BUILDING_LOOKUP_VERTICAL_MARGIN);
    }

    private static boolean isOnStructuralGroundFloor(Village village, BlockPos pos) {
        return village.getBuildings().values().stream()
                .filter(Building::isStructureRoot)
                .filter(root -> !root.getBuildingType().grouped())
                .anyMatch(root -> root.containsFloorPosition(pos)
                        && Math.abs(lowestFloorY(root) - pos.getY()) <= BUILDING_LOOKUP_VERTICAL_MARGIN);
    }

    private static int lowestFloorY(Building building) {
        return building.getFloorRegions().stream()
                .mapToInt(region -> region.anchorY())
                .min()
                .orElse(building.getFloorY());
    }

    private static boolean containsLenient(Building building, BlockPos pos) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();

        return pos.getX() >= p0.getX() - BUILDING_LOOKUP_HORIZONTAL_MARGIN && pos.getX() <= p1.getX() + BUILDING_LOOKUP_HORIZONTAL_MARGIN
                && pos.getY() >= p0.getY() - BUILDING_LOOKUP_VERTICAL_MARGIN && pos.getY() <= p1.getY() + BUILDING_LOOKUP_VERTICAL_MARGIN
                && pos.getZ() >= p0.getZ() - BUILDING_LOOKUP_HORIZONTAL_MARGIN && pos.getZ() <= p1.getZ() + BUILDING_LOOKUP_HORIZONTAL_MARGIN;
    }

    private static boolean containsHorizontally(Building building, BlockPos pos) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();

        return pos.getX() >= p0.getX() - BUILDING_LOOKUP_HORIZONTAL_MARGIN
                && pos.getX() <= p1.getX() + BUILDING_LOOKUP_HORIZONTAL_MARGIN
                && pos.getZ() >= p0.getZ() - BUILDING_LOOKUP_HORIZONTAL_MARGIN
                && pos.getZ() <= p1.getZ() + BUILDING_LOOKUP_HORIZONTAL_MARGIN;
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
        REMOVE_ROOM
    }
}
