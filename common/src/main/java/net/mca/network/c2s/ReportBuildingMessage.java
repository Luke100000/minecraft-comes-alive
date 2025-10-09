package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;
import java.util.Locale;
import java.util.Optional;

public class ReportBuildingMessage implements Message {
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
    public void receive(ServerPlayer player) {
        VillageManager villages = VillageManager.get(player.serverLevel());
        switch (action) {
            case ADD, ADD_ROOM -> {
                Building.validationResult result = villages.processBuilding(player.blockPosition(), true, action == Action.ADD_ROOM);
                player.displayClientMessage(Component.translatable("blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
            }
            case AUTO_SCAN -> villages.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
            case FULL_SCAN -> villages.findNearestVillage(player).ifPresent(buildings ->
                    buildings.getBuildings().values().stream().toList().forEach(b ->
                            villages.processBuilding(b.getCenter(), true, b.isStrictScan())
                    )
            );
            case FORCE_TYPE, REMOVE -> {
                Optional<Village> village = villages.findNearestVillage(player);
                Optional<Building> building = village.flatMap(v -> v.getBuildings().values().stream()
                        .filter(b -> b.containsPos(player.blockPosition()))
                        .filter(b -> action != Action.FORCE_TYPE || !b.getBuildingType().grouped())
                        .findAny());
                building.ifPresentOrElse(b -> {
                    if (action == Action.FORCE_TYPE) {
                        if (b.getType().equals(data)) {
                            b.setTypeForced(false);
                            b.determineType();
                        } else {
                            b.setTypeForced(true);
                            b.setType(data);
                        }
                    } else {
                        //noinspection OptionalGetWithoutIsPresent
                        village.get().removeBuilding(b.getId());
                    }
                }, () -> {
                    player.displayClientMessage(Component.translatable("blueprint.noBuilding"), true);
                });
            }
        }
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
