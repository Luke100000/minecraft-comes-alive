package net.conczin.mca.entity.ai.chatAI.inworldAIModules;

import net.conczin.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationshipModuleTest {
    private static final Interaction.RelationshipUpdate UPDATE =
            new Interaction.RelationshipUpdate(10, 10, 10, 10, 10);

    @Test
    void eligibleRelationshipIncludesRomanticFields() {
        assertEquals(5, RelationshipModule.calculateHeartDelta(UPDATE, false));
    }

    @Test
    void childOrRelativeRelationshipSuppressesRomanticFields() {
        assertEquals(3, RelationshipModule.calculateHeartDelta(UPDATE, true));
    }
}
