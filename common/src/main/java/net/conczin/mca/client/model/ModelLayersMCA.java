package net.conczin.mca.client.model;

import net.conczin.mca.MCA;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import java.util.Set;
import java.util.function.Supplier;

public final class ModelLayersMCA {
    public static final ModelLayerLocation VILLAGER = create("villager", "main");
    public static final ModelLayerLocation ZOMBIE_VILLAGER = create("zombie_villager", "main");
    public static final ModelLayerLocation PLAYER = create("player", "main");
    public static final ModelLayerLocation PLAYER_SLIM = create("player_slim", "main");

    private static final Set<ModelLayerLocation> PLAYER_COMPAT_LAYERS = Set.of(
            VILLAGER,
            ZOMBIE_VILLAGER,
            PLAYER
    );

    private static final Set<ModelLayerLocation> SLIM_PLAYER_COMPAT_LAYERS = Set.of(
            PLAYER_SLIM
    );

    private ModelLayersMCA() {
    }

    public static boolean isPlayerCompatLayer(ModelLayerLocation layer) {
        return PLAYER_COMPAT_LAYERS.contains(layer);
    }

    public static boolean isSlimPlayerCompatLayer(ModelLayerLocation layer) {
        return SLIM_PLAYER_COMPAT_LAYERS.contains(layer);
    }

    public static void register(LayerRegistrar registrar) {
        registrar.register(VILLAGER, () -> LayerDefinition.create(VillagerEntityModelMCA.playerData(CubeDeformation.NONE), 64, 64));
        registrar.register(ZOMBIE_VILLAGER, () -> LayerDefinition.create(VillagerEntityModelMCA.playerData(CubeDeformation.NONE), 64, 64));
        registrar.register(PLAYER, () -> LayerDefinition.create(VillagerEntityModelMCA.playerData(CubeDeformation.NONE, false), 64, 64));
        registrar.register(PLAYER_SLIM, () -> LayerDefinition.create(VillagerEntityModelMCA.playerData(CubeDeformation.NONE, true), 64, 64));
    }

    private static ModelLayerLocation create(String path, String layer) {
        return new ModelLayerLocation(MCA.locate(path), layer);
    }

    @FunctionalInterface
    public interface LayerRegistrar {
        void register(ModelLayerLocation layer, Supplier<LayerDefinition> definition);
    }
}
