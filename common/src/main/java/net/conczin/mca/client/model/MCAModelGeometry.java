package net.conczin.mca.client.model;

import net.conczin.mca.Config;
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
    private static final float HAIR_BREAST_SEAM_OVERLAP = 0.125F;

    private MCAModelGeometry() {
    }

    public static MeshDefinition overlayData(CubeDeformation dilation, boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(dilation, slim);
        addBreastParts(mesh.getRoot().getChild(PartNames.BODY), dilation, true);
        return mesh;
    }

    public static MeshDefinition hairData(CubeDeformation dilation, boolean slim) {
        MeshDefinition mesh = overlayData(dilation, slim);
        PartDefinition root = mesh.getRoot();
        addBreastParts(
                root.getChild(PartNames.BODY),
                dilation.extend(0.0F, HAIR_BREAST_SEAM_OVERLAP, 0.0F),
                true
        );
        root.addOrReplaceChild(
                PartNames.HAT,
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4, -8, -4, 8, 8, 8, dilation.extend(0.3F)),
                PartPose.ZERO
        );
        clearGeometry(
                root,
                PartNames.LEFT_ARM,
                PartNames.RIGHT_ARM,
                PartNames.LEFT_LEG,
                PartNames.RIGHT_LEG,
                "left_sleeve",
                "right_sleeve",
                "left_pants",
                "right_pants"
        );
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
                    newBreasts(dilation.extend(0.1F), 16),
                    PartPose.ZERO
            );
        }
    }

    private static CubeListBuilder newBreasts(CubeDeformation dilation, int textureYOffset) {
        CubeListBuilder builder = CubeListBuilder.create();
        if (Config.getInstance().enableBoobs) {
            builder.texOffs(18, 21 + textureYOffset)
                    .addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
        }
        return builder;
    }

    private static void clearGeometry(PartDefinition root, String... parts) {
        for (String part : parts) {
            root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
        }
    }
}
