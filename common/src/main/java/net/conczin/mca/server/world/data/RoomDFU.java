package net.conczin.mca.server.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/** One-time compatibility boundary from origin/1.21.1 Village Building persistence. */
final class RoomDFU {
    private RoomDFU() {
    }

    static Result load(CompoundTag villageTag) {
        Map<Integer, Building> buildings = new HashMap<>();
        Map<Integer, ExternalBuilding> externalBuildings = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();

        if (villageTag.contains("structures", Tag.TAG_LIST)) {
            for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
                Building room = new Building((CompoundTag) value);
                buildings.put(room.getId(), room);
            }
            for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
                ExternalBuilding external = new ExternalBuilding((CompoundTag) value);
                externalBuildings.put(external.getId(), external);
            }
            for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
                Structure structure = new Structure((CompoundTag) value);
                structures.put(structure.getId(), structure);
            }
            return new Result(buildings, externalBuildings, structures);
        }

        ListTag legacy = villageTag.getList("buildings", Tag.TAG_COMPOUND);
        boolean unreleasedFloorFormat = legacy.stream()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .anyMatch(tag -> tag.contains("structureRoot")
                        || tag.contains("structureId")
                        || tag.contains("floorRegions"));
        if (unreleasedFloorFormat) {
            // The floor-system branch was never a supported persistence format. Do not carry its
            // hidden-root hierarchy into the production runtime; users of those development saves
            // can rescan/re-register Structures instead.
            return new Result(buildings, externalBuildings, structures);
        }

        // origin/1.21.1 stored every normal building as one Building record. Convert each
        // normal record into one Structure with one Root Room. Grouped records become
        // ExternalBuilding. The unreleased hidden-root floor-system format is intentionally
        // not repaired here.
        for (Tag value : legacy) {
            CompoundTag tag = (CompoundTag) value;
            Building probe = new Building(tag);
            if (probe.getBuildingType().grouped()) {
                ExternalBuilding external = new ExternalBuilding(tag);
                externalBuildings.put(external.getId(), external);
                continue;
            }
            Structure structure = Structure.fromLegacyBuilding(probe);
            buildings.put(probe.getId(), probe);
            structures.put(structure.getId(), structure);
        }
        return new Result(buildings, externalBuildings, structures);
    }

    record Result(Map<Integer, Building> buildings, Map<Integer, ExternalBuilding> externalBuildings, Map<Integer, Structure> structures) {
    }
}
