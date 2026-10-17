package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.BreastMorphologyModel;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

/** Renders MCA morphology using the authoritative parent model's body transform. */
public final class MorphologyLayer<T extends LivingEntity> extends RenderLayer<T, PlayerModel<T>> {
    private static final int TRANSLUCENT_WHITE = 0x26FFFFFF;

    private final BreastMorphologyModel morphology;

    public MorphologyLayer(
            RenderLayerParent<T, PlayerModel<T>> renderer,
            ModelPart attachments
    ) {
        super(renderer);
        morphology = new BreastMorphologyModel(attachments);
    }

    @Override
    public void render(
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            T entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        // Player data may still be needed for hitbox dimensions when MCA's player
        // renderer is disabled. In that case no player morphology should be drawn.
        if (!(entity instanceof VillagerLike<?>) && !MCAClient.isPlayerRendererAllowed()) {
            return;
        }

        VillagerLike<?> villager = entity instanceof VillagerLike<?> data
                ? data
                : MCAClient.getGeneticsPlayerData(entity.getUUID()).orElse(null);
        if (villager == null) {
            return;
        }

        PlayerModel<T> parent = getParentModel();
        boolean villagerSkin = entity instanceof VillagerLike<?>
                || villager.getPlayerModel() == VillagerLike.PlayerModel.VILLAGER;
        morphology.apply(villager, !villagerSkin && parent.jacket.visible);

        ResourceLocation texture = getTextureLocation(entity);
        Minecraft minecraft = Minecraft.getInstance();
        boolean visible = !entity.isInvisible();
        boolean translucent = !visible && minecraft.player != null && !entity.isInvisibleTo(minecraft.player);
        boolean glowing = minecraft.shouldEntityAppearGlowing(entity);
        RenderType renderType;
        if (translucent) {
            renderType = RenderType.itemEntityTranslucentCull(texture);
        } else if (visible) {
            renderType = parent.renderType(texture);
        } else if (glowing) {
            renderType = RenderType.outline(texture);
        } else {
            return;
        }
        if (renderType == null) {
            return;
        }

        int color = translucent ? TRANSLUCENT_WHITE : 0xFFFFFFFF;
        if (villagerSkin) {
            color = FastColor.ARGB32.multiply(color, SkinExporter.getSkinColor(villager));
        }
        VertexConsumer vertices = buffers.getBuffer(renderType);
        morphology.render(
                parent,
                matrices,
                vertices,
                light,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                color
        );
    }
}
