package net.conczin.mca.entity.ai;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathingBlockInteractionReflectionTest {
    private static final Identifier DRAMATIC_DOOR = Identifier.fromNamespaceAndPath("dramaticdoors", "oak_tall_door");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsExactDramaticDoorContract() {
        assertTrue(PathingBlockInteraction.hasDramaticDoorSetOpen(DRAMATIC_DOOR, VanillaLikeDoor.class));
    }

    @Test
    void rejectsOtherReflectiveShapes() {
        assertFalse(PathingBlockInteraction.hasDramaticDoorSetOpen(DRAMATIC_DOOR, ToggleDoor.class));
        assertFalse(PathingBlockInteraction.hasDramaticDoorSetOpen(DRAMATIC_DOOR, ReorderedDoor.class));
        assertFalse(PathingBlockInteraction.hasDramaticDoorSetOpen(DRAMATIC_DOOR, MissingWorldDoor.class));
        assertFalse(PathingBlockInteraction.hasDramaticDoorSetOpen(DRAMATIC_DOOR, NonVoidDoor.class));
    }

    @Test
    void ignoresOtherModWithMatchingDoorMethods() {
        assertFalse(PathingBlockInteraction.hasDramaticDoorSetOpen(
                Identifier.fromNamespaceAndPath("othermod", "similar_door"), VanillaLikeDoor.class));
    }

    @Test
    void readsHandOpenabilityFromDramaticDoorBlockSetType() {
        assertTrue(PathingBlockInteraction.dramaticDoorOpensByHand(Blocks.OAK_DOOR));
        assertFalse(PathingBlockInteraction.dramaticDoorOpensByHand(Blocks.IRON_DOOR));
    }

    public static class VanillaLikeDoor {
        public void setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        }
    }

    public static class ToggleDoor {
        public void toggleDoor(Level level, BlockPos pos, boolean open) {
        }
    }

    public static class ReorderedDoor {
        public void setOpen(BlockPos pos, boolean open, BlockState state, Level level) {
        }
    }

    public static class MissingWorldDoor {
        public void setOpen(BlockPos pos, boolean open) {
        }
    }

    public static class NonVoidDoor {
        public Object setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
            return new Object();
        }
    }
}
