package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.IdentifierException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;

public final class PlayerClothingOverlayLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final PlayerModel clothingModel;

    public PlayerClothingOverlayLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, PlayerModel clothingModel) {
        super(renderer);
        this.clothingModel = clothingModel;
        this.clothingModel.body.skipDraw = true;
        this.clothingModel.rightArm.skipDraw = true;
        this.clothingModel.leftArm.skipDraw = true;
        this.clothingModel.rightLeg.skipDraw = true;
        this.clothingModel.leftLeg.skipDraw = true;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, AvatarRenderState renderState, float yRot, float xRot) {
        Avatar avatar = mca$getAvatar(renderState);
        if (avatar == null || avatar.isInvisible() || !MCAClient.useGeneticsRenderer(avatar.getUUID())) {
            return;
        }

        VillagerLike<?> villager = MCAClient.getPlayerData(avatar.getUUID()).orElse(null);
        if (villager == null) {
            return;
        }

        Identifier texture = resolveClothingTexture(villager);
        if (texture == null) {
            return;
        }

        PlayerModel parent = this.getParentModel();
        copyPartPose(parent.body, this.clothingModel.body);
        copyPartPose(parent.rightArm, this.clothingModel.rightArm);
        copyPartPose(parent.leftArm, this.clothingModel.leftArm);
        copyPartPose(parent.rightLeg, this.clothingModel.rightLeg);
        copyPartPose(parent.leftLeg, this.clothingModel.leftLeg);

        this.clothingModel.head.visible = false;
        this.clothingModel.hat.visible = false;
        this.clothingModel.body.visible = parent.body.visible;
        this.clothingModel.rightArm.visible = parent.rightArm.visible;
        this.clothingModel.leftArm.visible = parent.leftArm.visible;
        this.clothingModel.rightLeg.visible = parent.rightLeg.visible;
        this.clothingModel.leftLeg.visible = parent.leftLeg.visible;
        this.clothingModel.jacket.visible = parent.jacket.visible && parent.body.visible;
        this.clothingModel.rightSleeve.visible = parent.rightSleeve.visible && parent.rightArm.visible;
        this.clothingModel.leftSleeve.visible = parent.leftSleeve.visible && parent.leftArm.visible;
        this.clothingModel.rightPants.visible = parent.rightPants.visible && parent.rightLeg.visible;
        this.clothingModel.leftPants.visible = parent.leftPants.visible && parent.leftLeg.visible;

        submitNodeCollector.submitModel(
                this.clothingModel,
                renderState,
                poseStack,
                RenderTypes.entityCutoutNoCull(texture),
                light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                null,
                renderState.outlineColor,
                null
        );
    }

    private static void copyPartPose(net.minecraft.client.model.geom.ModelPart source, net.minecraft.client.model.geom.ModelPart target) {
        target.loadPose(source.storePose());
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    private static Identifier resolveClothingTexture(VillagerLike<?> villager) {
        String gender = villager.getGenetics().getGender().getDataName();
        String identifier = villager.getClothes();
        if (identifier == null || identifier.isBlank() || "mca:missing".equals(identifier) || "minecraft:missing".equals(identifier)) {
            identifier = "mca:skins/clothing/normal/" + gender + "/none/0.png";
        }

        if (identifier.startsWith("immersive_library:")) {
            try {
                return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
            } catch (NumberFormatException ignored) {
                identifier = "mca:skins/clothing/normal/" + gender + "/none/0.png";
            }
        }

        try {
            Identifier texture = Identifier.parse(identifier);
            return canUse(texture) ? texture : null;
        } catch (IdentifierException ignored) {
            Identifier fallback = MCA.locate("skins/clothing/normal/" + gender + "/none/0.png");
            return canUse(fallback) ? fallback : null;
        }
    }

    private static boolean canUse(Identifier texture) {
        return texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    private static Avatar mca$getAvatar(AvatarRenderState renderState) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }

        Entity entity = Minecraft.getInstance().level.getEntity(renderState.id);
        return entity instanceof Avatar avatar ? avatar : null;
    }
}