package net.conczin.mca.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathingBlockInteractionReflectionTest {
    private static final ResourceLocation DRAMATIC_DOOR = ResourceLocation.fromNamespaceAndPath("dramaticdoors", "oak_tall_door");

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
                ResourceLocation.fromNamespaceAndPath("othermod", "similar_door"), VanillaLikeDoor.class));
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
