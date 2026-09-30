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
        assertFalse((boolean)showPreviewControls.invoke(null, "skin"), "unified galleries must not show overlapping preview controls");
        assertFalse((boolean)showPreviewControls.invoke(null, "hair"), "unified galleries must not show overlapping preview controls");
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
        int previewSize = readInt(layoutType, layout, "previewSize");
        int hoveredPreviewSize = readInt(layoutType, layout, "hoveredPreviewSize");

        assertEquals(6, columns, "standard editor screens use six gallery columns");
        assertEquals(12, itemsPerPage, "standard editor screens show two rows of six items");
        assertTrue(searchY <= 112, "search bar moves upward to free gallery space");
        assertTrue(footerY >= 330, "footer controls move downward to use the screen height");
        assertTrue(cardRadius >= 30, "gallery cards stay comfortably sized on a standard editor screen");
        assertTrue(previewSize <= cardRadius + 6, "avatars leave breathing room inside the cards");
        assertEquals(previewSize, hoveredPreviewSize, "hover highlighting does not resize avatars");
        assertTrue(searchY + 18 < filterY, "search and gender filters do not overlap");
        assertTrue(filterY + 20 < row0CenterY - cardRadius, "gender filters stay clear of the first row");
        assertTrue(row0CenterY + cardRadius < row1CenterY - cardRadius, "gallery rows do not overlap");
        assertTrue(row1CenterY + cardRadius < footerY, "second row stays clear of footer controls");

        Object compactLayout = layoutMethod.invoke(null, 427, 240);
        int compactFooterY = readInt(layoutType, compactLayout, "footerY");
        int compactColumns = readInt(layoutType, compactLayout, "columns");
        int compactItemsPerPage = readInt(layoutType, compactLayout, "itemsPerPage");
        assertEquals(4, compactColumns, "compact screens fall back to four gallery columns");
        assertEquals(8, compactItemsPerPage, "compact screens show two rows of four items");
        assertTrue(compactFooterY + 20 <= 240, "unified gallery footer stays on-screen at compact GUI heights");
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
