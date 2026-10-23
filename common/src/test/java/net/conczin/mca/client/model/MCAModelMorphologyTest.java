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

    @Test
    void headMorphologyComposesWithExistingModelScale() {
        ModelPart head = emptyPart();
        ModelPart hat = emptyPart();
        head.xScale = 1.2F;
        head.yScale = 0.8F;
        head.zScale = 1.1F;
        hat.xScale = 0.9F;
        hat.yScale = 1.3F;
        hat.zScale = 1.05F;

        MCAModelMorphology.applyHeadScale(head, hat, 1.5F);

        assertEquals(1.8F, head.xScale, EPSILON);
        assertEquals(1.2F, head.yScale, EPSILON);
        assertEquals(1.65F, head.zScale, EPSILON);
        assertEquals(1.35F, hat.xScale, EPSILON);
        assertEquals(1.95F, hat.yScale, EPSILON);
        assertEquals(1.575F, hat.zScale, EPSILON);
    }

    private static ModelPart emptyPart() {
        return new ModelPart(List.of(), Map.of());
    }
}
