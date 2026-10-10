package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;

public record BuildingScanResult(
        Building.validationResult result,
        BlockPos source,
        Building building,
        List<String> matchingTypes,
        Village village,
        Structure pendingStructure
) {
    public BuildingScanResult(Building.validationResult result,
                              BlockPos source,
                              Building building,
                              List<String> matchingTypes,
                              Village village) {
        this(result, source, building, matchingTypes, village, null);
    }

    public BuildingScanResult {
        matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
    }

    public boolean isAmbiguous() {
        return RoomTypeResolver.requiresTypeChoice(matchingTypes);
    }

    public boolean matchesType(String type) {
        return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
    }

    BuildingScanResult withPendingStructure(Structure structure) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, structure);
    }

    BuildingScanResult withSource(BlockPos source) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, pendingStructure);
    }

    public int targetBuildingId() {
        if (pendingStructure == null || pendingStructure.getLogicalBuildingId() == pendingStructure.getId()) return -1;
        return pendingStructure.getLogicalBuildingId();
    }
}
