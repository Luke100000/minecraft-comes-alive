package net.mca.client.render;

import net.mca.MCA;
import net.mca.client.model.CribEntityModel;
import net.mca.entity.CribEntity;
import net.mca.entity.CribWoodType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import javax.imageio.ImageIO;
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

    private final Map<String, Identifier> REGISTERED_TEXTURES = new HashMap<>();

    protected CribEntityModel<CribEntity> model;

    private final ItemRenderer itemRenderer;

    public CribEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);

        this.itemRenderer = ctx.getItemRenderer();

        this.model = new CribEntityModel<>(TexturedModelData.of(CribEntityModel.getModelData(Dilation.NONE), TEXTURE_WIDTH, TEXTURE_HEIGHT).createModel());
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
    public void render(CribEntity cribEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        Identifier texture = REGISTERED_TEXTURES.get(getTextureID(cribEntity));

        matrixStack.push();
        matrixStack.translate(0.0, 0.375, 0.0);
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - f));

        matrixStack.scale(-1.0f, -1.0f, 1.0f);
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
        this.model.setAngles(cribEntity, g, 0.0f, -0.1f, 0.0f, 0.0f);
        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(this.model.getLayer(texture));
        this.model.render(matrixStack, vertexConsumer, i, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);

        ItemStack babyItem = cribEntity.getBabyItem();
        if (!babyItem.equals(ItemStack.EMPTY)) {
            matrixStack.translate(0.0f, 0.05f, 0f);
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
            matrixStack.scale(0.75f, 0.75f, 0.75f);

            this.itemRenderer.renderItem(babyItem, ModelTransformationMode.FIXED, i, OverlayTexture.DEFAULT_UV, matrixStack, vertexConsumerProvider, cribEntity.getWorld(), cribEntity.getId());
        }

        matrixStack.pop();
        super.render(cribEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }

    private String getTextureID(CribEntity cribEntity) {
        return getTextureID(cribEntity.getWoodType(), cribEntity.getColor());
    }

    private String getTextureID(CribWoodType wood, DyeColor color) {
        return wood.toString().toLowerCase(Locale.ROOT) + "-" + color.getName();
    }

    // Create the crib texture from multiple layers depending on crib wood material and wool color
    private Identifier generateMultiTexture(CribWoodType wood, DyeColor color) throws IOException {
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

        NativeImageBackedTexture dynTex = new NativeImageBackedTexture(NativeImage.read(bytes));

        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture(MCA.MOD_ID, dynTex);
    }

    @Override
    public Identifier getTexture(CribEntity crib) {
        return REGISTERED_TEXTURES.get(getTextureID(crib));
    }
}