package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

public final class VillagerMorphologyLayer<T extends LivingEntity & VillagerLike<T>>
        extends RenderLayer<T, PlayerModel<T>> {
    private static final int TRANSLUCENT_WHITE = 0x26FFFFFF;

    private final BreastMorphologyModel morphology;

    public VillagerMorphologyLayer(
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
            T villager,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        PlayerModel<T> parent = getParentModel();
        morphology.apply(villager, false);

        ResourceLocation texture = getTextureLocation(villager);
        Minecraft minecraft = Minecraft.getInstance();
        boolean visible = !villager.isInvisible();
        boolean translucent = !visible && !villager.isInvisibleTo(minecraft.player);
        boolean glowing = minecraft.shouldEntityAppearGlowing(villager);

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
        color = FastColor.ARGB32.multiply(color, SkinExporter.getSkinColor(villager));
        VertexConsumer vertices = buffers.getBuffer(renderType);
        morphology.render(
                parent,
                matrices,
                vertices,
                light,
                LivingEntityRenderer.getOverlayCoords(villager, 0.0F),
                color
        );
    }
}
