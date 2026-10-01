package net.conczin.mca.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class MCALayerDefinitions {
    public static final float VILLAGER_CLOTHING_DILATION = 0.0625F;
    public static final float ZOMBIE_VILLAGER_CLOTHING_DILATION = 0.075F;
    public static final float VILLAGER_HAIR_DILATION = 0.125F;
    public static final float ZOMBIE_VILLAGER_HAIR_DILATION = 0.1F;

    private MCALayerDefinitions() {
    }

    public static void register(BiConsumer<ModelLayerLocation, Supplier<LayerDefinition>> register) {
        register.accept(MCAModelLayers.VILLAGER_FACE,
                () -> LayerDefinition.create(MCAModelGeometry.overlayData(new CubeDeformation(0.01F), false), 64, 64));
        register.accept(MCAModelLayers.VILLAGER_FACE_SLIM,
                () -> LayerDefinition.create(MCAModelGeometry.overlayData(new CubeDeformation(0.01F), true), 64, 64));
        register.accept(MCAModelLayers.VILLAGER_CLOTHING,
                () -> LayerDefinition.create(MCAModelGeometry.overlayData(new CubeDeformation(VILLAGER_CLOTHING_DILATION), false), 64, 64));
        register.accept(MCAModelLayers.VILLAGER_CLOTHING_SLIM,
                () -> LayerDefinition.create(MCAModelGeometry.overlayData(new CubeDeformation(VILLAGER_CLOTHING_DILATION), true), 64, 64));
        register.accept(MCAModelLayers.VILLAGER_HAIR,
                () -> LayerDefinition.create(MCAModelGeometry.hairData(VILLAGER_HAIR_DILATION), 64, 64));

        register.accept(MCAModelLayers.ZOMBIE_VILLAGER_FACE,
                () -> LayerDefinition.create(MCAModelGeometry.overlayData(new CubeDeformation(0.01F), false), 64, 64));
        register.accept(MCAModelLayers.ZOMBIE_VILLAGER_CLOTHING,
                () -> LayerDefinition.create(MCAModelGeometry.overlayData(new CubeDeformation(ZOMBIE_VILLAGER_CLOTHING_DILATION), false), 64, 64));
        register.accept(MCAModelLayers.ZOMBIE_VILLAGER_HAIR,
                () -> LayerDefinition.create(MCAModelGeometry.hairData(ZOMBIE_VILLAGER_HAIR_DILATION), 64, 64));

        register.accept(MCAModelLayers.VILLAGER_INNER_ARMOR,
                () -> LayerDefinition.create(MCAModelGeometry.armorData(new CubeDeformation(0.3F)), 64, 32));
        register.accept(MCAModelLayers.VILLAGER_OUTER_ARMOR,
                () -> LayerDefinition.create(MCAModelGeometry.armorData(new CubeDeformation(0.55F)), 64, 32));

        register.accept(MCAModelLayers.PLAYER_ATTACHMENTS,
                () -> LayerDefinition.create(MCAModelGeometry.attachmentData(CubeDeformation.NONE), 64, 64));
        register.accept(MCAModelLayers.PLAYER_INNER_ARMOR,
                () -> LayerDefinition.create(MCAModelGeometry.armorData(new CubeDeformation(0.5F)), 64, 32));
        register.accept(MCAModelLayers.PLAYER_OUTER_ARMOR,
                () -> LayerDefinition.create(MCAModelGeometry.armorData(new CubeDeformation(1.0F)), 64, 32));
    }
}
