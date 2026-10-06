package net.conczin.mca.client.model;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerPlayerModelTest {
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

        VillagerPlayerModel.composeHeadScale(head, hat, 1.5F);

        assertEquals(1.8F, head.xScale, 0.00001F);
        assertEquals(1.2F, head.yScale, 0.00001F);
        assertEquals(1.65F, head.zScale, 0.00001F);
        assertEquals(1.35F, hat.xScale, 0.00001F);
        assertEquals(1.95F, hat.yScale, 0.00001F);
        assertEquals(1.575F, hat.zScale, 0.00001F);
    }

    private static ModelPart emptyPart() {
        return new ModelPart(List.of(), Map.of());
    }
}
