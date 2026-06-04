package net.conczin.mca.client.render;

import net.conczin.mca.MCA;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;

import java.lang.reflect.Method;

public final class SkinLayers3dCompat {
    private static final boolean ENABLED = MCA.platformHelper.isModLoaded("skinlayers3d");
    private static final Method SET_IGNORED_METHOD = findSetIgnoredMethod();
    private static final Class<?> MESH_CLASS = findClass("dev.tr7zw.skinlayers.api.Mesh");
    private static final Class<?> OFFSET_PROVIDER_CLASS = findClass("dev.tr7zw.skinlayers.api.OffsetProvider");
    private static final Method SET_INJECTED_MESH_METHOD = findSetInjectedMeshMethod();

    private SkinLayers3dCompat() {
    }

    private static Method findSetIgnoredMethod() {
        if (!ENABLED) {
            return null;
        }

        try {
            return Class.forName("dev.tr7zw.skinlayers.accessor.PlayerEntityModelAccessor")
                    .getMethod("setIgnored", boolean.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
        private static Method findSetInjectedMeshMethod() {
            if (!ENABLED || MESH_CLASS == null || OFFSET_PROVIDER_CLASS == null) {
                return null;
            }

            try {
                return ModelPart.class.getMethod("setInjectedMesh", MESH_CLASS, OFFSET_PROVIDER_CLASS);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static Class<?> findClass(String name) {
            if (!ENABLED) {
                return null;
            }

            try {
                return Class.forName(name);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

    public static void setIgnored(PlayerModel model, boolean ignored) {
        if (!ENABLED || model == null || SET_IGNORED_METHOD == null) {
            return;
        }

        try {
            SET_IGNORED_METHOD.invoke(model, ignored);
        } catch (ReflectiveOperationException ignoredException) {
        }
    }

    public static void clearInjectedMeshes(PlayerModel model) {
        if (!ENABLED || model == null || SET_INJECTED_MESH_METHOD == null) {
            return;
        }

        clearInjectedMesh(model.head);
        clearInjectedMesh(model.hat);
        clearInjectedMesh(model.body);
        clearInjectedMesh(model.rightArm);
        clearInjectedMesh(model.leftArm);
        clearInjectedMesh(model.rightLeg);
        clearInjectedMesh(model.leftLeg);
        clearInjectedMesh(model.jacket);
        clearInjectedMesh(model.leftSleeve);
        clearInjectedMesh(model.rightSleeve);
        clearInjectedMesh(model.leftPants);
        clearInjectedMesh(model.rightPants);
    }

    private static void clearInjectedMesh(ModelPart part) {
        if (part == null) {
            return;
        }

        try {
            SET_INJECTED_MESH_METHOD.invoke(part, null, null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}