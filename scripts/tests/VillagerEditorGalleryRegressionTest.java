package net.conczin.mca.client.gui;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

public final class VillagerEditorGalleryRegressionTest {
    private VillagerEditorGalleryRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName("net.conczin.mca.client.gui.VillagerEditorGalleryPolicy");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Unified gallery policy is missing", e);
        }

        Method usesUnifiedGallery = policy.getDeclaredMethod("usesUnifiedGallery", String.class);
        usesUnifiedGallery.setAccessible(true);
        assertTrue((boolean)usesUnifiedGallery.invoke(null, "skin"), "skin must use the unified gallery");
        assertTrue((boolean)usesUnifiedGallery.invoke(null, "hair"), "hair must use the unified gallery");
        assertTrue((boolean)usesUnifiedGallery.invoke(null, "clothing"), "clothing must use the unified gallery");
        assertTrue((boolean)usesUnifiedGallery.invoke(null, "eyes_catalog"), "eyes must use the unified gallery");

        Method showPreviewControls = policy.getDeclaredMethod("showPreviewControls", String.class);
        showPreviewControls.setAccessible(true);
        assertTrue((boolean)showPreviewControls.invoke(null, "skin"), "skin gallery restores preview controls");
        assertTrue((boolean)showPreviewControls.invoke(null, "hair"), "hair gallery restores preview controls");
        assertTrue((boolean)showPreviewControls.invoke(null, "clothing"), "clothing gallery restores preview controls and lock access");
        assertTrue((boolean)showPreviewControls.invoke(null, "eyes_catalog"), "eyes gallery restores preview controls");
        assertTrue((boolean)showPreviewControls.invoke(null, "hair_back"), "layered-hair selection keeps its dedicated preview controls");

        Method filterOptions = policy.getDeclaredMethod("filterOptions", List.class, Predicate.class, Object.class);
        filterOptions.setAccessible(true);
        List<String> filters = List.of("all", "neutral", "female", "male");
        Predicate<String> hasItems = filter -> !filter.equals("neutral");
        List<?> options = (List<?>)filterOptions.invoke(null, filters, hasItems, "all");

        assertEquals(4, options.size(), "unavailable filters stay visible");
        Object neutral = options.get(1);
        Method value = neutral.getClass().getDeclaredMethod("value");
        Method available = neutral.getClass().getDeclaredMethod("available");
        value.setAccessible(true);
        available.setAccessible(true);
        assertEquals("neutral", value.invoke(neutral), "neutral stays in the stable filter row");
        assertFalse((boolean)available.invoke(neutral), "neutral is disabled when the current catalog has no neutral entries");

        Method layoutMethod = policy.getDeclaredMethod("layout", int.class, int.class);
        layoutMethod.setAccessible(true);
        Object layout = layoutMethod.invoke(null, 1024, 450);
        Class<?> layoutType = layout.getClass();
        int searchY = readInt(layoutType, layout, "searchY");
        int filterY = readInt(layoutType, layout, "filterY");
        int row0CenterY = readInt(layoutType, layout, "row0CenterY");
        int row1CenterY = readInt(layoutType, layout, "row1CenterY");
        int footerY = readInt(layoutType, layout, "footerY");
        int columns = readInt(layoutType, layout, "columns");
        int itemsPerPage = readInt(layoutType, layout, "itemsPerPage");
        int cardRadius = readInt(layoutType, layout, "cardRadius");
        int cardHalfHeight = readInt(layoutType, layout, "cardHalfHeight");
        int previewControlsY = readInt(layoutType, layout, "previewControlsY");

        assertEquals(6, columns, "standard editor screens use six gallery columns");
        assertEquals(12, itemsPerPage, "standard editor screens show two rows of six items");
        assertTrue(searchY <= 112, "search bar moves upward to free gallery space");
        assertTrue(footerY >= 330, "footer controls move downward to use the screen height");
        assertTrue(cardRadius >= 38, "gallery cards grow enough for full villager previews on a standard editor screen");
        assertTrue(cardHalfHeight > cardRadius, "villager cards provide extra vertical room for heads and feet");
        assertTrue(searchY + 18 < filterY, "search and gender filters do not overlap");
        assertTrue(filterY + 20 < row0CenterY - cardHalfHeight, "gender filters stay clear of the first row");
        assertTrue(row0CenterY + cardHalfHeight < row1CenterY - cardHalfHeight, "gallery rows do not overlap");
        assertTrue(row1CenterY + cardHalfHeight < previewControlsY, "preview controls sit below the gallery");
        assertTrue(previewControlsY + 20 < footerY, "preview controls stay separate from pagination");

        Object closeFitLayout = layoutMethod.invoke(null, 427, 240);
        int closeFitFooterY = readInt(layoutType, closeFitLayout, "footerY");
        int closeFitColumns = readInt(layoutType, closeFitLayout, "columns");
        int closeFitItemsPerPage = readInt(layoutType, closeFitLayout, "itemsPerPage");
        assertEquals(6, closeFitColumns, "six gallery columns are kept whenever they physically fit");
        assertEquals(12, closeFitItemsPerPage, "close-fit screens still show two rows of six items");
        assertTrue(closeFitFooterY + 20 <= 240, "six-column gallery footer stays on-screen at close-fit heights");

        Object compactLayout = layoutMethod.invoke(null, 300, 240);
        int compactFooterY = readInt(layoutType, compactLayout, "footerY");
        int compactColumns = readInt(layoutType, compactLayout, "columns");
        int compactItemsPerPage = readInt(layoutType, compactLayout, "itemsPerPage");
        assertEquals(4, compactColumns, "genuinely narrow screens fall back to four gallery columns");
        assertEquals(8, compactItemsPerPage, "narrow screens show two rows of four items");
        assertTrue(compactFooterY + 20 <= 240, "compact gallery footer stays on-screen");

        for (int[] screen : new int[][]{{427, 240}, {720, 340}, {1024, 450}, {300, 240}, {427, 180}}) {
            Object screenLayout = layoutMethod.invoke(null, screen[0], screen[1]);
            int halfHeight = readInt(layoutType, screenLayout, "cardHalfHeight");
            int secondRow = readInt(layoutType, screenLayout, "row1CenterY");
            int controlsY = readInt(layoutType, screenLayout, "previewControlsY");
            int screenFooter = readInt(layoutType, screenLayout, "footerY");
            assertTrue(readInt(layoutType, screenLayout, "searchY") >= 4, "search is visible at every supported height");
            assertTrue(secondRow + halfHeight < controlsY, "cards do not overlap bottom controls");
            assertTrue(controlsY + 20 < screenFooter, "bottom rows stay separate on short screens");
            assertTrue(screenFooter + 20 <= screen[1], "footer stays inside the screen");
        }

        // The old 52px scale painted a 2-block villager over 104px tall in an 80px card.
        // Test projected model bounds, including tall/wide genetics and maximum zoom.
        for (float zoom : new float[]{0.7F, 1.0F, 1.4F}) {
            for (float[] bounds : new float[][]{{2.3F, 1.35F}, {3.45F, 1.35F}, {2.3F, 2.7F}}) {
                float scale = VillagerEditorGalleryPolicy.fitPreviewScale(96, 144, bounds[0], bounds[1], zoom);
                assertTrue(scale * bounds[0] <= 132.01F, "projected villager height stays inside padded card at any zoom");
                assertTrue(scale * bounds[1] <= 84.01F, "wide and rotated villagers stay inside padded card");
            }
        }
        float regularScale = VillagerEditorGalleryPolicy.fitPreviewScale(96, 144, 2.3F, 1.35F, 1.0F);
        assertTrue(regularScale * 2.3F > 112, "default preview still fills most of the expanded card");
    }

    private static int readInt(Class<?> type, Object target, String methodName) throws Exception {
        Method method = type.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (int)method.invoke(target);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
