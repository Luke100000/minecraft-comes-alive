package net.conczin.mca.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CribEntityRenderer extends EntityRenderer<CribEntity, CribEntityRenderState> {
    private static final int TEXTURE_WIDTH = 88;
    private static final int TEXTURE_HEIGHT = 60;

    private final Map<String, ResourceLocation> registeredTextures = new HashMap<>();
    protected final CribEntityModel model;

    public CribEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new CribEntityModel(LayerDefinition.create(CribEntityModel.getModelData(CubeDeformation.NONE), TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot());
        this.shadowRadius = 0.75F;

        for (CribWoodType woodType : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                try {
                    registeredTextures.put(getTextureID(woodType, color), generateMultiTexture(woodType, color));
                } catch (IOException e) {
                    MCA.LOGGER.warn("An error occurred while loading dynamic crib texture! Skipping...\n{}", e.getMessage());
                }
            }
        }
    }

    @Override
    public CribEntityRenderState createRenderState() {
        return new CribEntityRenderState();
    }

    @Override
    public void extractRenderState(CribEntity cribEntity, CribEntityRenderState state, float partialTicks) {
        super.extractRenderState(cribEntity, state, partialTicks);
        state.crib = cribEntity;
        state.yRot = cribEntity.getYRot();
    }

    @Override
    public void render(CribEntityRenderState state, PoseStack matrixStack, MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation texture = getTextureLocation(state);

        matrixStack.pushPose();
        matrixStack.translate(0.0, 0.375, 0.0);
        matrixStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        matrixStack.scale(-1.0F, -1.0F, 1.0F);
        matrixStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        this.model.setupAnim(state);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(this.model.renderType(texture));
        this.model.renderToBuffer(matrixStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        matrixStack.popPose();

        super.render(state, matrixStack, bufferSource, packedLight);
    }

    private String getTextureID(CribEntity cribEntity) {
        return getTextureID(cribEntity.getWoodType(), cribEntity.getColor());
    }

    private String getTextureID(CribWoodType wood, DyeColor color) {
        return wood.toString().toLowerCase(Locale.ROOT) + "-" + color.getName();
    }

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
        ResourceLocation texture = MCA.locate("crib/" + getTextureID(wood, color));
        Minecraft.getInstance().getTextureManager().register(texture, dynTex);
        return texture;
    }

    public ResourceLocation getTextureLocation(CribEntityRenderState state) {
        CribEntity crib = state.crib;
        return crib == null ? registeredTextures.values().stream().findFirst().orElse(MCA.locate("textures/entity/crib/frames/oak.png")) : registeredTextures.get(getTextureID(crib));
    }
}
