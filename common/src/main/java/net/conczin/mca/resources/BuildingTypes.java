package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import net.conczin.mca.MCA;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class BuildingTypes extends SimpleJsonResourceReloadListener<JsonElement> implements Iterable<BuildingType> {
    protected static final Identifier ID = MCA.locate("building_types");
    private static BuildingTypes INSTANCE = new BuildingTypes();
    private final Map<String, BuildingType> buildingTypes = new HashMap<>();
    private final Map<String, BuildingType> buildingTypesClient = new HashMap<>();

    public BuildingTypes() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(ID.getPath()));
        INSTANCE = this;
    }

    public static BuildingTypes getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        for (Map.Entry<Identifier, JsonElement> pair : prepared.entrySet()) {
            String name = pair.getKey().getPath();
            buildingTypes.put(name, new BuildingType(name, pair.getValue().getAsJsonObject()));
        }
        setBuildingTypes(buildingTypes);
    }

    public Map<String, BuildingType> getServerBuildingTypes() {
        return buildingTypes;
    }

    public Map<String, BuildingType> getBuildingTypes() {
        return buildingTypesClient;
    }

    // Provide the client with building types
    public void setBuildingTypes(Map<String, BuildingType> buildingTypes) {
        buildingTypesClient.clear();
        buildingTypesClient.putAll(buildingTypes);
    }

    public BuildingType getBuildingType(String type) {
        return buildingTypesClient.containsKey(type) ? buildingTypesClient.get(type) : new BuildingType();
    }

    @Override
    public Iterator<BuildingType> iterator() {
        return buildingTypesClient.values().iterator();
    }
}
