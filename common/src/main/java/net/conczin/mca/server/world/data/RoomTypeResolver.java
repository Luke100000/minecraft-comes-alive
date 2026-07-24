package net.conczin.mca.server.world.data;

import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Single derived view for Room-local and logical-Main inherited POIs/type matching. */
public final class RoomTypeResolver {
    private final Village village;
    private final StructureLayout.Layout layout;
    private final Map<Integer, Building> roomsById;
    private final Map<Integer, List<Building>> roomsByStructure;

    private RoomTypeResolver(Village village,
                             StructureLayout.Layout layout,
                             Collection<Building> rooms) {
        this.village = village;
        this.layout = layout == null ? StructureLayout.build(village) : layout;
        List<Building> snapshot = rooms == null ? List.of() : List.copyOf(rooms);
        Map<Integer, Building> byId = new HashMap<>();
        Map<Integer, List<Building>> byStructure = new HashMap<>();
        for (Building room : snapshot) {
            if (room.getId() >= 0) byId.put(room.getId(), room);
            byStructure.computeIfAbsent(room.getStructureId(), ignored -> new ArrayList<>()).add(room);
        }
        byStructure.replaceAll((ignored, grouped) -> List.copyOf(grouped));
        this.roomsById = Map.copyOf(byId);
        this.roomsByStructure = Map.copyOf(byStructure);
    }

    public static RoomTypeResolver create(Village village, StructureLayout.Layout layout) {
        return new RoomTypeResolver(village, layout,
                village == null ? List.of() : village.getRooms().toList());
    }

    static RoomTypeResolver create(Village village,
                                   StructureLayout.Layout layout,
                                   Collection<Building> rooms) {
        return new RoomTypeResolver(village, layout, rooms);
    }

    public Context resolve(Building room) {
        return resolve(room, findMainRoom(room));
    }

    /**
     * Client-facing Room presentation. Inherited Rooms share the logical Main Room's effective
     * type for colour/icon rendering without changing their persisted direct type.
     */
    public BuildingType presentationType(Building room) {
        if (room == null) return null;
        if (village == null || !village.isRoomInheritance()) return room.getBuildingType();
        Context context = resolve(room);
        if (context.isMainRoom() || context.mainRoom() == null) return context.effectiveType();
        return resolve(context.mainRoom()).effectiveType();
    }

    Context resolve(Building room, Building mainRoom) {
        Map<ResourceLocation, List<BlockPos>> own = snapshot(room == null ? Map.of() : room.getBlocks());
        if (village == null || room == null || !room.isFunctionalRoom()
                || mainRoom == null || !village.isRoomInheritance() || !sameRoom(mainRoom, room)) {
            return new Context(room, mainRoom == null ? room : mainRoom, own, Map.of(), own, List.of());
        }

        List<Building> logicalRooms = layout.buildingFor(room.getStructureId())
                .map(logical -> logical.structureIds().stream()
                        .flatMap(structureId -> roomsByStructure.getOrDefault(structureId, List.of()).stream())
                        .toList())
                .orElseGet(() -> roomsByStructure.getOrDefault(room.getStructureId(), List.of()));
        List<Building> contributors = logicalRooms.stream()
                .filter(Building::isFunctionalRoom)
                .filter(candidate -> !sameRoom(candidate, room))
                .filter(candidate -> !candidate.getBlocks().isEmpty())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();

        Map<ResourceLocation, LinkedHashSet<BlockPos>> inherited = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        contributors.forEach(contributor -> merge(inherited, contributor.getBlocks()));
        Map<ResourceLocation, List<BlockPos>> inheritedPoi = freeze(inherited);

        Map<ResourceLocation, LinkedHashSet<BlockPos>> effective = mutable(own);
        merge(effective, inheritedPoi);
        return new Context(room, mainRoom, own, inheritedPoi, freeze(effective), contributors);
    }

    private static boolean sameRoom(Building first, Building second) {
        return first == second || first != null && second != null
                && first.getId() >= 0 && first.getId() == second.getId();
    }

    private Building findMainRoom(Building room) {
        if (village == null || room == null) return room;
        int mainRoomId = layout.buildingFor(room.getStructureId())
                .map(StructureLayout.LogicalBuilding::mainRoomId).orElse(room.getId());
        if (room.getId() == mainRoomId) return room;
        Building snapshotRoom = roomsById.get(mainRoomId);
        if (snapshotRoom != null) return snapshotRoom;
        return village.getBuilding(mainRoomId).filter(Building::isFunctionalRoom).orElse(room);
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
            return sameRoom(room, mainRoom);
        }

        public Map<ResourceLocation, List<BlockPos>> classificationPoi() {
            return isMainRoom() ? effectivePoi : ownPoi;
        }

        public List<BuildingType> directMatchingTypes() {
            return Building.visibleMatchingTypes(ownPoi);
        }

        public List<BuildingType> visibleMatchingTypes() {
            return Building.visibleMatchingTypes(classificationPoi());
        }

        public boolean matchesForcedType(String typeName) {
            if (room == null || typeName == null) return false;
            BuildingType type = BuildingTypes.getInstance().getBuildingType(typeName);
            return Building.matchesType(type, classificationPoi());
        }

        /** Returns the direct type to persist after an update, or null when a forced type is invalid. */
        public String updatedType(String forcedType) {
            if (room == null) return null;
            if (forcedType != null) return matchesForcedType(forcedType) ? forcedType : null;
            List<BuildingType> matches = directMatchingTypes();
            return matches.isEmpty() ? (isMainRoom() ? "house" : "building") : matches.getFirst().name();
        }

        public BuildingType effectiveType() {
            if (room == null || room.isTypeForced() || !isMainRoom() || inheritedPoi.isEmpty()) {
                return room == null ? null : room.getBuildingType();
            }
            List<BuildingType> visible = visibleMatchingTypes();
            return visible.isEmpty() ? room.getBuildingType() : visible.getFirst();
        }
    }
}
