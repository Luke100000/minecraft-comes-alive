package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import java.util.List;

public record BuildingScanResult(
    Building.validationResult result,
    BlockPos source,
    boolean strictScan,
    Building building,
    List<String> matchingTypes,
    Village village,
    int existingBuildingId,
    List<Integer> mergedBuildingIds
) {
    public BuildingScanResult {
        matchingTypes = List.copyOf(matchingTypes);
        mergedBuildingIds = List.copyOf(mergedBuildingIds);
    }

    public BuildingScanResult(
            Building.validationResult result,
            BlockPos source,
            boolean strictScan,
            Building building,
            List<String> matchingTypes,
            Village village
    ) {
        this(result, source, strictScan, building, matchingTypes, village, -1, List.of());
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
