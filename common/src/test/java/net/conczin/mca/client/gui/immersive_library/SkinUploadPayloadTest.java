package net.conczin.mca.client.gui.immersive_library;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.client.gui.SkinLibraryScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(HeadlessTextureDevice.class)
class SkinUploadPayloadTest {
    @Test
    void uploadBodyAndTargetRemainFrozenAfterWorkspaceChanges() throws Exception {
        Workspace workspace = new Workspace(new NativeImage(64, 64, false));
        try {
            workspace.skinType = SkinLibraryScreen.SkinType.CLOTHING;
            workspace.title = "Original title";
            workspace.tags = "alpha, beta, gamma";
            workspace.currentImage.setPixel(0, 0, 0xFF112233);
            workspace.currentImage.setPixel(1, 0, 0x80445566);
            int[] expectedPixels = workspace.currentImage.getPixels();

            Method capture = SkinLibraryScreen.class.getDeclaredMethod("snapshotUpload", Workspace.class, String.class);
            capture.setAccessible(true);
            Object payload = capture.invoke(null, workspace, workspace.title);
            Method method = payload.getClass().getDeclaredMethod("method");
            Method endpoint = payload.getClass().getDeclaredMethod("endpoint");
            Method bodyAccessor = payload.getClass().getDeclaredMethod("body");
            method.setAccessible(true);
            endpoint.setAccessible(true);
            bodyAccessor.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) bodyAccessor.invoke(payload);
            byte[] originalPng = Base64.getDecoder().decode((String) body.get("data"));
            String originalMetadata = (String) body.get("meta");

            workspace.title = "Changed title";
            workspace.contentid = 99;
            workspace.tags = "changed";
            workspace.skinType = SkinLibraryScreen.SkinType.HAIR;
            workspace.currentImage.setPixel(0, 0, 0xFFFFFFFF);

            assertEquals(Api.HttpMethod.POST, method.invoke(payload));
            assertEquals("v1/content/mca", endpoint.invoke(payload));
            assertEquals("Original title", body.get("title"));
            assertEquals(originalMetadata, body.get("meta"));
            assertFalse(java.util.Arrays.equals(expectedPixels, workspace.currentImage.getPixels()));
            byte[] frozenPng = Base64.getDecoder().decode((String) body.get("data"));
            assertArrayEquals(originalPng, frozenPng);
            try (NativeImage decoded = NativeImage.read(frozenPng)) {
                assertEquals(64, decoded.getWidth());
                assertEquals(64, decoded.getHeight());
                assertArrayEquals(expectedPixels, decoded.getPixels());
            }
            assertEquals(java.util.Set.of("alpha", "beta", "gamma", "clothing"), java.util.Set.copyOf((java.util.List<?>) body.get("tags")));
            assertThrows(UnsupportedOperationException.class, () -> body.put("title", "other"));
        } finally {
            workspace.close();
        }
    }
}
