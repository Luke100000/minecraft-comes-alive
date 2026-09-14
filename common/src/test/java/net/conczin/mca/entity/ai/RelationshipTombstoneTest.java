package net.conczin.mca.entity.ai;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RelationshipTombstoneTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void shortTombstoneIdDefaultsToMcaNamespace() {
        assertEquals(
                Identifier.fromNamespaceAndPath("mca", "cross_headstone"),
                Relationship.configuredTombstoneId("cross_headstone")
        );
    }

    @Test
    void namespacedTombstoneIdIsPreserved() {
        assertEquals(
                Identifier.parse("example:custom_grave"),
                Relationship.configuredTombstoneId("example:custom_grave")
        );
    }

    @Test
    void nonTombstoneSelectionUsesProvidedFallback() {
        assertSame(Blocks.DIRT, Relationship.selectConfiguredTombstone(Blocks.STONE, Blocks.DIRT));
    }
}
