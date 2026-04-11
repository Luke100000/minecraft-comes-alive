package net.conczin.mca.client.render.layer;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.function.Function;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.minecraft.IdentifierException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public abstract class VillagerLayer<M extends HumanoidModel<MCAHumanoidRenderState>> extends RenderLayer<MCAHumanoidRenderState, M> {
   private static final Map<String, Identifier> TEXTURE_CACHE = Maps.newHashMap();
   private static final Map<Identifier, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();
   public final M model;

   public VillagerLayer(RenderLayerParent<MCAHumanoidRenderState, M> renderer, M model) {
      super(renderer);
      this.model = model;
   }

   @Nullable
   public Identifier getSkin(MCAHumanoidRenderState renderState) {
      return null;
   }

   @Nullable
   protected Identifier getOverlay(MCAHumanoidRenderState renderState) {
      return null;
   }

   public void adjustVisibility(MCAHumanoidRenderState renderState) {
   }

   public int getColor(MCAHumanoidRenderState renderState, float tickDelta) {
      return -1;
   }

   private static void copyPartPose(ModelPart source, ModelPart target) {
      target.loadPose(source.storePose());
      target.xScale = source.xScale;
      target.yScale = source.yScale;
      target.zScale = source.zScale;
   }

   private void syncPoseFromParent() {
      M parent = (M)this.getParentModel();
      copyPartPose(parent.head, this.model.head);
      copyPartPose(parent.hat, this.model.hat);
      copyPartPose(parent.body, this.model.body);
      copyPartPose(parent.rightArm, this.model.rightArm);
      copyPartPose(parent.leftArm, this.model.leftArm);
      copyPartPose(parent.rightLeg, this.model.rightLeg);
      copyPartPose(parent.leftLeg, this.model.leftLeg);
   }

   protected boolean isTranslucent() {
      return false;
   }

   public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, MCAHumanoidRenderState renderState, float yRot, float xRot) {
      if (renderState.visible || renderState.glowing) {
         if (renderState.villager != null && !renderState.villager.isInvisible()) {
            if (!(renderState.villager instanceof Player player && !MCAClient.useVillagerRenderer(player.getUUID()))) {
               if (this.model instanceof VillagerEntityModelMCA layerModel) {
                  layerModel.copyVisibility((HumanoidModel<? extends HumanoidRenderState>)this.getParentModel());
               }

               this.model.setupAnim(renderState);
               this.syncPoseFromParent();
               this.adjustVisibility(renderState);
               int tint = LivingEntityRenderer.getOverlayCoords(renderState, 0.0F);
               Identifier skin = this.getSkin(renderState);
               if (this.canUse(skin)) {
                  int color = this.getColor(renderState, 0.0F);
                  this.renderModel(poseStack, submitNodeCollector, light, this.model, renderState, color, skin, tint, renderState.visible, renderState.glowing);
               }

               Identifier overlay = this.getOverlay(renderState);
               if (overlay != null && !overlay.equals(skin) && this.canUse(overlay)) {
                  this.renderModel(poseStack, submitNodeCollector, light, this.model, renderState, -1, overlay, tint, renderState.visible, renderState.glowing);
               }
            }
         }
      }
   }

   @Nullable
   protected RenderType getRenderLayer(Identifier texture, boolean showBody, boolean translucent, boolean showOutline) {
      if (translucent) {
         return RenderTypes.itemEntityTranslucentCull(texture);
      } else if (showBody) {
         return RenderTypes.entityCutoutNoCull(texture);
      } else {
         return showOutline ? RenderTypes.outline(texture) : null;
      }
   }

   private void renderModel(
      PoseStack transform,
      SubmitNodeCollector provider,
      int light,
      M model,
      MCAHumanoidRenderState renderState,
      int color,
      Identifier texture,
      int overlay,
      boolean visible,
      boolean glowing
   ) {
      RenderType layer = this.getRenderLayer(texture, visible, this.isTranslucent(), glowing);
      if (layer != null) {
         provider.submitModel(model, renderState, transform, layer, light, overlay, color, null, 0, null);
      }
   }

   public final boolean canUse(Identifier texture) {
      return TEXTURE_EXIST_CACHE.computeIfAbsent(texture, s -> {
         if (texture != null && texture.getNamespace().equals("immersive_library")) {
            return true;
         } else {
            boolean result = texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
            System.out.println("MCA_DEBUG canUse: " + texture + " -> " + result);
            return result;
         }
      });
   }

   @Nullable
   protected final Identifier cached(String name, Function<String, Identifier> supplier) {
      return TEXTURE_CACHE.computeIfAbsent(name, s -> {
         try {
            return supplier.apply(s);
         } catch (IdentifierException var3) {
            return null;
         }
      });
   }

   static {
      TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
   }
}
