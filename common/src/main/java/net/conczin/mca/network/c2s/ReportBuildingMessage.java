package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BuildingPolymorphMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingScanResult;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

public record ReportBuildingMessage(Action action, String data) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ReportBuildingMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("report_building"));
    public static final StreamCodec<FriendlyByteBuf, ReportBuildingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ReportBuildingMessage::action,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).map(o -> o.orElse(null), o -> o == null ? java.util.Optional.empty() : java.util.Optional.of(o)), ReportBuildingMessage::data,
            ReportBuildingMessage::new
    );

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager villages = VillageManager.get(player.serverLevel());
        switch (action) {
            case ADD, ADD_ROOM -> {
                boolean isRoom = action == Action.ADD_ROOM;
                BuildingScanResult scan = villages.analyzeBuilding(player.blockPosition(), isRoom);
                if (scan.result() == Building.validationResult.SUCCESS && scan.isAmbiguous()) {
                    Network.sendToPlayer(new BuildingPolymorphMessage(scan.matchingTypes(), scan.source(), scan.strictScan()), player);
                } else {
                    Building.validationResult result = villages.commitBuilding(scan, null);
                    player.displayClientMessage(Component.translatable("blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
                }
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

    @Override
    public CustomPacketPayload.Type<ReportBuildingMessage> type() {
        return TYPE;
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
