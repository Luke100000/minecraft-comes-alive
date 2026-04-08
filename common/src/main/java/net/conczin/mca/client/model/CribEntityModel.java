package net.conczin.mca.client.model;

import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.client.render.CribEntityRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class CribEntityModel extends EntityModel<CribEntityRenderState> {
    private final ModelPart CRIB;

    public CribEntityModel(ModelPart root) {
        super(root);
        this.CRIB = root.getChild("Crib");
    }


    public static MeshDefinition getModelData(CubeDeformation dilation) {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition data = modelData.getRoot();

        PartDefinition crib = data.addOrReplaceChild("Crib", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, -5.0F, -13.0F, 19.0F, 2.0F, 25.0F, dilation), PartPose.offset(-0.5F, 6.0F, 0.5F));

        crib.addOrReplaceChild("Bars", CubeListBuilder.create()
                .texOffs(0, 4).addBox(9.0F, -16.0F, -11.5F, 0.0F, 11.0F, 23.0F, dilation)
                .texOffs(46, 49).addBox(-8.0F, -16.0F, 11.5F, 17.0F, 11.0F, 0.0F, dilation)
                .texOffs(0, 4).addBox(-8.0F, -16.0F, -11.5F, 0.0F, 11.0F, 23.0F, dilation)
                .texOffs(46, 49).addBox(-8.0F, -16.0F, -11.5F, 17.0F, 11.0F, 0.0F, dilation), PartPose.offset(0.0F, 0.0F, -0.5F));

        crib.addOrReplaceChild("Frame", CubeListBuilder.create()
                .texOffs(25, 27).addBox(8.0F, -17.0F, -11.0F, 2.0F, 1.0F, 21.0F, dilation)
                .texOffs(50, 30).addBox(-7.0F, -17.0F, 10.0F, 15.0F, 1.0F, 2.0F, dilation)
                .texOffs(50, 27).addBox(-7.0F, -17.0F, -13.0F, 15.0F, 1.0F, 2.0F, dilation)
                .texOffs(25, 27).mirror().addBox(-9.0F, -17.0F, -11.0F, 2.0F, 1.0F, 21.0F, dilation).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

        crib.addOrReplaceChild("Legs", CubeListBuilder.create()
                .texOffs(0, 0).addBox(8.0F, -17.0F, -13.0F, 2.0F, 12.0F, 2.0F, dilation)
                .texOffs(0, 0).addBox(8.0F, -17.0F, 10.0F, 2.0F, 12.0F, 2.0F, dilation)
                .texOffs(9, 0).addBox(8.0F, -3.0F, 10.0F, 2.0F, 3.0F, 2.0F, dilation)
                .texOffs(9, 0).mirror().addBox(-9.0F, -3.0F, 10.0F, 2.0F, 3.0F, 2.0F, dilation).mirror(false)
                .texOffs(9, 0).addBox(-9.0F, -3.0F, -13.0F, 2.0F, 3.0F, 2.0F, dilation)
                .texOffs(9, 0).mirror().addBox(8.0F, -3.0F, -13.0F, 2.0F, 3.0F, 2.0F, dilation).mirror(false)
                .texOffs(0, 0).mirror().addBox(-9.0F, -17.0F, 10.0F, 2.0F, 12.0F, 2.0F, dilation).mirror(false)
                .texOffs(0, 0).mirror().addBox(-9.0F, -17.0F, -13.0F, 2.0F, 12.0F, 2.0F, dilation).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

        return modelData;
    }

    @Override
    public void setupAnim(CribEntityRenderState state) {

    }
}
