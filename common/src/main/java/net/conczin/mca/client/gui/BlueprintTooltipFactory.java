package net.conczin.mca.client.gui;

import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomTypeResolver;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Single owner for Blueprint Room/Structure tooltip hierarchy and styling. */
final class BlueprintTooltipFactory {
    private static final int FLOOR_BASEMENT_COLOR = 0x9b8cff;
    private static final int FLOOR_GROUND_COLOR = 0xf2c94c;
    private static final int FLOOR_UPPER_COLOR = 0x6fd6a5;
    private static final String DETAIL_INDENT = "  ";

    private final Village village;
    private final RoomTypeResolver roomTypeResolver;

    private BlueprintTooltipFactory(Village village, RoomTypeResolver roomTypeResolver) {
        this.village = village;
        this.roomTypeResolver = roomTypeResolver;
    }

    static BlueprintTooltipFactory empty() {
        return new BlueprintTooltipFactory(null, RoomTypeResolver.create(null));
    }

    static BlueprintTooltipFactory create(Village village, RoomTypeResolver roomTypeResolver) {
        return village == null ? empty() : new BlueprintTooltipFactory(village, roomTypeResolver);
    }

    List<Component> tooltip(Building hovered, Integer floorOrdinal, boolean structureHover) {
        if (village == null || hovered == null) return List.of();
        if (!hovered.isFunctionalRoom()) return externalBuildingTooltip(hovered);
        if (structureHover) {
            return floorOrdinal == null
                    ? allFloorsTooltip(hovered)
                    : structureFloorTooltip(hovered, floorOrdinal);
        }

        RoomTypeResolver.Context resolved = roomTypeResolver.resolve(hovered);
        BuildingType presentationType = presentationType(hovered, resolved);
        List<Component> lines = new LinkedList<>();
        lines.add(typeLabel(presentationType));
        if (floorOrdinal != null) lines.add(floorStatusLabel(floorOrdinal, resolved.isMainRoom()));
        appendRoomDetails(lines, hovered, resolved);
        return List.copyOf(lines);
    }

    Component compactTooltip(Building building, Integer floorOrdinal, int relativeElevation) {
        if (village == null || building == null) return Component.empty();
        BuildingType presentationType = building.isFunctionalRoom()
                ? presentationType(building, roomTypeResolver.resolve(building))
                : building.getBuildingType();
        String marker = relativeElevation > 0 ? "▲ " : relativeElevation < 0 ? "▼ " : "• ";
        Component line = Component.literal(marker).withStyle(ChatFormatting.DARK_GRAY)
                .copy().append(typeLabel(presentationType));
        if (!building.isFunctionalRoom()) return detail(line);
        int floor = floorOrdinal == null ? building.getFloorNumber(village) : floorOrdinal;
        return detail(line.copy()
                .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(floorLabel(floor).copy().withStyle(style -> style.withBold(false))));
    }

    private BuildingType presentationType(Building room, RoomTypeResolver.Context resolved) {
        BuildingType presentationType = roomTypeResolver.presentationType(resolved);
        return presentationType == null ? room.getBuildingType() : presentationType;
    }

