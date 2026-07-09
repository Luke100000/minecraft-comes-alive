package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.BuildingPolymorphMessage;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.BuildingScanResult;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.Locale;
import java.util.Optional;

public class ReportBuildingMessage implements Message {
    private static final int BUILDING_LOOKUP_HORIZONTAL_MARGIN = 1;
    private static final int BUILDING_LOOKUP_VERTICAL_MARGIN = 2;

    @Serial
    private static final long serialVersionUID = 3510050513221709603L;

    private final Action action;
    private final String data;

    public ReportBuildingMessage(Action action, String data) {
        this.action = action;
        this.data = data;
    }

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        VillageManager villages = VillageManager.get(player.getServerWorld());
        switch (action) {
            case ADD, ADD_ROOM -> {
                boolean isRoom = action == Action.ADD_ROOM;
                BuildingScanResult scan = villages.analyzeBuilding(player.getBlockPos(), isRoom);
                if (scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
                    NetworkHandler.sendToPlayer(new BuildingPolymorphMessage(scan.matchingTypes(), scan.source(), scan.strictScan()), player);
                } else {
                    Building.validationResult result = villages.commitBuilding(scan, null);
                    player.sendMessage(Text.translatable("blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
                }
            }
            case AUTO_SCAN -> villages.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
            case FULL_SCAN -> villages.findNearestVillage(player).ifPresent(buildings ->
                    buildings.getBuildings().values().stream().toList().forEach(b ->
                            villages.processBuilding(b.getCenter(), true, b.isStrictScan())
                    )
            );
            case FORCE_TYPE, REMOVE -> {
                BlockPos playerPos = player.getBlockPos();
                Optional<Village> village = villages.findNearestVillage(player);

                Building targetBuilding = null;
                Village targetVillage = null;
                boolean targetExact = false;
                double targetDistance = Double.MAX_VALUE;

                if (village.isPresent()) {
                    Village candidateVillage = village.get();
                    for (Building building : candidateVillage.getBuildings().values()) {
                        if (action == Action.FORCE_TYPE && building.getBuildingType().grouped()) {
                            continue;
                        }

                        boolean exact = building.containsPos(playerPos);
                        boolean lenient = containsLenient(building, playerPos);
                        if (!exact && !lenient) {
                            continue;
                        }

                        double distance = building.getCenter().getSquaredDistance(playerPos);
                        if (targetBuilding == null
                                || (exact && !targetExact)
                                || (exact == targetExact && distance < targetDistance)) {
                            targetBuilding = building;
                            targetVillage = candidateVillage;
                            targetExact = exact;
                            targetDistance = distance;
                        }
                    }
                }

                if (targetBuilding != null && targetVillage != null) {
                    if (action == Action.FORCE_TYPE) {
                        if (targetBuilding.getType().equals(data)) {
                            targetBuilding.setTypeForced(false);
                            targetBuilding.determineType();
                        } else {
                            targetBuilding.setTypeForced(true);
                            targetBuilding.setType(data);
                        }
                        targetVillage.markDirty();
                    } else {
                        targetVillage.removeBuilding(targetBuilding.getId());
                    }
                } else {
                    player.sendMessage(Text.translatable("blueprint.noBuilding"), true);
                }
            }
        }
    }


    private static boolean containsLenient(Building building, BlockPos pos) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();

        return pos.getX() >= p0.getX() - BUILDING_LOOKUP_HORIZONTAL_MARGIN && pos.getX() <= p1.getX() + BUILDING_LOOKUP_HORIZONTAL_MARGIN
                && pos.getY() >= p0.getY() - BUILDING_LOOKUP_VERTICAL_MARGIN && pos.getY() <= p1.getY() + BUILDING_LOOKUP_VERTICAL_MARGIN
                && pos.getZ() >= p0.getZ() - BUILDING_LOOKUP_HORIZONTAL_MARGIN && pos.getZ() <= p1.getZ() + BUILDING_LOOKUP_HORIZONTAL_MARGIN;
    }

    public enum Action {
        AUTO_SCAN,
        ADD_ROOM,
        ADD,
        REMOVE,
        FORCE_TYPE,
        FULL_SCAN
    }
}
