package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.StructureLayout;
import net.conczin.mca.server.world.data.Village;

import java.util.List;
import java.util.OptionalInt;

/** Blueprint-facing names for the canonical floor placement held by {@link StructureLayout}. */
final class BlueprintFloorLayout {
    private final StructureLayout.Layout layout;

    private BlueprintFloorLayout(StructureLayout.Layout layout) {
        this.layout = layout;
    }

    static BlueprintFloorLayout empty() {
        return new BlueprintFloorLayout(StructureLayout.build(null));
    }

    static BlueprintFloorLayout build(Village village) {
        return new BlueprintFloorLayout(StructureLayout.build(village));
    }

    static BlueprintFloorLayout build(Village village, StructureLayout.Layout layout) {
        return village == null ? empty() : new BlueprintFloorLayout(layout);
    }

    List<Integer> ordinals() {
        return layout.ordinals();
    }

    List<Integer> ordinalsFor(Building building) {
        List<Integer> structural = layout.ordinalsForStructure(building.getEffectiveStructureId());
        if (!structural.isEmpty()) return structural;
        OptionalInt ordinal = layout.ordinalForBuilding(building.getId());
        return ordinal.isPresent() ? List.of(ordinal.getAsInt()) : List.of();
    }

    OptionalInt ordinalForFloor(int structureId, int floorId) {
        return layout.ordinal(structureId, floorId);
    }

    OptionalInt rootRoomIdForStructure(int structureId) {
        return layout.rootRoomIdForStructure(structureId);
    }

    boolean isBuildingOnFloor(Building building, int floorOrdinal) {
        return layout.ordinalForBuilding(building.getId()).orElse(Integer.MIN_VALUE) == floorOrdinal;
    }

    boolean isBuildingVisible(Building building, Integer selectedFloor) {
        OptionalInt ordinal = layout.ordinalForBuilding(building.getId());
        return ordinal.isPresent() && (selectedFloor == null || ordinal.getAsInt() == selectedFloor);
    }

    OptionalInt floorOrdinalFor(Building building) {
        return layout.ordinalForBuilding(building.getId());
    }
}
