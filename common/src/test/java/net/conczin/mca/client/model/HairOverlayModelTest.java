package net.conczin.mca.client.model;

import net.conczin.mca.Config;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the actual baked hair geometry without launching the game renderer. */
class HairOverlayModelTest {
    private static boolean originalEnableBoobs;

    @BeforeAll
    static void enableGeometry() {
        originalEnableBoobs = Config.getInstance().enableBoobs;
        Config.getInstance().enableBoobs = true;
    }

    @AfterAll
    static void restoreConfig() {
        Config.getInstance().enableBoobs = originalEnableBoobs;
    }

    @Test
    void projectedHairUsesTorsoUvsAndCoversTheUpperBreastSurface() throws Exception {
        ModelPart breast = bakedHair(MCALayerDefinitions.VILLAGER_HAIR_DILATION)
                .getChild("body").getChild(MCAModelGeometry.BREAST_TRANSFORM).getChild(MCAModelGeometry.BREASTS);

        int topFaces = 0;
        for (Object polygon : polygons(breast)) {
            Vector3f normal = (Vector3f) field(polygon, "normal");
            if (normal.y() != -1.0F) {
                continue;
            }
            topFaces++;
            for (Object vertex : (Object[]) field(polygon, "vertices")) {
                Vector3f pos = (Vector3f) field(vertex, "pos");
                float u = (float) field(vertex, "u") * 64.0F;
                float v = (float) field(vertex, "v") * 64.0F;
                assertEquals(MCAModelGeometry.BREAST_Y, pos.y());
                assertTrue(u >= 21.0F && u <= 27.0F, "Upper face must sample torso hair columns");
                assertTrue(v >= 21.0F && v <= 24.0F, "Upper face must sample torso hair rows");
            }
        }
        assertEquals(1, topFaces, "Hair needs an actual baked upper breast face");

        ModelPart outerHair = breast.getChild(MCAModelGeometry.BREASTPLATE);
        assertEquals(2, polygons(outerHair).size(), "Outer hair needs front and upper faces");
        for (Object polygon : polygons(outerHair)) {
            for (Object vertex : (Object[]) field(polygon, "vertices")) {
                float u = (float) field(vertex, "u") * 64.0F;
                float v = (float) field(vertex, "v") * 64.0F;
                assertTrue(u >= 21.0F && u <= 27.0F, "Outer hair must sample torso columns");
                assertTrue(v >= 37.0F && v <= 43.0F, "Outer hair must sample outer torso rows");
            }
        }
    }

    @Test
    void villagerAndZombieHairStayCloseToClothing() {
        float[][] dilations = {
                {MCALayerDefinitions.VILLAGER_HAIR_DILATION, MCALayerDefinitions.VILLAGER_CLOTHING_DILATION},
                {MCALayerDefinitions.ZOMBIE_VILLAGER_HAIR_DILATION, MCALayerDefinitions.ZOMBIE_VILLAGER_CLOTHING_DILATION}
        };
        for (float[] pair : dilations) {
            ModelPart root = bakedHair(pair[0]);
            new HairOverlayModel<>(root, pair[1]);
            ModelPart breast = root.getChild("body")
                    .getChild(MCAModelGeometry.BREAST_TRANSFORM).getChild(MCAModelGeometry.BREASTS);
            ModelPart outerHair = breast.getChild(MCAModelGeometry.BREASTPLATE);
            float clothingWidth = MCAModelGeometry.BREAST_WIDTH
                    + 2.0F * (pair[1] + MCAModelGeometry.BREAST_WEAR_DILATION);
            float baseGap = (MCAModelGeometry.BREAST_WIDTH * breast.xScale - clothingWidth) / 2.0F;
            float outerGap = (MCAModelGeometry.BREAST_WIDTH * breast.xScale * outerHair.xScale - clothingWidth) / 2.0F;

            assertTrue(baseGap > 0.0F, "Hair must clear clothing");
            assertTrue(outerGap > baseGap, "Outer hair must clear inner hair");
            assertTrue(outerGap <= 0.02F, "Outer hair must not float above clothing");
        }
    }

    private static ModelPart bakedHair(float dilation) {
        return LayerDefinition.create(MCAModelGeometry.hairData(dilation), 64, 64).bakeRoot();
    }

    private static List<Object> polygons(ModelPart part) throws ReflectiveOperationException {
        List<Object> result = new java.util.ArrayList<>();
        for (Object cube : (List<?>) field(part, "cubes")) {
            result.addAll(List.of((Object[]) field(cube, "polygons")));
        }
        return result;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
