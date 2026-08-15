package net.conczin.mca.client.render;

import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CribEntityModel;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.CribWoodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CribEntityRenderer extends EntityRenderer<CribEntity> {
    private final int TEXTURE_WIDTH = 88;
    private final int TEXTURE_HEIGHT = 60;

    private final Map<String, ResourceLocation> REGISTERED_TEXTURES = new HashMap<>();

    protected CribEntityModel<CribEntity> model;

    private final ItemRenderer itemRenderer;

    public CribEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);

        this.itemRenderer = ctx.getItemRenderer();

        this.model = new CribEntityModel<>(LayerDefinition.create(CribEntityModel.getModelData(CubeDeformation.NONE), TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot());
        this.shadowRadius = 0.75F;

        for (CribWoodType woodType : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                try {
                    REGISTERED_TEXTURES.put(getTextureID(woodType, color), generateMultiTexture(woodType, color));
                } catch (IOException e) {
                    MCA.LOGGER.warn("And error occurred while loading dynamic crib texture! Skipping...\n{}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void render(CribEntity cribEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
        ResourceLocation texture = REGISTERED_TEXTURES.get(getTextureID(cribEntity));

        matrixStack.pushPose();
        matrixStack.translate(0.0, 0.375, 0.0);
        matrixStack.mulPose(Axis.YP.rotationDegrees(180.0f - f));

        matrixStack.scale(-1.0f, -1.0f, 1.0f);
        matrixStack.mulPose(Axis.YP.rotationDegrees(90.0f));
        this.model.setupAnim(cribEntity, g, 0.0f, -0.1f, 0.0f, 0.0f);
        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(this.model.renderType(texture));
        this.model.renderToBuffer(matrixStack, vertexConsumer, i, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

        ItemStack babyItem = cribEntity.getBabyItem();
        if (!babyItem.equals(ItemStack.EMPTY)) {
            matrixStack.translate(0.0f, 0.05f, 0f);
            matrixStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            matrixStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            matrixStack.scale(0.75f, 0.75f, 0.75f);

            this.itemRenderer.renderStatic(babyItem, ItemDisplayContext.FIXED, i, OverlayTexture.NO_OVERLAY, matrixStack, vertexConsumerProvider, cribEntity.level(), cribEntity.getId());
        }

        matrixStack.popPose();
        super.render(cribEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }

    private String getTextureID(CribEntity cribEntity) {
        return getTextureID(cribEntity.getWoodType(), cribEntity.getColor());
    }

    private String getTextureID(CribWoodType wood, DyeColor color) {
        return wood.toString().toLowerCase(Locale.ROOT) + "-" + color.getName();
    }

    // Create the crib texture from multiple layers depending on crib wood material and wool color
    private ResourceLocation generateMultiTexture(CribWoodType wood, DyeColor color) throws IOException {
        ClassLoader loader = MCA.class.getClassLoader();
        InputStream frameStream = loader.getResourceAsStream("assets/mca/textures/entity/crib/frames/" + wood.toString().toLowerCase(Locale.ROOT) + ".png");
        if (frameStream == null) {
            frameStream = loader.getResourceAsStream("assets/mca/textures/entity/crib/frames/oak.png");
        }
        assert frameStream != null;

        BufferedImage frame = ImageIO.read(frameStream);
        InputStream bedStream = loader.getResourceAsStream("assets/mca/textures/entity/crib/beds/" + color.getName() + ".png");
        if (bedStream == null) {
            bedStream = loader.getResourceAsStream("assets/mca/textures/entity/crib/beds/white.png");
        }
        assert bedStream != null;
        BufferedImage bed = ImageIO.read(bedStream);

        BufferedImage combined = new BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics g = combined.getGraphics();
        g.drawImage(frame, 0, 0, null);
        g.drawImage(bed, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(combined, "png", baos);
        byte[] bytes = baos.toByteArray();

        DynamicTexture dynTex = new DynamicTexture(NativeImage.read(bytes));

        return Minecraft.getInstance().getTextureManager().register(MCA.MOD_ID, dynTex);
    }

    @Override
    public ResourceLocation getTextureLocation(CribEntity crib) {
        return REGISTERED_TEXTURES.get(getTextureID(crib));
    }
}
