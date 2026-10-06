package net.conczin.mca.client.model;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCAModelMorphologyTest {
    private static final float EPSILON = 0.00001F;

    @Test
    void transformOwnsScaleRotationAndScaleCompensatedPivot() {
        ModelPart transform = emptyPart();

        MCAModelMorphology.applyBreastDimensions(transform, 1.0F, 1.0F, true);

        assertTrue(transform.visible);
        assertEquals(1.25F, transform.xScale, EPSILON);
        assertEquals(1.5F, transform.yScale, EPSILON);
        assertEquals(1.5F, transform.zScale, EPSILON);
        assertEquals(MCAModelMorphology.BREAST_ROTATION_X, transform.xRot, EPSILON);
        assertEquals(0.25F * transform.xScale, transform.x, EPSILON);
        assertEquals(2.5F * transform.yScale, transform.y, EPSILON);
        assertEquals(-1.25F * transform.zScale, transform.z, EPSILON);
    }

    @Test
    void hiddenMorphologyStillKeepsDeterministicPose() {
        ModelPart transform = emptyPart();

        MCAModelMorphology.applyBreastDimensions(transform, 1.0F, 1.0F, false);

        assertFalse(transform.visible);
        assertEquals(MCAModelMorphology.BREAST_ROTATION_X, transform.xRot, EPSILON);
        assertEquals(transform.yScale, transform.zScale, EPSILON);
    }

    private static ModelPart emptyPart() {
        return new ModelPart(List.of(), Map.of());
    }
}
