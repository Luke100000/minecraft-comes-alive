package net.conczin.mca.client.model;

import net.conczin.mca.Config;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the actual baked hair geometry without launching the game renderer. */
class HairOverlayModelTest {
    private static boolean originalEnableBoobs;

    @BeforeAll
    static void enableGeometry() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalEnableBoobs = Config.getInstance().enableBoobs;
        Config.getInstance().enableBoobs = true;
    }

    @AfterAll
    static void restoreConfig() {
        Config.getInstance().enableBoobs = originalEnableBoobs;
    }

    @Test
    void projectedHairUsesTorsoUvsAndCoversTheUpperBreastSurface() throws Exception {
        ModelPart pivot = bakedHair(MCALayerDefinitions.VILLAGER_HAIR_DILATION)
                .getChild("body").getChild(MCAModelGeometry.BREAST_TRANSFORM)
                .getChild(MCAModelGeometry.BREASTS);
        ModelPart breast = pivot.getChild(MCAModelGeometry.BREAST_HAIR_SURFACE);

        assertEquals(2, polygons(breast).size(), "Inner hair needs front and upper faces");
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

        ModelPart outerHair = pivot.getChild(MCAModelGeometry.BREASTPLATE);
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
    void hairUsesVanillaPlayerOuterLayerSpacing() throws Exception {
        ModelPart hairRoot = bakedHair(MCALayerDefinitions.VILLAGER_HAIR_DILATION);
        Bounds head = bounds(hairRoot.getChild("head"));
        Bounds hat = bounds(hairRoot.getChild("hat"));
        Bounds body = bounds(hairRoot.getChild("body"));
        Bounds jacket = bounds(hairRoot.getChild("jacket"));

        assertEquals(0.5F, hat.maxX() - head.maxX(), 0.00001F, "Vanilla hat spacing should be preserved");
        assertEquals(0.5F, head.minX() - hat.minX(), 0.00001F, "Vanilla hat spacing should be symmetric");
        assertEquals(0.25F, jacket.maxX() - body.maxX(), 0.00001F, "Vanilla jacket spacing should be preserved");
        assertEquals(0.25F, body.minX() - jacket.minX(), 0.00001F, "Vanilla jacket spacing should be symmetric");
    }

    @Test
    void hairAndClothingFollowTheExistingVanillaPlayerPose() {
        for (boolean slim : new boolean[]{false, true}) {
            PlayerModel<LivingEntity> parent = new PlayerModel<>(LayerDefinition.create(
                    PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            parent.head.setRotation(0.4F, 0.6F, 0.1F);
            parent.head.xScale = 1.3F;
            parent.hat.visible = false;
            parent.body.setRotation(0.15F, -0.3F, 0.2F);
            parent.rightArm.xRot = -1.2F;

            ModelPart hairRoot = bakedHair(MCALayerDefinitions.VILLAGER_HAIR_DILATION);
            HairOverlayModel<LivingEntity> hair = new HairOverlayModel<>(
                    hairRoot, MCALayerDefinitions.VILLAGER_CLOTHING_DILATION);
            hair.copyFrom(parent);
            assertEquals(parent.head.xRot, hairRoot.getChild("head").xRot);
            assertEquals(parent.head.yRot, hairRoot.getChild("head").yRot);
            assertEquals(parent.head.xScale, hairRoot.getChild("head").xScale);
            assertEquals(parent.body.zRot, hairRoot.getChild("body").zRot);
            assertFalse(hairRoot.getChild("hat").visible);

            VillagerOverlayModel<LivingEntity> clothing = new VillagerOverlayModel<>(
                    LayerDefinition.create(MCAModelGeometry.overlayData(CubeDeformation.NONE, slim), 64, 64).bakeRoot(),
                    slim);
            clothing.copyFrom(parent);
            assertEquals(parent.head.yRot, clothing.head.yRot);
            assertEquals(parent.head.xScale, clothing.head.xScale);
            assertEquals(parent.body.zRot, clothing.body.zRot);
            assertEquals(parent.rightArm.xRot, clothing.rightArm.xRot);
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
            ModelPart pivot = root.getChild("body")
                    .getChild(MCAModelGeometry.BREAST_TRANSFORM).getChild(MCAModelGeometry.BREASTS);
            ModelPart breast = pivot.getChild(MCAModelGeometry.BREAST_HAIR_SURFACE);
            ModelPart outerHair = pivot.getChild(MCAModelGeometry.BREASTPLATE);
            float clothingWidth = MCAModelGeometry.BREAST_WIDTH
                    + 2.0F * (pair[1] + MCAModelGeometry.BREAST_WEAR_DILATION);
            float baseGap = (MCAModelGeometry.BREAST_WIDTH * breast.xScale - clothingWidth) / 2.0F;
            float outerGap = (MCAModelGeometry.BREAST_WIDTH * outerHair.xScale - clothingWidth) / 2.0F;

            assertTrue(baseGap > 0.0F, "Hair must clear clothing");
            assertTrue(outerGap > baseGap, "Outer hair must clear inner hair");
            assertTrue(outerGap <= 0.02F, "Outer hair must not float above clothing");
            assertEquals(1.0F, pivot.xScale, "Morphology pivot must not inherit shell dilation");
            assertEquals(1.0F, pivot.yScale);
            for (ModelPart shell : List.of(breast, outerHair)) {
                assertEquals(MCAModelGeometry.BREAST_CENTER_X,
                        MCAModelGeometry.BREAST_CENTER_X * shell.xScale + shell.x, 0.00001F);
                assertEquals(MCAModelGeometry.BREAST_CENTER_Y,
                        MCAModelGeometry.BREAST_CENTER_Y * shell.yScale + shell.y, 0.00001F);
            }
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

    private static Bounds bounds(ModelPart part) throws ReflectiveOperationException {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (Object polygon : polygons(part)) {
            for (Object vertex : (Object[]) field(polygon, "vertices")) {
                Vector3f pos = (Vector3f) field(vertex, "pos");
                minX = Math.min(minX, pos.x());
                minY = Math.min(minY, pos.y());
                minZ = Math.min(minZ, pos.z());
                maxX = Math.max(maxX, pos.x());
                maxY = Math.max(maxY, pos.y());
                maxZ = Math.max(maxZ, pos.z());
            }
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }
}