    private void appendRoomDetails(List<Component> lines,
                                   Building room,
                                   RoomTypeResolver.Context resolved) {
        if (!resolved.isMainRoom() && resolved.contributesToMain()) {
            lines.add(detail(Component.translatable("gui.blueprint.roomTooltip.contributesToMain")
                    .withStyle(ChatFormatting.DARK_AQUA)));
        }

        village.getResidents(room.getId()).forEach(name ->
                lines.add(detail(Component.literal(name).withStyle(ChatFormatting.GRAY))));

        appendPoi(lines, resolved.ownPoi(), Component.translatable("gui.blueprint.roomTooltip.roomPoi")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (!resolved.inheritedPoi().isEmpty()) {
            appendPoi(lines, resolved.inheritedPoi(), Component.translatable("gui.blueprint.roomTooltip.inheritedPoi")
                    .withStyle(ChatFormatting.AQUA));
            resolved.contributors().forEach(contributor -> lines.add(detail(Component.translatable(
                    "gui.blueprint.roomTooltip.inheritedFrom", contributor.getId()).withStyle(ChatFormatting.DARK_GRAY))));
        }
    }

    private List<Component> externalBuildingTooltip(Building building) {
        List<Component> lines = new ArrayList<>();
        lines.add(typeLabel(building.getBuildingType()));
        poiLines(building.getBlocks()).forEach(item -> lines.add(detail(item)));
        return List.copyOf(lines);
    }

    private List<Component> structureFloorTooltip(Building structureBuilding, int floorOrdinal) {
        List<Building> rooms = structureTooltipBuildings(structureBuilding).stream()
                .filter(room -> room.getFloorNumber(village) == floorOrdinal)
                .toList();
        RoomTypeResolver.Context titleContext = roomTypeResolver.resolve(structureBuilding);
        BuildingType titleType = presentationType(structureBuilding, titleContext);
        List<Component> lines = new LinkedList<>();
        lines.add(typeLabel(titleType));
        lines.add(floorStatusLabel(floorOrdinal,
                rooms.stream().map(roomTypeResolver::resolve).anyMatch(RoomTypeResolver.Context::isMainRoom)));
        appendAggregateRooms(lines, rooms, titleType);
        return List.copyOf(lines);
    }

    private List<Component> allFloorsTooltip(Building structureBuilding) {
        List<Building> structureRooms = structureTooltipBuildings(structureBuilding);
        RoomTypeResolver.Context titleContext = roomTypeResolver.resolve(structureBuilding);
        BuildingType titleType = presentationType(structureBuilding, titleContext);
        List<Component> lines = new LinkedList<>();
        lines.add(typeLabel(titleType));

        TreeSet<Integer> floors = new TreeSet<>();
        structureRooms.forEach(room -> floors.add(room.getFloorNumber(village)));
        for (int floorOrdinal : floors) {
            List<Building> floorRooms = structureRooms.stream()
                    .filter(room -> room.getFloorNumber(village) == floorOrdinal)
                    .toList();
            lines.add(floorStatusLabel(floorOrdinal,
                    floorRooms.stream().map(roomTypeResolver::resolve)
                            .anyMatch(RoomTypeResolver.Context::isMainRoom)));
            appendAggregateRooms(lines, floorRooms, titleType);
        }
        return List.copyOf(lines);
    }

    private void appendAggregateRooms(List<Component> lines,
                                      List<Building> rooms,
                                      BuildingType titleType) {
        List<RoomTypeResolver.Context> resolvedRooms = rooms.stream()
                .map(roomTypeResolver::resolve)
                .toList();

        Map<BuildingType, List<RoomTypeResolver.Context>> grouped = new LinkedHashMap<>();
        for (RoomTypeResolver.Context resolved : resolvedRooms) {
            BuildingType presentationType = roomTypeResolver.presentationType(resolved);
            if (presentationType == null) presentationType = resolved.room().getBuildingType();
            grouped.computeIfAbsent(presentationType, k -> new ArrayList<>()).add(resolved);
        }

        for (Map.Entry<BuildingType, List<RoomTypeResolver.Context>> entry : grouped.entrySet()) {
            BuildingType type = entry.getKey();
            List<RoomTypeResolver.Context> typeRooms = entry.getValue();

            boolean repeatType = grouped.size() > 1 || titleType == null || !titleType.name().equals(type.name());
            if (repeatType) lines.add(detail(typeLabel(type)));

            Set<String> residents = new LinkedHashSet<>();
            typeRooms.forEach(ctx -> village.getResidents(ctx.room().getId()).forEach(residents::add));
            residents.forEach(name ->
                    lines.add(detail(Component.literal(name).withStyle(ChatFormatting.GRAY))));

            Map<ResourceLocation, List<BlockPos>> combinedPoi = new LinkedHashMap<>();
            typeRooms.forEach(ctx -> ctx.ownPoi().forEach((k, v) ->
                    combinedPoi.computeIfAbsent(k, key -> new ArrayList<>()).addAll(v)));

            appendPoi(lines, combinedPoi, Component.translatable("gui.blueprint.roomTooltip.roomPoi")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private List<Building> structureTooltipBuildings(Building building) {
        if (!building.isFunctionalRoom() || building.getBuildingType().grouped() || village == null) {
            return List.of(building);
        }

        int buildingId = village.getLogicalBuildingId(building.getStructureId());
        return village.getRooms()
                .filter(Building::isComplete)
                .filter(candidate -> village.getLogicalBuildingId(
                        candidate.getStructureId()) == buildingId)
                .sorted(Comparator.comparingInt((Building room) -> room.getFloorNumber(village))
                        .thenComparingInt(Building::getId))
                .toList();
    }

    private static void appendPoi(List<Component> lines, Map<ResourceLocation, List<BlockPos>> poi, Component title) {
        if (poi.isEmpty()) return;
        lines.add(detail(title));
        poiLines(poi).forEach(item -> lines.add(detail(item)));
    }

    private static Component detail(Component component) {
        return Component.literal(DETAIL_INDENT).append(component);
    }

    private static Component typeLabel(BuildingType type) {
        return Component.translatable("buildingType." + type.name())
                .withStyle(style -> style.withColor(type.getColor() & 0x00ffffff).withBold(true));
    }

    private static Component floorStatusLabel(int floorOrdinal, boolean mainRoom) {
        Component floor = floorLabel(floorOrdinal);
        if (!mainRoom) return floor;
        return floor.copy()
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("gui.blueprint.roomTooltip.mainRoom")
                        .withStyle(ChatFormatting.GOLD));
    }

    private static Component floorLabel(int floorOrdinal) {
        int color = floorOrdinal < 0
                ? FLOOR_BASEMENT_COLOR
                : floorOrdinal == 0 ? FLOOR_GROUND_COLOR : FLOOR_UPPER_COLOR;
        Component label = floorOrdinal == 0
                ? Component.translatable("gui.blueprint.floor.ground")
                : floorOrdinal > 0
                ? Component.translatable("gui.blueprint.floor.upper", floorOrdinal)
                : Component.translatable("gui.blueprint.floor.basement", -floorOrdinal);
        return label.copy().withStyle(style -> style.withColor(color).withBold(true));
    }

    private static List<Component> poiLines(Map<ResourceLocation, List<BlockPos>> poi) {
        return poi.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .<Component>map(entry -> Component.literal(entry.getValue().size() + " x ")
                        .append(blockName(entry.getKey())).withStyle(ChatFormatting.DARK_GRAY))
                .toList();
    }

    private static Component blockName(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id)
                ? Component.translatable(BuiltInRegistries.BLOCK.get(id).getDescriptionId())
                : Component.translatable("tag.block." + id.getNamespace() + "." + id.getPath());
    }
}
