package net.conczin.mca.client.gui.immersive_library;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceLifecycleTest {
    @Test
    void abandoningEditorClosesUndoSnapshotsAndCurrentPixels() throws Exception {
        NativeImage pixels = new NativeImage(64, 64, false);
        Workspace workspace = new Workspace(pixels);
        workspace.saveSnapshot(true);
        NativeImage snapshot = workspace.history.getFirst();

        assertInstanceOf(AutoCloseable.class, workspace);
        AutoCloseable.class.cast(workspace).close();

        assertTrue(workspace.history.isEmpty());
        assertThrows(IllegalStateException.class, () -> snapshot.getPixelRGBA(0, 0));
        assertThrows(IllegalStateException.class, () -> pixels.getPixelRGBA(0, 0));
        AutoCloseable.class.cast(workspace).close();
    }

    @Test
    void snapshotHistoryNeverGrowsBeyondItsCapacity() throws Exception {
        Workspace workspace = new Workspace(new NativeImage(64, 64, false));
        try {
            for (int i = 0; i < 51; i++) {
                workspace.saveSnapshot(true);
            }
            assertEquals(50, workspace.history.size());
        } finally {
            if (AutoCloseable.class.isInstance(workspace)) {
                AutoCloseable.class.cast(workspace).close();
            } else {
                workspace.history.forEach(NativeImage::close);
                workspace.backendTexture.close();
            }
        }
    }

    @Test
    void transparentContrastDoesNotCreateUndoHistoryOrDirtyPixels() throws Exception {
        NativeImage transparentPixels = new NativeImage(64, 64, false);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                transparentPixels.setPixelRGBA(x, y, 0);
            }
        }
        Workspace workspace = new Workspace(transparentPixels);
        try {
            workspace.setDirty(false);
            assertDoesNotThrow(() -> workspace.addContrast(0.15f));
            assertDoesNotThrow(() -> workspace.addContrast(-0.15f));
            assertTrue(workspace.history.isEmpty());
            assertFalse(workspace.isDirty());
        } finally {
            if (AutoCloseable.class.isInstance(workspace)) {
                AutoCloseable.class.cast(workspace).close();
            } else {
                workspace.history.forEach(NativeImage::close);
                workspace.backendTexture.close();
            }
        }
    }
}
