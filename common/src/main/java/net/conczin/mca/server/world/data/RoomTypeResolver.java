package net.conczin.mca.server.world.data;

import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Single derived view for Room-local and logical-Main inherited POIs/type matching. */
public final class RoomTypeResolver {
    private RoomTypeResolver() {
    }

    public static Context resolve(Village village, StructureLayout.Layout layout, Building room) {
        Collection<Building> rooms = village == null ? List.of() : village.getRooms().toList();
        return resolve(village, layout, room, rooms, findMainRoom(village, layout, room, rooms));
    }

    static Context resolve(Village village,
                           StructureLayout.Layout layout,
                           Building room,
                           Collection<Building> rooms) {
        return resolve(village, layout, room, rooms, findMainRoom(village, layout, room, rooms));
    }

    static Context resolve(Village village,
                           StructureLayout.Layout layout,
                           Building room,
                           Collection<Building> rooms,
                           Building mainRoom) {
        Map<ResourceLocation, List<BlockPos>> own = snapshot(room == null ? Map.of() : room.getBlocks());
        if (village == null || layout == null || room == null || !room.isFunctionalRoom()
                || mainRoom == null || !village.isRoomInheritance() || mainRoom.getId() != room.getId()) {
            return new Context(room, mainRoom == null ? room : mainRoom, own, Map.of(), own, List.of());
        }

        Set<Integer> structureIds = new HashSet<>(layout.buildingFor(room.getStructureId())
                .map(StructureLayout.LogicalBuilding::structureIds).orElse(List.of(room.getStructureId())));
        List<Building> contributors = rooms.stream()
                .filter(Building::isFunctionalRoom)
                .filter(candidate -> candidate.getId() != room.getId())
                .filter(candidate -> structureIds.contains(candidate.getStructureId()))
                .filter(candidate -> !candidate.getBlocks().isEmpty())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();

        Map<ResourceLocation, LinkedHashSet<BlockPos>> inherited = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        contributors.forEach(contributor -> merge(inherited, contributor.getBlocks()));
        Map<ResourceLocation, List<BlockPos>> inheritedPoi = freeze(inherited);

        Map<ResourceLocation, LinkedHashSet<BlockPos>> effective = mutable(own);
        merge(effective, inheritedPoi);
        Map<ResourceLocation, List<BlockPos>> effectivePoi = freeze(effective);
        return new Context(room, mainRoom, own, inheritedPoi, effectivePoi, contributors);
    }

    private static Building findMainRoom(Village village,
                                         StructureLayout.Layout layout,
                                         Building room,
                                         Collection<Building> rooms) {
        if (village == null || layout == null || room == null) return room;
        int mainRoomId = layout.buildingFor(room.getStructureId())
                .map(StructureLayout.LogicalBuilding::mainRoomId).orElse(room.getId());
        if (room.getId() == mainRoomId) return room;
        return rooms.stream().filter(candidate -> candidate.getId() == mainRoomId).findFirst()
                .orElseGet(() -> village.getBuilding(mainRoomId).orElse(room));
    }

    private static Map<ResourceLocation, List<BlockPos>> snapshot(Map<ResourceLocation, List<BlockPos>> source) {
        Map<ResourceLocation, List<BlockPos>> copy = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        source.forEach((key, positions) -> copy.put(key, List.copyOf(positions)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, LinkedHashSet<BlockPos>> mutable(Map<ResourceLocation, List<BlockPos>> source) {
        Map<ResourceLocation, LinkedHashSet<BlockPos>> result = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        merge(result, source);
        return result;
    }

    private static void merge(Map<ResourceLocation, LinkedHashSet<BlockPos>> target,
                              Map<ResourceLocation, List<BlockPos>> source) {
        source.forEach((key, positions) -> target.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(positions));
    }

    private static Map<ResourceLocation, List<BlockPos>> freeze(Map<ResourceLocation, LinkedHashSet<BlockPos>> source) {
        Map<ResourceLocation, List<BlockPos>> result = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        source.forEach((key, positions) -> result.put(key, List.copyOf(positions)));
        return Collections.unmodifiableMap(result);
    }

    public record Context(Building room,
                          Building mainRoom,
                          Map<ResourceLocation, List<BlockPos>> ownPoi,
                          Map<ResourceLocation, List<BlockPos>> inheritedPoi,
                          Map<ResourceLocation, List<BlockPos>> effectivePoi,
                          List<Building> contributors) {
        public Context {
            ownPoi = snapshot(ownPoi);
            inheritedPoi = snapshot(inheritedPoi);
            effectivePoi = snapshot(effectivePoi);
            contributors = List.copyOf(contributors);
        }

        public boolean isMainRoom() {
            return room != null && mainRoom != null && room.getId() == mainRoom.getId();
        }

        public Map<ResourceLocation, List<BlockPos>> classificationPoi() {
            return isMainRoom() ? effectivePoi : ownPoi;
        }

        public List<BuildingType> visibleMatchingTypes() {
            return Building.visibleMatchingTypes(classificationPoi());
        }

        public BuildingType effectiveType() {
            if (room == null || room.isTypeForced() || inheritedPoi.isEmpty()) return room == null ? null : room.getBuildingType();
            List<BuildingType> visible = visibleMatchingTypes();
            return visible.isEmpty() ? room.getBuildingType() : visible.getFirst();
        }
    }
}
