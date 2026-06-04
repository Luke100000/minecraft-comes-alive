package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.MCAClient;
import net.conczin.mca.item.RelationshipItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PlayerRingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ItemStackRenderState ringRenderState = new ItemStackRenderState();

    public PlayerRingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, AvatarRenderState renderState, float yRot, float xRot) {
        Avatar avatar = mca$getAvatar(renderState);
        if (avatar == null || avatar.isInvisible()) {
            return;
        }

        ItemStack ringStack = MCAClient.getEquippedRing(avatar.getUUID()).orElseGet(avatar::getOffhandItem);
        if (!RelationshipItem.isRing(ringStack)) {
            return;
        }

        ringRenderState.clear();
        Minecraft.getInstance().getItemModelResolver().updateForNonLiving(ringRenderState, ringStack, ItemDisplayContext.FIXED, avatar);
        if (ringRenderState.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.28F, -0.19F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.35F, 0.35F, 0.35F);
        ringRenderState.submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        poseStack.popPose();
    }

    private static Avatar mca$getAvatar(AvatarRenderState renderState) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }

        Entity entity = Minecraft.getInstance().level.getEntity(renderState.id);
        return entity instanceof Avatar avatar ? avatar : null;
    }
}
