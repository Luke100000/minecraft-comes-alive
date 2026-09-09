package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;

public record BuildingScanResult(
        Building.validationResult result,
        BlockPos source,
        Building building,
        List<String> matchingTypes,
        Village village,
        Structure pendingStructure,
        List<Building> pendingFloorRooms
) {
    public BuildingScanResult(Building.validationResult result,
                              BlockPos source,
                              Building building,
                              List<String> matchingTypes,
                              Village village) {
        this(result, source, building, matchingTypes, village, null, List.of());
    }

    public BuildingScanResult {
        matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
        pendingFloorRooms = pendingFloorRooms == null ? List.of() : List.copyOf(pendingFloorRooms);
    }

    public boolean isAmbiguous() {
        return RoomTypeResolver.requiresTypeChoice(matchingTypes);
    }

    public boolean matchesType(String type) {
        return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
    }

    BuildingScanResult withPendingStructure(Structure structure) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, structure, pendingFloorRooms);
    }

    BuildingScanResult withPendingFloorRooms(List<Building> rooms) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, pendingStructure, rooms);
    }

    boolean hasPendingFloorRefresh() {
        return !pendingFloorRooms.isEmpty();
    }

    BuildingScanResult withSource(BlockPos source) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, pendingStructure, pendingFloorRooms);
    }

    public int targetBuildingId() {
        if (pendingStructure == null || pendingStructure.getLogicalBuildingId() == pendingStructure.getId()) return -1;
        return pendingStructure.getLogicalBuildingId();
    }
}
