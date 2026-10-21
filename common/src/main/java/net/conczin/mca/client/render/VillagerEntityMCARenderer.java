package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.client.render.layer.VillagerFishingLineLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.BedDebugLog;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    private final Map<VillagerEntityMCA, Long> bedDebugLogTimes = new WeakHashMap<>();
    private VillagerEntityMCA bedDebugEntity;
    private Matrix4f bedDebugBaseInverse;
    private Vec3 bedDebugRenderOrigin;

    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createAnimationModel(ctx).hideWears());

        // The parent drives external animation; visible layers keep MCA geometry and textures.
        layers.add(0, new SkinLayer<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>>(
                this, createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears()) {
            @Override
            public void render(PoseStack transform, MultiBufferSource buffers, int light, VillagerEntityMCA villager,
                               float limbAngle, float limbDistance, float partialTick, float animationProgress, float headYaw, float headPitch) {
                super.render(transform, buffers, light, villager, limbAngle, limbDistance, partialTick, animationProgress, headYaw, headPitch);
                logSleepingModel(villager, transform, model);
            }
        });
        addLayer(new FaceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625F))), "normal"));
        addLayer(new HairLayer<>(this, createVisibleModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125F)))));
        addLayer(new VillagerFishingLineLayer(this));
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createAnimationModel(EntityRendererProvider.Context ctx) {
        return new VillagerEntityModelMCA<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createVisibleModel(MeshDefinition data) {
        return new VillagerEntityModelMCA<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Override
    public void render(VillagerEntityMCA villager, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        long time = villager.level().getGameTime();
        if (BedDebugLog.ENABLED && villager.isSleeping()
                && time >= bedDebugLogTimes.getOrDefault(villager, Long.MIN_VALUE)) {
            bedDebugLogTimes.put(villager, time + 200);
            bedDebugEntity = villager;
            // Cancel the caller's camera/root matrix, so sampled model points are relative to this entity.
            bedDebugBaseInverse = new Matrix4f(poseStack.last().pose()).invert();
            bedDebugRenderOrigin = new Vec3(Mth.lerp(partialTick, villager.xOld, villager.getX()),
                    Mth.lerp(partialTick, villager.yOld, villager.getY()),
                    Mth.lerp(partialTick, villager.zOld, villager.getZ())).add(getRenderOffset(villager, partialTick));
        }
        try {
            super.render(villager, yaw, partialTick, poseStack, buffers, light);
        } finally {
            bedDebugEntity = null;
            bedDebugBaseInverse = null;
            bedDebugRenderOrigin = null;
        }
    }

    /** Called by the visible skin layer after animation and model-property copying. */
    private void logSleepingModel(VillagerEntityMCA villager, PoseStack poseStack, VillagerEntityModelMCA<?> visibleModel) {
        if (bedDebugEntity != villager) {
            return;
        }
        BlockPos bed = villager.getSleepingPos().orElse(null);
        Direction direction = villager.getBedOrientation();
        if (bed == null || direction == null) {
            return;
        }

        Vec3 root = bedDebugPoint(poseStack, 0, 0, 0);
        Vec3 torsoPivot = bedDebugPoint(poseStack, visibleModel.body.x / 16, visibleModel.body.y / 16, visibleModel.body.z / 16);
        poseStack.pushPose();
        poseStack.scale(visibleModel.getDimensions().getHead(), visibleModel.getDimensions().getHead(), visibleModel.getDimensions().getHead());
        visibleModel.head.translateAndRotate(poseStack);
        Vec3 headCenter = bedDebugPoint(poseStack, 0, -0.25F, 0);
        poseStack.popPose();

        Vec3 bedAnchor = Vec3.atBottomCenterOf(bed).add(0, 0.6875, 0);
        Vec3 headDelta = headCenter.subtract(bedAnchor);
        Direction across = direction.getClockWise();
        float correction = VillagerLike.PLAYER_MODEL_EYE_HEIGHT
                * (villager.getVisualVerticalScaleFactor() - villager.getPhysicalVerticalScaleFactor());
        MCA.LOGGER.info("[MCA-BED][client-render] name={} uuid={} bed={} facing={} entityPos={} interpolatedRenderOrigin={} rootWorld={} torsoPivotWorld={} headCubeCenterWorld={} headFromBed={} headAlongBed={} headAcrossBed={} physicalEyeWorld={} vanillaShiftWorld={} correctionBeforeEntityScale={} correctionWorld={} entityScale={} visualHeightScale={} physicalHeightScale={} widthScale={} headSize={} bodyRot={} headRot={} headPivot={}",
                villager.getName().getString(), villager.getUUID(), bed, direction, villager.position(), bedDebugRenderOrigin,
                root, torsoPivot, headCenter, headDelta,
                headDelta.x * direction.getStepX() + headDelta.z * direction.getStepZ(),
                headDelta.x * across.getStepX() + headDelta.z * across.getStepZ(),
                villager.getEyeHeight(Pose.STANDING), villager.getEyeHeight(Pose.STANDING) - 0.1F,
                correction, correction * villager.getScale(), villager.getScale(), villager.getVisualVerticalScaleFactor(),
                villager.getPhysicalVerticalScaleFactor(), villager.getVisualHorizontalScaleFactor(), visibleModel.getDimensions().getHead(),
                new Vector3f(visibleModel.body.xRot, visibleModel.body.yRot, visibleModel.body.zRot),
                new Vector3f(visibleModel.head.xRot, visibleModel.head.yRot, visibleModel.head.zRot),
                new Vector3f(visibleModel.head.x, visibleModel.head.y, visibleModel.head.z));
        bedDebugEntity = null;
    }

    private Vec3 bedDebugPoint(PoseStack poseStack, float x, float y, float z) {
        Vector3f point = new Matrix4f(bedDebugBaseInverse).mul(poseStack.last().pose()).transformPosition(new Vector3f(x, y, z));
        return bedDebugRenderOrigin.add(point.x, point.y, point.z);
    }

    @Override
    protected void setupRotations(VillagerEntityMCA villager, PoseStack poseStack, float bob, float bodyRot, float partialTick, float scale) {
        if (villager.hasPose(Pose.SLEEPING)) {
            Direction direction = villager.getBedOrientation();
            if (direction != null) {
                // Vanilla already offset by the physical eye height; add the uncapped visual remainder.
                float correction = VillagerLike.PLAYER_MODEL_EYE_HEIGHT
                        * (villager.getVisualVerticalScaleFactor() - villager.getPhysicalVerticalScaleFactor());
                poseStack.translate(
                        -direction.getStepX() * correction,
                        0.0,
                        -direction.getStepZ() * correction
                );
            }
        }

        super.setupRotations(villager, poseStack, bob, bodyRot, partialTick, scale);
    }
}
