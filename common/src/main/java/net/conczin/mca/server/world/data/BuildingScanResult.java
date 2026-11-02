package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public record BuildingScanResult(
        Building.validationResult result,
        BlockPos source,
        Building building,
        List<String> matchingTypes,
        Village village,
        Structure pendingStructure,
        RoomScanPlan plan
) {
    public BuildingScanResult(Building.validationResult result,
                              BlockPos source,
                              Building building,
                              List<String> matchingTypes,
                              Village village,
                              RoomScanPlan plan) {
        this(result, source, building, matchingTypes, village, null, plan);
    }

    public BuildingScanResult {
        matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
        if (result == Building.validationResult.SUCCESS) Objects.requireNonNull(plan, "plan");
    }

    public boolean isAmbiguous() {
        return RoomTypeResolver.requiresTypeChoice(matchingTypes);
    }

    public boolean matchesType(String type) {
        return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
    }

    BuildingScanResult withPendingStructure(Structure structure) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, structure, plan);
    }

    BuildingScanResult withSource(BlockPos source) {
        return new BuildingScanResult(result, source, building, matchingTypes,
                village, pendingStructure, plan);
    }

    public int targetBuildingId() {
        return plan != null && plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT ? plan.targetBuildingId() : -1;
    }
}
