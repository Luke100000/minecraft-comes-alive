package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.client.render.GrimReaperRenderState;
import net.conczin.mca.entity.ReaperAttackState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.Map;

import static net.minecraft.client.model.geom.PartNames.*;

public class GrimReaperEntityModel extends HumanoidModel<GrimReaperRenderState> {
    private static final Map<ReaperAttackState, ModelTransformSet> POSES = ImmutableMap.of(
            ReaperAttackState.PRE, new ModelTransformSet.Builder()
                    .rotate(HEAD, -15.6F, 40.4F, 0)
                    .rotate(BODY, 0, -13, 0)
                    .rotate(LEFT_ARM, -130, -112, 7.8F)
                    .rotate(RIGHT_ARM, -36.5F, 122.6F, 0)
                    .rotate(LEFT_LEG, 18, -13, 0)
                    .rotate(RIGHT_LEG, 13, -13, 0)
                    .rotate("scythe_handle", 0, 0, 90)
                    .build(),
            ReaperAttackState.POST, new ModelTransformSet.Builder()
                    .rotate(HEAD, 44.3F, 41.7F, 0)
                    .rotate(BODY, 34, 34, 0)
                    .rotate(LEFT_ARM, -44, 62, 7.8F)
                    .with(RIGHT_ARM, -5, 1.7F, 3.3F, -36.5F, 122.6F, 0)
                    .with(LEFT_LEG, 5.4F, 9.8F, 4.6F, 28.7F, 39, -2.6F)
                    .with(RIGHT_LEG, 2, 10, 6.6F, 31.3F, 34, -5.2F)
                    .with("scythe_handle", -10, 10, 0, 0, -10, 90)
                    .build(),
            ReaperAttackState.BLOCK, new ModelTransformSet.Builder()
                    .rotate(HEAD, 7.8F, 0, 0)
                    .with(BODY, 0, 0, 1, -5.2F, 5.2F, 0)
                    .rotate(LEFT_ARM, -86F, 23.5F, 7.8F)
                    .rotate(RIGHT_ARM, -70, 0, 107)
                    .rotate(LEFT_LEG, -7.8F, 2.6F, 0)
                    .rotate(RIGHT_LEG, -7.8F, 5.2F, 0)
                    .rotate("scythe_handle", 120, 88, 0)
                    .build(),
            ReaperAttackState.REST, new ModelTransformSet.Builder()
                    .rotate(HEAD, 62.6F, 0, 1.8F)
                    .rotate(BODY, 0, 5.2F, 0)
                    .rotate(LEFT_ARM, 0, 0, -20, ModelTransformSet.Op.ADD)
                    .rotate(RIGHT_ARM, 0, 0, 20, ModelTransformSet.Op.ADD)
                    .rotate(LEFT_LEG, 2.6F, 2.6F, 0)
                    .rotate(RIGHT_LEG, 2.6F, 5.2F, 0)
                    .with("scythe_handle", 0, 10, 0, 90, -20, 90, ModelTransformSet.Op.KEEP, ModelTransformSet.Op.KEEP)
                    .build());

    private final ModelPart scythe;
    private final PartPose scytheTransform;
    public ReaperAttackState reaperState = ReaperAttackState.IDLE;

    public GrimReaperEntityModel(ModelPart tree) {
        super(tree);
        scythe = tree.getChild(LEFT_ARM).getChild("scythe_handle");
        scytheTransform = scythe.storePose();
    }

    public static MeshDefinition getModelData(CubeDeformation dilation) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0);
        PartDefinition data = modelData.getRoot();

        data.getChild(LEFT_ARM)
                .addOrReplaceChild("scythe_handle",
                        CubeListBuilder.create().texOffs(36, 32).addBox(0, -26, 0, 1, 31, 1, dilation)
                                .texOffs(0, 32).addBox(0.5F, -26, 0.5F, 16, 16, 0, dilation),
                        ModelTransformSet.Builder.createTransform(0, 10, 0, 90, -20, 90)
                );

        return modelData;
    }

    @Override
    public void setupAnim(GrimReaperRenderState state) {
        super.setupAnim(state);

        body.setPos(0, 0, 0);
        body.setRotation(0, 0, 0);

        leftLeg.setPos(1.9F, 12, 0);
        leftLeg.setRotation(0, 0, 0);
        rightLeg.setPos(-1.9F, 12, 0);
        rightLeg.setRotation(0, 0, 0);

        scythe.loadPose(scytheTransform);
        reaperState = state.attackState;
        ModelTransformSet set = POSES.get(reaperState);

        if (set != null) {
            set.get(HEAD).applyTo(head);
            set.get(BODY).applyTo(body);
            set.get(LEFT_ARM).applyTo(leftArm);
            set.get(RIGHT_ARM).applyTo(rightArm);
            set.get(LEFT_LEG).applyTo(leftLeg);
            set.get(RIGHT_LEG).applyTo(rightLeg);
            set.get("scythe_handle").applyTo(scythe);
        }
    }
}
