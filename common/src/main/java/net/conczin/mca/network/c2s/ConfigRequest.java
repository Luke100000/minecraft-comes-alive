package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.ConfigResponse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

public record ConfigRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfigRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("config_request"));
    public static final StreamCodec<FriendlyByteBuf, ConfigRequest> STREAM_CODEC = StreamCodec.unit(new ConfigRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        Network.sendToPlayer(new ConfigResponse(Config.getInstance(), getSyncedDestinySpawnLocations(player)), player);
    }

    private static List<String> getSyncedDestinySpawnLocations(ServerPlayer player) {
        LinkedHashSet<String> locations = new LinkedHashSet<>(Config.getInstance().destinySpawnLocations);
        Registry<Structure> structures = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        TreeSet<String> discoveredVillages = new TreeSet<>();

        structures.getTagOrEmpty(StructureTags.VILLAGE).forEach(village ->
                village.unwrapKey().ifPresent(key -> discoveredVillages.add(key.location().toString()))
        );

        // Fallback for structure mods that register village-named structures without adding them to #minecraft:village.
        structures.keySet().stream()
                .filter(id -> id.getPath().contains("village"))
                .map(ResourceLocation::toString)
                .forEach(discoveredVillages::add);

        locations.addAll(discoveredVillages);
        return List.copyOf(locations);
    }

    @Override
    public CustomPacketPayload.Type<ConfigRequest> type() {
        return TYPE;
    }
}
