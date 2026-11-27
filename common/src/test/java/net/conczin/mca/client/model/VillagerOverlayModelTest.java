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

/** Checks the shared baked overlay geometry without launching the game renderer. */
class VillagerOverlayModelTest {
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
        float dilation = MCALayerDefinitions.VILLAGER_HAIR_DILATION;
        ModelPart transform = bakedOverlay(dilation)
                .getChild("body").getChild(MCAModelGeometry.BREAST_TRANSFORM);
        ModelPart breast = transform.getChild(MCAModelGeometry.BREASTS);

        assertEquals(6, polygons(breast).size(), "Hair uses the same complete breast cube as every overlay");
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
                assertEquals(MCAModelGeometry.BREAST_Y - dilation, pos.y(), 0.00001F);
                assertTrue(u >= 21.0F && u <= 27.0F, "Upper face must sample torso hair columns");
                assertTrue(v >= 21.0F && v <= 24.0F, "Upper face must sample torso hair rows");
            }
        }
        assertEquals(1, topFaces, "Hair needs an actual baked upper breast face");

        ModelPart outerHair = transform.getChild(MCAModelGeometry.BREASTPLATE);
        assertEquals(6, polygons(outerHair).size(), "Outer hair uses the shared overlay wear cube");
        int projectedFaces = 0;
        for (Object polygon : polygons(outerHair)) {
            Vector3f normal = (Vector3f) field(polygon, "normal");
            if (normal.y() != -1.0F && normal.z() != -1.0F) {
                continue;
            }
            projectedFaces++;
            for (Object vertex : (Object[]) field(polygon, "vertices")) {
                float u = (float) field(vertex, "u") * 64.0F;
                float v = (float) field(vertex, "v") * 64.0F;
                assertTrue(u >= 21.0F && u <= 27.0F, "Outer hair must sample torso columns");
                assertTrue(v >= 37.0F && v <= 43.0F, "Outer hair must sample outer torso rows");
            }
        }
        assertEquals(2, projectedFaces, "Outer hair needs the front and upper torso surfaces");
    }

    @Test
    void projectedHairCoversBreastSidesUsingTorsoSideUvs() throws Exception {
        ModelPart transform = bakedOverlay(MCALayerDefinitions.VILLAGER_HAIR_DILATION)
                .getChild("body").getChild(MCAModelGeometry.BREAST_TRANSFORM);

        assertSideUvs(transform.getChild(MCAModelGeometry.BREASTS), 24.0F, 27.0F);
        assertSideUvs(transform.getChild(MCAModelGeometry.BREASTPLATE), 40.0F, 43.0F);
    }

    @Test
    void breastGeometryIsBakedIndependentlyOfRuntimeVisibilitySetting() throws Exception {
        boolean previous = Config.getInstance().enableBoobs;
        try {
            Config.getInstance().enableBoobs = false;

            ModelPart hairTransform = bakedOverlay(MCALayerDefinitions.VILLAGER_HAIR_DILATION)
                    .getChild("body").getChild(MCAModelGeometry.BREAST_TRANSFORM);
            assertEquals(6, polygons(hairTransform.getChild(MCAModelGeometry.BREASTS)).size());
            assertEquals(6, polygons(hairTransform.getChild(MCAModelGeometry.BREASTPLATE)).size());

            ModelPart overlayTransform = LayerDefinition.create(
                            MCAModelGeometry.overlayData(CubeDeformation.NONE, false), 64, 64)
                    .bakeRoot().getChild("body").getChild(MCAModelGeometry.BREAST_TRANSFORM);
            assertEquals(6, polygons(overlayTransform.getChild(MCAModelGeometry.BREASTS)).size());
            assertEquals(6, polygons(overlayTransform.getChild(MCAModelGeometry.BREASTPLATE)).size());
        } finally {
            Config.getInstance().enableBoobs = previous;
        }
    }

    @Test
    void hairUsesVanillaPlayerOuterLayerSpacing() throws Exception {
        ModelPart hairRoot = bakedOverlay(MCALayerDefinitions.VILLAGER_HAIR_DILATION);
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

            ModelPart hairRoot = bakedOverlay(MCALayerDefinitions.VILLAGER_HAIR_DILATION);
            VillagerOverlayModel<LivingEntity> hair = new VillagerOverlayModel<>(hairRoot, false);
            hair.copyFrom(parent);
            hair.showHairOnly();
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
    void hairUsesItsRegisteredDilationWithoutRuntimeProjectionOffsets() throws Exception {
        for (float dilation : new float[]{
                MCALayerDefinitions.VILLAGER_HAIR_DILATION,
                MCALayerDefinitions.ZOMBIE_VILLAGER_HAIR_DILATION
        }) {
            ModelPart root = bakedOverlay(dilation);
            new VillagerOverlayModel<LivingEntity>(root, false);
            ModelPart transform = root.getChild("body").getChild(MCAModelGeometry.BREAST_TRANSFORM);
            ModelPart breast = transform.getChild(MCAModelGeometry.BREASTS);
            ModelPart outerHair = transform.getChild(MCAModelGeometry.BREASTPLATE);

            assertEquals(MCAModelGeometry.BREAST_WIDTH + 2.0F * dilation,
                    bounds(breast).maxX() - bounds(breast).minX(), 0.00001F);
            assertEquals(MCAModelGeometry.BREAST_WIDTH + 2.0F * (dilation + MCAModelGeometry.BREAST_WEAR_DILATION),
                    bounds(outerHair).maxX() - bounds(outerHair).minX(), 0.00001F);
            assertEquals(1.0F, transform.xScale, "Morphology transform must not inherit shell dilation");
            assertEquals(1.0F, transform.yScale);
            for (ModelPart shell : List.of(breast, outerHair)) {
                assertEquals(1.0F, shell.xScale);
                assertEquals(1.0F, shell.yScale);
                assertEquals(1.0F, shell.zScale);
                assertEquals(0.0F, shell.x);
                assertEquals(0.0F, shell.y);
                assertEquals(0.0F, shell.z);
            }
        }
    }

    private static ModelPart bakedOverlay(float dilation) {
        return LayerDefinition.create(
                MCAModelGeometry.overlayData(new CubeDeformation(dilation), false), 64, 64
        ).bakeRoot();
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

    private static void assertSideUvs(ModelPart part, float minV, float maxV) throws ReflectiveOperationException {
        int sideFaces = 0;
        for (Object polygon : polygons(part)) {
            Vector3f normal = (Vector3f) field(polygon, "normal");
            if (Math.abs(normal.x()) != 1.0F) {
                continue;
            }
            sideFaces++;
            float minU = normal.x() < 0.0F ? 18.0F : 27.0F;
            float maxU = normal.x() < 0.0F ? 21.0F : 30.0F;
            for (Object vertex : (Object[]) field(polygon, "vertices")) {
                float u = (float) field(vertex, "u") * 64.0F;
                float v = (float) field(vertex, "v") * 64.0F;
                assertTrue(u >= minU && u <= maxU, "Side hair must sample the matching torso side columns");
                assertTrue(v >= minV && v <= maxV, "Side hair must sample the matching torso rows");
            }
        }
        assertEquals(2, sideFaces, "Hair needs both east and west breast side faces");
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }
}
