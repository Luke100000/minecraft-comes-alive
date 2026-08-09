package net.mca.network.c2s;

import net.mca.Config;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.ConfigResponse;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.Structure;

import java.io.Serial;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

public class ConfigRequest implements Message {
    @Serial
    private static final long serialVersionUID = 7108115056986169352L;

    @Override
    public void receive(ServerPlayerEntity player) {
        NetworkHandler.sendToPlayer(new ConfigResponse(Config.getInstance(), getSyncedDestinySpawnLocations(player)), player);
    }

    private static List<String> getSyncedDestinySpawnLocations(ServerPlayerEntity player) {
        LinkedHashSet<String> locations = new LinkedHashSet<>(Config.getInstance().destinySpawnLocations);
        Registry<Structure> structures = player.getServerWorld().getRegistryManager().get(RegistryKeys.STRUCTURE);
        TreeSet<String> discoveredVillages = new TreeSet<>();

        structures.iterateEntries(StructureTags.VILLAGE).forEach(village ->
                village.getKey().ifPresent(key -> discoveredVillages.add(key.getValue().toString()))
        );
        structures.getIds().stream()
                .filter(id -> id.getPath().contains("village"))
                .map(Identifier::toString)
                .forEach(discoveredVillages::add);

        locations.addAll(discoveredVillages);
        return List.copyOf(locations);
    }
}
