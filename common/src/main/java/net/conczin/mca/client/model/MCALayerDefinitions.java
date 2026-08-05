package net.conczin.mca.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class MCALayerDefinitions {
    private MCALayerDefinitions() {
    }

    public static void register(BiConsumer<ModelLayerLocation, Supplier<LayerDefinition>> register) {
        register.accept(MCAModelLayers.VILLAGER,
                () -> LayerDefinition.create(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE), 64, 64));
        register.accept(MCAModelLayers.VILLAGER_FACE,
                () -> LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F)), 64, 64));
        register.accept(MCAModelLayers.VILLAGER_CLOTHING,
                () -> LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625F)), 64, 64));
        register.accept(MCAModelLayers.VILLAGER_HAIR,
                () -> LayerDefinition.create(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125F)), 64, 64));

        register.accept(MCAModelLayers.ZOMBIE_VILLAGER,
                () -> LayerDefinition.create(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE), 64, 64));
        register.accept(MCAModelLayers.ZOMBIE_VILLAGER_FACE,
                () -> LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F)), 64, 64));
        register.accept(MCAModelLayers.ZOMBIE_VILLAGER_CLOTHING,
                () -> LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.075F)), 64, 64));
        register.accept(MCAModelLayers.ZOMBIE_VILLAGER_HAIR,
                () -> LayerDefinition.create(VillagerEntityModelMCA.hairData(new CubeDeformation(0.1F)), 64, 64));

        register.accept(MCAModelLayers.VILLAGER_INNER_ARMOR,
                () -> LayerDefinition.create(VillagerEntityBaseModelMCA.getModelData(new CubeDeformation(0.3F)), 64, 32));
        register.accept(MCAModelLayers.VILLAGER_OUTER_ARMOR,
                () -> LayerDefinition.create(VillagerEntityBaseModelMCA.getModelData(new CubeDeformation(0.55F)), 64, 32));

        register.accept(MCAModelLayers.PLAYER_ATTACHMENTS,
                () -> LayerDefinition.create(VillagerEntityModelMCA.attachmentData(CubeDeformation.NONE), 64, 64));
        register.accept(MCAModelLayers.PLAYER_INNER_ARMOR,
                () -> LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(0.5F)), 64, 32));
        register.accept(MCAModelLayers.PLAYER_OUTER_ARMOR,
                () -> LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(1.0F)), 64, 32));
    }
}
