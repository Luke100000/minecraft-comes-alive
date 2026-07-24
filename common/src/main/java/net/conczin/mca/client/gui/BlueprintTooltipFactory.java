package net.conczin.mca.client.gui;

import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomTypeResolver;
import net.conczin.mca.server.world.data.StructureLayout;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** Single owner for Blueprint Room/Structure tooltip hierarchy and styling. */
final class BlueprintTooltipFactory {
    private static final int FLOOR_BASEMENT_COLOR = 0x9b8cff;
    private static final int FLOOR_GROUND_COLOR = 0xf2c94c;
    private static final int FLOOR_UPPER_COLOR = 0x6fd6a5;

    private final Village village;
    private final BlueprintFloorLayout floorLayout;
    private final StructureLayout.Layout structureLayout;
    private final RoomTypeResolver roomTypeResolver;

    private BlueprintTooltipFactory(Village village,
                                    BlueprintFloorLayout floorLayout,
                                    StructureLayout.Layout structureLayout,
                                    RoomTypeResolver roomTypeResolver) {
        this.village = village;
        this.floorLayout = floorLayout;
        this.structureLayout = structureLayout;
        this.roomTypeResolver = roomTypeResolver;
    }

    static BlueprintTooltipFactory empty() {
        StructureLayout.Layout layout = StructureLayout.build(null);
        return new BlueprintTooltipFactory(null, BlueprintFloorLayout.empty(), layout,
                RoomTypeResolver.create(null, layout));
    }

    static BlueprintTooltipFactory create(Village village,
                                          BlueprintFloorLayout floorLayout,
                                          StructureLayout.Layout structureLayout,
                                          RoomTypeResolver roomTypeResolver) {
        return village == null ? empty()
                : new BlueprintTooltipFactory(village, floorLayout, structureLayout, roomTypeResolver);
    }

    List<Component> tooltip(Building hovered, Integer floorOrdinal, boolean structureHover) {
        if (village == null || hovered == null) return List.of();
        if (structureHover) {
            return floorOrdinal == null
                    ? allFloorsTooltip(hovered)
                    : structureFloorTooltip(hovered, floorOrdinal);
        }

        List<Component> lines = new LinkedList<>();
        if (floorOrdinal != null) lines.add(floorLabel(floorOrdinal));
        appendRoom(lines, hovered);
        return List.copyOf(lines);
    }

    private void appendRoom(List<Component> lines, Building room) {
        RoomTypeResolver.Context resolved = roomTypeResolver.resolve(room);
        BuildingType presentationType = roomTypeResolver.presentationType(room);
        if (presentationType == null) presentationType = room.getBuildingType();

        lines.add(Component.literal("  ").append(typeLabel(presentationType)));

        if (room.isFunctionalRoom() && resolved.isMainRoom()) {
            lines.add(Component.literal("    ").append(Component.translatable(
                    "gui.blueprint.roomTooltip.mainRoom").withStyle(ChatFormatting.GOLD)));
        } else if (room.isFunctionalRoom() && village.isRoomInheritance()
                && resolved.mainRoom() != null && !resolved.isMainRoom()) {
            lines.add(Component.literal("    ").append(Component.translatable(
                    "gui.blueprint.roomTooltip.contributesToMain").withStyle(ChatFormatting.DARK_AQUA)));
        }

        village.getResidents(room.getId()).forEach(name ->
                lines.add(Component.literal("    ")
                        .append(Component.literal(name).withStyle(ChatFormatting.GRAY))));

        if (!resolved.ownPoi().isEmpty()) {
            lines.add(Component.literal("    ").append(Component.translatable(
                    "gui.blueprint.roomTooltip.roomPoi").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            poiLines(resolved.ownPoi()).forEach(item ->
                    lines.add(Component.literal("      ").append(item)));
        }

        if (!resolved.inheritedPoi().isEmpty()) {
            lines.add(Component.literal("    ").append(Component.translatable(
                    "gui.blueprint.roomTooltip.inheritedPoi").withStyle(ChatFormatting.AQUA)));
            poiLines(resolved.inheritedPoi()).forEach(item ->
                    lines.add(Component.literal("      ").append(item)));
            resolved.contributors().forEach(contributor ->
                    lines.add(Component.literal("      ").append(Component.translatable(
                            "gui.blueprint.roomTooltip.inheritedFrom", contributor.getId())
                            .withStyle(ChatFormatting.DARK_GRAY))));
        }
    }

    private List<Component> structureFloorTooltip(Building structureBuilding, int floorOrdinal) {
        List<Component> lines = new LinkedList<>();
        lines.add(floorLabel(floorOrdinal));
        structureTooltipBuildings(structureBuilding).stream()
                .filter(room -> floorLayout.isBuildingVisible(room, floorOrdinal))
                .forEach(room -> appendRoom(lines, room));
        return List.copyOf(lines);
    }

    private List<Component> allFloorsTooltip(Building structureBuilding) {
        List<Building> structureRooms = structureTooltipBuildings(structureBuilding);
        List<Component> lines = new LinkedList<>();

        for (int floorOrdinal : floorLayout.ordinalsFor(structureBuilding)) {
            lines.add(floorLabel(floorOrdinal));
            structureRooms.stream()
                    .filter(room -> floorLayout.isBuildingVisible(room, floorOrdinal))
                    .forEach(room -> appendRoom(lines, room));
        }
        return List.copyOf(lines);
    }

    private List<Building> structureTooltipBuildings(Building building) {
        if (building.getBuildingType().grouped()) return List.of(building);

        int structureId = building.getEffectiveStructureId();
        List<Integer> structureIds = structureLayout.buildingFor(structureId)
                .map(StructureLayout.LogicalBuilding::structureIds)
                .orElse(List.of(structureId));
        return village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .filter(candidate -> structureIds.contains(candidate.getEffectiveStructureId()))
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
    }

    private static Component typeLabel(BuildingType type) {
        return Component.translatable("buildingType." + type.name())
                .withStyle(style -> style.withColor(type.getColor() & 0x00ffffff).withBold(true));
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
        List<Component> lines = new ArrayList<>();
        poi.forEach((block, positions) -> {
            if (!positions.isEmpty()) {
                lines.add(Component.literal(positions.size() + " x ")
                        .append(blockName(block))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        });
        return List.copyOf(lines);
    }

    private static Component blockName(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id)
                ? Component.translatable(BuiltInRegistries.BLOCK.get(id).getDescriptionId())
                : Component.translatable("tag.block." + id.getNamespace() + "." + id.getPath());
    }
}
