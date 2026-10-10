package net.conczin.mca.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class MCAModelGeometry {
    public static final String BREAST_TRANSFORM = "breast_transform";
    public static final String BREASTS = "breasts";
    public static final String BREASTPLATE = "breastplate";
    static final float BREAST_WEAR_DILATION = 0.1F;
    static final float BREAST_X = -3.25F;
    static final float BREAST_Y = -1.25F;
    static final float BREAST_Z = -1.5F;
    static final float BREAST_WIDTH = 6.0F;
    static final float BREAST_HEIGHT = 3.0F;
    static final float BREAST_DEPTH = 3.0F;
    private static final int BREAST_TEXTURE_X = 18;
    private static final int BREAST_TEXTURE_Y = 21;

    private MCAModelGeometry() {
    }

    public static MeshDefinition overlayData(CubeDeformation dilation, boolean slim) {
        // Reuse the vanilla PlayerModel tree so overlays can copy the authoritative
        // parent pose directly; each render layer chooses which parts stay visible.
        MeshDefinition mesh = PlayerModel.createMesh(dilation, slim);
        addBreastParts(mesh.getRoot().getChild(PartNames.BODY), dilation, true);
        return mesh;
    }

    public static MeshDefinition attachmentData(CubeDeformation dilation) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild(
                PartNames.BODY,
                CubeListBuilder.create(),
                PartPose.ZERO
        );
        addBreastParts(body, dilation, true);
        return mesh;
    }

    public static MeshDefinition armorData(CubeDeformation dilation) {
        MeshDefinition mesh = HumanoidModel.createMesh(dilation, 0.0F);
        addBreastParts(mesh.getRoot().getChild(PartNames.BODY), dilation, false);
        return mesh;
    }

    private static void addBreastParts(
            PartDefinition body,
            CubeDeformation dilation,
            boolean withBreastplate
    ) {
        PartDefinition transform = body.addOrReplaceChild(
                BREAST_TRANSFORM,
                CubeListBuilder.create(),
                PartPose.ZERO
        );
        transform.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        if (withBreastplate) {
            transform.addOrReplaceChild(
                    BREASTPLATE,
                    newBreasts(dilation.extend(BREAST_WEAR_DILATION), 16),
                    PartPose.ZERO
            );
        }
    }

    private static CubeListBuilder newBreasts(CubeDeformation dilation, int textureYOffset) {
        return CubeListBuilder.create()
                .texOffs(BREAST_TEXTURE_X, BREAST_TEXTURE_Y + textureYOffset)
                .addBox(BREAST_X, BREAST_Y, BREAST_Z, BREAST_WIDTH, BREAST_HEIGHT, BREAST_DEPTH, dilation);
    }
}
