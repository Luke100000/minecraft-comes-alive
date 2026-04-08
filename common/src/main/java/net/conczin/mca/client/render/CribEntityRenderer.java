package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CribEntityModel;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.CribWoodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.*;
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

    private final Map<String, Identifier> registeredTextures = new HashMap<>();
    private final ItemModelResolver itemModelResolver;
    protected final CribEntityModel model;

    public CribEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);

        this.itemModelResolver = ctx.getItemModelResolver();
        this.model = new CribEntityModel(LayerDefinition.create(CribEntityModel.getModelData(CubeDeformation.NONE), TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot());
        this.shadowRadius = 0.75F;

        for (CribWoodType woodType : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                try {
                    registeredTextures.put(getTextureID(woodType, color), generateMultiTexture(woodType, color));
                } catch (IOException e) {
                    MCA.LOGGER.warn("And error occurred while loading dynamic crib texture! Skipping...\n{}", e.getMessage());
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
        state.yRot = Mth.rotLerp(partialTicks, cribEntity.yRotO, cribEntity.getYRot());
        this.itemModelResolver.updateForNonLiving(state.babyItem, cribEntity.getBabyItem(), ItemDisplayContext.FIXED, cribEntity);
    }

    @Override
    public void submit(CribEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        CribEntity crib = state.crib;
        if (crib == null) {
            return;
        }

        Identifier texture = getTextureLocation(state);

        poseStack.pushPose();
        poseStack.translate(0.0, 0.375, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        this.model.setupAnim(state);
        submitNodeCollector.submitModel(this.model, state, poseStack, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

        ItemStack babyItem = crib.getBabyItem();
        if (!babyItem.equals(ItemStack.EMPTY) && !state.babyItem.isEmpty()) {
            poseStack.translate(0.0F, 0.05F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            poseStack.scale(0.75F, 0.75F, 0.75F);
            state.babyItem.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private String getTextureID(CribEntity cribEntity) {
        return getTextureID(cribEntity.getWoodType(), cribEntity.getColor());
    }

    private String getTextureID(CribWoodType wood, DyeColor color) {
        return wood.toString().toLowerCase(Locale.ROOT) + "-" + color.getName();
    }

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

        Identifier textureId = Identifier.fromNamespaceAndPath(MCA.MOD_ID, "dynamic/crib/" + getTextureID(wood, color));
        DynamicTexture dynTex = new DynamicTexture(textureId::toString, com.mojang.blaze3d.platform.NativeImage.read(bytes));
        Minecraft.getInstance().getTextureManager().register(textureId, dynTex);
        return textureId;
    }

    public Identifier getTextureLocation(CribEntityRenderState state) {
        return state.crib == null ? registeredTextures.values().stream().findFirst().orElseThrow() : registeredTextures.get(getTextureID(state.crib));
    }
}
