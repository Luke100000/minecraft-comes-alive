package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerMorphologyModel;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** Renders MCA player morphology on top of the actual player model supplied by the renderer. */
public final class PlayerMorphologyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int TRANSLUCENT_WHITE = 0x26FFFFFF;

    private final PlayerMorphologyModel morphology;

    public PlayerMorphologyLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer,
            ModelPart attachments
    ) {
        super(renderer);
        morphology = new PlayerMorphologyModel(attachments);
    }

    @Override
    public void render(
            PoseStack matrices,
            MultiBufferSource buffers,
            int light,
            AbstractClientPlayer player,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        VillagerLike<?> villager = MCAClient.getGeneticsPlayerData(player.getUUID())
                .orElse(null);
        if (villager == null) {
            return;
        }

        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        boolean villagerSkin = villager.getPlayerModel() == VillagerLike.PlayerModel.VILLAGER;
        morphology.apply(villager, !villagerSkin && parent.jacket.visible);

        ResourceLocation texture = getTextureLocation(player);
        Minecraft minecraft = Minecraft.getInstance();
        boolean visible = !player.isInvisible();
        boolean translucent = !visible && !player.isInvisibleTo(minecraft.player);
        boolean glowing = minecraft.shouldEntityAppearGlowing(player);
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
                LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                color
        );
    }
}
