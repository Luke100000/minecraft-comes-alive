package net.conczin.mca.neoforge;

import java.lang.reflect.Method;
import net.minecraft.client.renderer.SubmitNodeCollector;

public class SubmitTest {
    public static void test() {
        for (Method method : SubmitNodeCollector.class.getDeclaredMethods()) {
            if (method.getName().equals("submitModel")) {
                System.out.println("MCA_DEBUG METHOD: " + method);
            }
        }
    }
}
