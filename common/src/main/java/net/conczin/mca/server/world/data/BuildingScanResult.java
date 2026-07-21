package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;

public record BuildingScanResult(
        Building.validationResult result,
        BlockPos source,
        Building building,
        List<String> matchingTypes,
        Village village,
        int existingBuildingId,
        int structureId,
        int floorId
) {
    public BuildingScanResult {
        matchingTypes = List.copyOf(matchingTypes);
    }


    public boolean isAmbiguous() {
        return matchingTypes.size() > 1;
    }

    public boolean matchesType(String type) {
        return matchingTypes.contains(type);
    }

    public boolean hasExistingBuilding() {
        return existingBuildingId >= 0;
    }
}
