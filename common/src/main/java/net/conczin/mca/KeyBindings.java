package net.conczin.mca;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;
import java.util.List;

public class KeyBindings {
    public static final List<KeyMapping> list = new LinkedList<>();
    private static final KeyMapping.Category MCA_CATEGORY = KeyMapping.Category.register(MCA.locate("mca_tab"));

    public static final KeyMapping SKIN_LIBRARY = newKey("skin_library", GLFW.GLFW_KEY_U);

    private static KeyMapping newKey(String name, int code) {
        KeyMapping key = new KeyMapping(
                "key.mca." + name,
                InputConstants.Type.KEYSYM,
                code,
                MCA_CATEGORY
        );
        list.add(key);
        return key;
    }
}
