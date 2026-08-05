package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.model.MCAModelLayers;
import net.conczin.mca.client.model.MCAArmorModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.Infectable;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class VillagerLikeEntityMCARenderer<T extends Mob & VillagerLike<T>> extends HumanoidMobRenderer<T, VillagerEntityModelMCA<T>> {
    private static final double CARRIED_NAME_TAG_Y = 0.63;

    public VillagerLikeEntityMCARenderer(EntityRendererProvider.Context ctx, VillagerEntityModelMCA<T> model) {
        super(ctx, model, 0.5F);
        addLayer(new HumanoidArmorLayer<>(
                this,
                new MCAArmorModel<>(ctx.bakeLayer(MCAModelLayers.VILLAGER_INNER_ARMOR)),
                new MCAArmorModel<>(ctx.bakeLayer(MCAModelLayers.VILLAGER_OUTER_ARMOR)),
                ctx.getModelManager()
        ));
    }

    @Override
    protected void scale(T villager, PoseStack matrices, float tickDelta) {
        float height = villager.getRawVerticalScaleFactor();
        float width = villager.getRawHorizontalScaleFactor();
        matrices.scale(width, height, width);
        if (villager.getAgeState() == AgeState.BABY && !villager.isPassenger()) {
            matrices.translate(0, 0.6F, 0);
        }
    }

    @Override
    protected boolean shouldShowName(T villager) {
        Player player = Minecraft.getInstance().player;
        return villager.getCustomName() != null
               && !(Minecraft.getInstance().screen instanceof VillagerEditorScreen)
               && player != null
               && Config.getInstance().showNameTags
               && player.distanceToSqr(villager) < Math.pow(Config.getInstance().nameTagDistance, 2.0f)
               && !villager.isInvisibleTo(player);
    }

    @Override
    protected void renderNameTag(T villager, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (!(villager.getVehicle() instanceof Player)) {
            super.renderNameTag(villager, displayName, poseStack, bufferSource, packedLight, partialTick);
            return;
        }

        if (this.entityRenderDispatcher.distanceToSqr(villager) > 4096.0) {
            return;
        }

        Vec3 attachment = villager.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, villager.getViewYRot(partialTick));
        if (attachment == null) {
            return;
        }

        boolean visibleThroughWalls = !villager.isDiscrete();
        int yOffset = "deadmau5".equals(displayName.getString()) ? -10 : 0;
        poseStack.pushPose();
        poseStack.translate(attachment.x, CARRIED_NAME_TAG_Y + 0.5, attachment.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        float backgroundOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int backgroundColor = (int) (backgroundOpacity * 255.0F) << 24;
        Font font = this.getFont();
        float x = (float) (-font.width(displayName) / 2);
        font.drawInBatch(displayName, x, yOffset, 553648127, false, matrix, bufferSource, visibleThroughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, backgroundColor, packedLight);
        if (visibleThroughWalls) {
            font.drawInBatch(displayName, x, yOffset, -1, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
        }

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(T mobEntity) {
        return SkinExporter.getSkin(mobEntity);
    }

    @Override
    protected boolean isShaking(T entity) {
        return entity.getInfectionProgress() > Infectable.FEVER_THRESHOLD;
    }
}
