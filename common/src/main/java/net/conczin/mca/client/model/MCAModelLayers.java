package net.conczin.mca.client.model;

import net.conczin.mca.MCA;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class MCAModelLayers {
    public static final ModelLayerLocation VILLAGER = layer("villager", "main");
    public static final ModelLayerLocation VILLAGER_FACE = layer("villager", "face");
    public static final ModelLayerLocation VILLAGER_CLOTHING = layer("villager", "clothing");
    public static final ModelLayerLocation VILLAGER_HAIR = layer("villager", "hair");
    public static final ModelLayerLocation VILLAGER_INNER_ARMOR = layer("villager", "inner_armor");
    public static final ModelLayerLocation VILLAGER_OUTER_ARMOR = layer("villager", "outer_armor");

    public static final ModelLayerLocation ZOMBIE_VILLAGER = layer("zombie_villager", "main");
    public static final ModelLayerLocation ZOMBIE_VILLAGER_FACE = layer("zombie_villager", "face");
    public static final ModelLayerLocation ZOMBIE_VILLAGER_CLOTHING = layer("zombie_villager", "clothing");
    public static final ModelLayerLocation ZOMBIE_VILLAGER_HAIR = layer("zombie_villager", "hair");

    public static final ModelLayerLocation PLAYER_ATTACHMENTS = layer("player", "mca_attachments");
    public static final ModelLayerLocation PLAYER_INNER_ARMOR = layer("player", "mca_inner_armor");
    public static final ModelLayerLocation PLAYER_OUTER_ARMOR = layer("player", "mca_outer_armor");

    private MCAModelLayers() {
    }

    private static ModelLayerLocation layer(String model, String layer) {
        return new ModelLayerLocation(MCA.locate(model), layer);
    }
}
