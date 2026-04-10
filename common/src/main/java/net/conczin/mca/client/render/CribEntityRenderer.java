package net.conczin.mca.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CribEntityModel;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.CribWoodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

public class CribEntityRenderer extends EntityRenderer<CribEntity, CribEntityRenderer.CribRenderState> {
    private static final Identifier TEXTURE = MCA.locate("textures/entity/crib.png");
    private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final int TEXTURE_SIZE = 64;

    private final CribEntityModel model;

    public CribEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new CribEntityModel(LayerDefinition.create(CribEntityModel.getModelData(CubeDeformation.NONE), 64, 64).bakeRoot());
        this.shadowRadius = 0.75F;
    }

    @Override
    public CribRenderState createRenderState() {
        return new CribRenderState();
    }

    @Override
    public void extractRenderState(CribEntity entity, CribRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yRot = entity.getYRot();
        state.xRot = entity.getXRot();
        state.wood = entity.getWoodType();
        state.color = entity.getColor();
        state.texture = getTexture(state.wood, state.color);
    }

    @Override
    public void submit(CribRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState) {
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.375D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - renderState.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(renderState.xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        model.setupAnim(renderState);
        submitNodeCollector.submitModel(model, renderState, poseStack, RenderTypes.entityCutoutNoCull(renderState.texture),
                renderState.lightCoords, 0, 0xFFFFFFFF, null, renderState.outlineColor, null);
        poseStack.popPose();
    }

    private static Identifier getTexture(CribWoodType wood, DyeColor color) {
        String key = color.getName() + "_" + wood.name().toLowerCase(Locale.ROOT);
        return TEXTURE_CACHE.computeIfAbsent(key, ignored -> buildTexture(wood, color, key));
    }

    private static Identifier buildTexture(CribWoodType wood, DyeColor color, String key) {
        Identifier textureId = MCA.locate("dynamic/crib/" + key);
        try {
            BufferedImage bed = loadTexture(MCA.locate("textures/entity/crib/beds/" + color.getName() + ".png"));
            BufferedImage frame = loadTexture(MCA.locate("textures/entity/crib/frames/" + wood.name().toLowerCase(Locale.ROOT) + ".png"));
            BufferedImage image = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);

            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(frame, 0, 0, null);
            graphics.drawImage(bed, 0, 0, null);
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("Could not encode crib texture " + key);
            }

            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
            Minecraft.getInstance().getTextureManager().register(textureId, new DynamicTexture(textureId::toString, nativeImage));
            return textureId;
        } catch (Exception e) {
            MCA.LOGGER.warn("Failed to build crib texture for {} {}", color, wood, e);
            return TEXTURE;
        }
    }

    private static BufferedImage loadTexture(Identifier textureId) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(textureId).orElseThrow();
        try (InputStream stream = resource.open()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Unable to read image " + textureId);
            }
            return image;
        }
    }

    public static final class CribRenderState extends EntityRenderState {
        public float yRot;
        public float xRot;
        public CribWoodType wood;
        public DyeColor color;
        public Identifier texture = TEXTURE;
    }
}
