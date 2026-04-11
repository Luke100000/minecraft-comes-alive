package net.conczin.mca.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CribEntityModel;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.CribWoodType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CribEntityRenderer extends EntityRenderer<CribEntity, CribEntityRenderer.CribRenderState> {
   private static final Identifier TEXTURE = MCA.locate("textures/entity/crib.png");
   private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();
   private static final int TEXTURE_SIZE = 64;
   private final CribEntityModel model = new CribEntityModel(LayerDefinition.create(CribEntityModel.getModelData(CubeDeformation.NONE), 64, 64).bakeRoot());

   public CribEntityRenderer(Context ctx) {
      super(ctx);
      this.shadowRadius = 0.75F;
   }

   public CribEntityRenderer.CribRenderState createRenderState() {
      return new CribEntityRenderer.CribRenderState();
   }

   public void extractRenderState(CribEntity entity, CribEntityRenderer.CribRenderState state, float partialTick) {
      super.extractRenderState(entity, state, partialTick);
      state.yRot = entity.getYRot();
      state.xRot = entity.getXRot();
      state.wood = entity.getWoodType();
      state.color = entity.getColor();
      state.texture = getTexture(state.wood, state.color);
      state.infant = entity.getFirstPassenger() instanceof VillagerEntityMCA infant ? infant : null;
      state.babyRenderState.clear();
      ItemStack babyItem = entity.getBabyItem();
      if (state.infant == null && !babyItem.isEmpty()) {
         Minecraft.getInstance().getItemModelResolver().updateForNonLiving(state.babyRenderState, babyItem, ItemDisplayContext.FIXED, entity);
      }
   }

   public void submit(
      CribEntityRenderer.CribRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
   ) {
      super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
      poseStack.pushPose();
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - renderState.yRot));
      poseStack.mulPose(Axis.XP.rotationDegrees(renderState.xRot));
      poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
      this.model.setupAnim((EntityRenderState)renderState);
      submitNodeCollector.submitModel(
         this.model,
         renderState,
         poseStack,
         RenderTypes.entityCutoutNoCull(renderState.texture),
         renderState.lightCoords,
         0,
         -1,
         null,
         renderState.outlineColor,
         null
      );
      if (renderState.infant != null) {
         poseStack.pushPose();
         poseStack.translate(0.0, 0.15, 0.0);
         poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
         renderInfant(renderState.infant, poseStack, submitNodeCollector, cameraRenderState);
         poseStack.popPose();
      } else if (!renderState.babyRenderState.isEmpty()) {
         poseStack.pushPose();
         poseStack.translate(0.0, -0.3, 0.0);
         poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
         poseStack.scale(0.92F, 0.92F, 0.92F);
         renderState.babyRenderState.submit(poseStack, submitNodeCollector, renderState.lightCoords, 0, renderState.outlineColor);
         poseStack.popPose();
      }

      poseStack.popPose();
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static void renderInfant(VillagerEntityMCA infant, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
      EntityRenderer<? super VillagerEntityMCA, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(infant);
      if (renderer instanceof EntityRenderer) {
         EntityRenderState infantRenderState = renderer.createRenderState(infant, 0.0F);
         infantRenderState.nameTag = null;
         infantRenderState.shadowRadius = 0.0F;
         infantRenderState.shadowPieces.clear();
         if (infantRenderState instanceof MCAHumanoidRenderState mcaHumanoidRenderState) {
            mcaHumanoidRenderState.isPassenger = true;
            mcaHumanoidRenderState.isBaby = infant.isBaby();
            mcaHumanoidRenderState.cribPassenger = true;
            mcaHumanoidRenderState.walkAnimationPos = 0.0F;
            mcaHumanoidRenderState.walkAnimationSpeed = 0.0F;
         }

         ((EntityRenderer)renderer).submit(infantRenderState, poseStack, submitNodeCollector, cameraRenderState);
      }
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
         BufferedImage image = new BufferedImage(64, 64, 2);
         Graphics2D graphics = image.createGraphics();
         graphics.drawImage(frame, 0, 0, null);
         graphics.drawImage(bed, 0, 0, null);
         graphics.dispose();
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         if (!ImageIO.write(image, "png", output)) {
            throw new IOException("Could not encode crib texture " + key);
         } else {
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
            Minecraft.getInstance().getTextureManager().register(textureId, new DynamicTexture(textureId::toString, nativeImage));
            return textureId;
         }
      } catch (Exception var10) {
         MCA.LOGGER.warn("Failed to build crib texture for {} {}", color, wood, var10);
         return TEXTURE;
      }
   }

   private static BufferedImage loadTexture(Identifier textureId) throws IOException {
      Resource resource = (Resource)Minecraft.getInstance().getResourceManager().getResource(textureId).orElseThrow();

      BufferedImage var4;
      try (InputStream stream = resource.open()) {
         BufferedImage image = ImageIO.read(stream);
         if (image == null) {
            throw new IOException("Unable to read image " + textureId);
         }

         var4 = image;
      }

      return var4;
   }

   public static final class CribRenderState extends EntityRenderState {
      public float yRot;
      public float xRot;
      public CribWoodType wood;
      public DyeColor color;
      public Identifier texture = CribEntityRenderer.TEXTURE;
      public VillagerEntityMCA infant;
      public final ItemStackRenderState babyRenderState = new ItemStackRenderState();
   }
}
