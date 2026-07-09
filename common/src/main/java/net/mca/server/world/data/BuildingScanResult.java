package net.mca.server.world.data;

import net.minecraft.util.math.BlockPos;

import java.util.List;

public record BuildingScanResult(
        Building.validationResult result,
        BlockPos source,
        boolean strictScan,
        Building building,
        List<String> matchingTypes,
        Village village
) {
    public boolean isAmbiguous() {
        return matchingTypes.size() > 1;
    }

    public boolean matchesType(String type) {
        return matchingTypes.contains(type);
    }
}
