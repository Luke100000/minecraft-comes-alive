package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingTypesReloadTest {
    @Test
    void removedDatapackDefinitionsDisappearFromBothServerAndClientViews() {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> oldServerTypes = Map.copyOf(types.getServerBuildingTypes());
        Map<String, BuildingType> oldClientTypes = types.getBuildingTypes();
        ResourceLocation removed = ResourceLocation.fromNamespaceAndPath("mca", "removed_room");
        ResourceLocation retained = ResourceLocation.fromNamespaceAndPath("mca", "retained_room");
        JsonElement definition = new JsonObject();

        try {
            types.apply(Map.of(removed, definition, retained, definition), null, null);
            assertTrue(types.getServerBuildingTypes().containsKey("removed_room"));
            assertTrue(types.getBuildingTypes().containsKey("removed_room"));

            types.apply(Map.of(retained, definition), null, null);
            assertFalse(types.getServerBuildingTypes().containsKey("removed_room"));
            assertFalse(types.getBuildingTypes().containsKey("removed_room"));
            assertTrue(types.getServerBuildingTypes().containsKey("retained_room"));
        } finally {
            types.getServerBuildingTypes().clear();
            types.getServerBuildingTypes().putAll(oldServerTypes);
            types.setBuildingTypes(oldClientTypes);
        }
    }
}
