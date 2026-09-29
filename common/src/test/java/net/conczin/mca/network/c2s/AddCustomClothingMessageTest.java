package net.conczin.mca.network.c2s;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddCustomClothingMessageTest {
    @Test
    void globalSkinIdsMustMatchClientIntegerContentIds() {
        assertTrue(AddCustomClothingMessage.isValidLibraryId("immersive_library:0"));
        assertTrue(AddCustomClothingMessage.isValidLibraryId("immersive_library:2147483647"));

        assertFalse(AddCustomClothingMessage.isValidLibraryId("immersive_library:2147483648"));
        assertFalse(AddCustomClothingMessage.isValidLibraryId("immersive_library:99999999999999999999"));
        assertFalse(AddCustomClothingMessage.isValidLibraryId("immersive_library:00012"));
        assertFalse(AddCustomClothingMessage.isValidLibraryId("immersive_library:-1"));
        assertFalse(AddCustomClothingMessage.isValidLibraryId("immersive_library:invalid"));
        assertFalse(AddCustomClothingMessage.isValidLibraryId("other_library:12"));
    }
}
