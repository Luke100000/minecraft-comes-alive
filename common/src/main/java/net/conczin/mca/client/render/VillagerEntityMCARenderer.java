package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.model.VillagerPlayerModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.MorphologyLayer;
import net.conczin.mca.client.render.layer.VillagerFishingLineLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.BedDebugLog;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
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
        super(ctx, new VillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false),
                new VillagerPlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));

        layers.add(0, new MorphologyLayer<>(this, ctx.bakeLayer(MCAModelLayers.PLAYER_ATTACHMENTS)));
        addLayer(new FaceLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_FACE).hideWears()));
        addLayer(new ClothingLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_CLOTHING), "normal"));
        addLayer(new ClothingLayer<>(this, new VillagerOverlayModel<>(
                ctx.bakeLayer(MCAModelLayers.VILLAGER_CLOTHING_SLIM), true), "normal", true));
        addLayer(new HairLayer<>(this, createOverlay(ctx, MCAModelLayers.VILLAGER_HAIR)));
        addLayer(new VillagerFishingLineLayer(this));
        addLayer(new RenderLayer<VillagerEntityMCA, PlayerModel<VillagerEntityMCA>>(this) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, VillagerEntityMCA villager,
                               float limbAngle, float limbDistance, float partialTick, float animationProgress,
                               float headYaw, float headPitch) {
                logSleepingModel(villager, poseStack, getParentModel());
            }
        });
    }

    @Override
    public void render(VillagerEntityMCA villager, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        long time = villager.level().getGameTime();
        if (BedDebugLog.ENABLED && villager.isSleeping()
                && time >= bedDebugLogTimes.getOrDefault(villager, Long.MIN_VALUE)) {
            bedDebugLogTimes.put(villager, time + 200);
            bedDebugEntity = villager;
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

    private void logSleepingModel(VillagerEntityMCA villager, PoseStack poseStack, PlayerModel<?> visibleModel) {
        if (bedDebugEntity != villager) {
            return;
        }
        BlockPos bed = villager.getSleepingPos().orElse(null);
        Direction direction = villager.getBedOrientation();
        if (bed == null || direction == null) {
            return;
        }

        Vec3 root = bedDebugPoint(poseStack, 0, 0, 0);
        Vec3 torsoPivot = bedDebugPoint(poseStack,
                visibleModel.body.x / 16, visibleModel.body.y / 16, visibleModel.body.z / 16);
        poseStack.pushPose();
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
                villager.getPhysicalVerticalScaleFactor(), villager.getVisualHorizontalScaleFactor(),
                villager.getVillagerDimensions().getHead(),
                new Vector3f(visibleModel.body.xRot, visibleModel.body.yRot, visibleModel.body.zRot),
                new Vector3f(visibleModel.head.xRot, visibleModel.head.yRot, visibleModel.head.zRot),
                new Vector3f(visibleModel.head.x, visibleModel.head.y, visibleModel.head.z));
        bedDebugEntity = null;
    }

    private Vec3 bedDebugPoint(PoseStack poseStack, float x, float y, float z) {
        Vector3f point = new Matrix4f(bedDebugBaseInverse)
                .mul(poseStack.last().pose())
                .transformPosition(new Vector3f(x, y, z));
        return bedDebugRenderOrigin.add(point.x, point.y, point.z);
    }

    @Override
    protected void setupRotations(VillagerEntityMCA villager, PoseStack poseStack, float bob, float bodyRot,
                                  float partialTick, float scale) {
        if (villager.hasPose(Pose.SLEEPING)) {
            Direction direction = villager.getBedOrientation();
            if (direction != null) {
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
