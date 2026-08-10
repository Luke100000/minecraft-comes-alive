package net.mca.server.world.data;

import java.util.List;
import net.minecraft.core.BlockPos;

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
