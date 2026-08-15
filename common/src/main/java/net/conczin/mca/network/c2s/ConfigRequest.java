package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.s2c.ConfigResponse;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.io.Serial;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

public class ConfigRequest implements Message {
    @Serial
    private static final long serialVersionUID = 7108115056986169352L;

    @Override
    public void receive(ServerPlayer player) {
        NetworkHandler.sendToPlayer(new ConfigResponse(Config.getInstance(), getSyncedDestinySpawnLocations(player)), player);
    }

    private static List<String> getSyncedDestinySpawnLocations(ServerPlayer player) {
        LinkedHashSet<String> locations = new LinkedHashSet<>(Config.getInstance().destinySpawnLocations);
        Registry<Structure> structures = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        TreeSet<String> discoveredVillages = new TreeSet<>();

        structures.getTagOrEmpty(StructureTags.VILLAGE).forEach(village ->
                village.unwrapKey().ifPresent(key -> discoveredVillages.add(key.location().toString()))
        );
        structures.keySet().stream()
                .filter(id -> id.getPath().contains("village"))
                .map(ResourceLocation::toString)
                .forEach(discoveredVillages::add);

        locations.addAll(discoveredVillages);
        return List.copyOf(locations);
    }
}
