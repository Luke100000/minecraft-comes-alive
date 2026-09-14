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
        PendingFloorRefresh pendingFloorRefresh
) {
    public BuildingScanResult(Building.validationResult result,
                              BlockPos source,
                              Building building,
                              List<String> matchingTypes,
                              Village village) {
        this(result, source, building, matchingTypes, village, null, null);
    }

    public BuildingScanResult {
        matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
        if (pendingStructure != null && pendingFloorRefresh != null) {
            throw new IllegalArgumentException("Scan cannot carry two pending structure mutations");
        }
    }

    public boolean isAmbiguous() {
        return RoomTypeResolver.requiresTypeChoice(matchingTypes);
    }

    public boolean matchesType(String type) {
        return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
    }

    BuildingScanResult withPendingStructure(Structure structure) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, structure, null);
    }

    BuildingScanResult withPendingFloorRefresh(Structure structure, List<Building> rooms) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, null, new PendingFloorRefresh(structure, rooms));
    }

    BuildingScanResult withSource(BlockPos source) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, pendingStructure, pendingFloorRefresh);
    }

    public int targetBuildingId() {
        Structure structure = pendingFloorRefresh == null ? pendingStructure : pendingFloorRefresh.structure();
        if (structure == null || structure.getLogicalBuildingId() == structure.getId()) return -1;
        return structure.getLogicalBuildingId();
    }

    public record PendingFloorRefresh(Structure structure, List<Building> existingRooms) {
        public PendingFloorRefresh {
            if (structure == null) throw new IllegalArgumentException("Pending Floor refresh requires a Structure");
            existingRooms = existingRooms == null ? List.of() : List.copyOf(existingRooms);
        }
    }
}
