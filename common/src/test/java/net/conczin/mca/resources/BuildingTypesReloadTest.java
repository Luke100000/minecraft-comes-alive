package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingTypesReloadTest {
    @Test
    void removedDatapackDefinitionsDisappearFromBothServerAndClientViews() {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> oldServerTypes = Map.copyOf(types.getServerBuildingTypes());
        Map<String, BuildingType> oldClientTypes = types.getBuildingTypes();
        Identifier removed = Identifier.fromNamespaceAndPath("mca", "removed_room");
        Identifier retained = Identifier.fromNamespaceAndPath("mca", "retained_room");
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

    @Test
    void buildingTypePublishesCompleteBlockAndGroupMapsUnderConcurrentFirstAccess() throws Exception {
        JsonObject blocks = new JsonObject();
        for (int i = 0; i < 64; i++) {
            blocks.addProperty("minecraft:block_" + i, i + 1);
        }
        JsonObject definition = new JsonObject();
        definition.add("blocks", blocks);
        BuildingType type = new BuildingType("concurrent_room", definition);

        int threads = 8;
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<Thread> workers = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    assertEquals(64, type.getBlockToGroup().size());
                    assertEquals(64, type.getGroups().size());
                    assertTrue(type.matchesBlock(Identifier.fromNamespaceAndPath("minecraft", "block_63")));
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            workers.add(worker);
            worker.start();
        }
        ready.await();
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        assertTrue(failures.isEmpty(), () -> "Concurrent access failed: " + failures);
    }
}
