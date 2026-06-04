package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.IdentifierException;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;

public class HairLayer<M extends HumanoidModel<MCAHumanoidRenderState>> extends VillagerLayer<M> {
   public HairLayer(RenderLayerParent<MCAHumanoidRenderState, M> renderer, M model) {
      super(renderer, model);
   }

   @Override
   protected boolean shouldApplyModelSetupAnim() {
      return false;
   }

   private static boolean isMissingIdentifier(String identifier) {
      return identifier == null
         || identifier.isBlank()
         || "mca:missing".equals(identifier)
         || "minecraft:missing".equals(identifier)
         || identifier.endsWith(":missing")
         || identifier.endsWith("/missing")
         || identifier.endsWith("/missing.png");
   }

   private static String defaultIdentifier(VillagerLike<?> villager) {
      String gender = villager.getGenetics().getGender().getDataName();
      return "mca:skins/hair/" + gender + "/0.png";
   }

   private static String normalizeIdentifier(VillagerLike<?> villager, String identifier) {
      if (isMissingIdentifier(identifier)) {
         return defaultIdentifier(villager);
      } else if (identifier.contains("/skins/hair/normal/")) {
         return identifier.replace("/skins/hair/normal/", "/skins/hair/");
      } else {
         return identifier.contains("/hair/normal/") ? identifier.replace("/hair/normal/", "/hair/") : identifier;
      }
   }

   @Override
   public void adjustVisibility(MCAHumanoidRenderState renderState) {
      this.model.setAllVisible(false);
      this.model.head.visible = true;
      this.model.hat.visible = true;
   }

   @Override
   public Identifier getSkin(MCAHumanoidRenderState renderState) {
      VillagerLike<?> villager = (VillagerLike<?>)renderState.villager;
      if (villager == null) {
         return null;
      } else {
         String fallbackIdentifier = defaultIdentifier(villager);
         String identifier = normalizeIdentifier(villager, villager.getHair());
         if (identifier.startsWith("immersive_library:")) {
            try {
               return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
            } catch (NumberFormatException var7) {
               identifier = fallbackIdentifier;
            }
         }

         return this.cached(identifier, id -> {
            Identifier resolved;
            try {
               resolved = Identifier.parse(id);
            } catch (IdentifierException var5) {
               resolved = Identifier.parse(fallbackIdentifier);
            }

            if (this.canUse(resolved)) {
               return resolved;
            } else {
               Identifier fallback = Identifier.parse(fallbackIdentifier);
               return this.canUse(fallback) ? fallback : null;
            }
         });
      }
   }

   @Override
   protected Identifier getOverlay(MCAHumanoidRenderState renderState) {
      VillagerLike<?> villager = (VillagerLike<?>)renderState.villager;
      if (villager == null) {
         return null;
      } else {
         String hair = normalizeIdentifier(villager, villager.getHair());
         if (hair.startsWith("immersive_library:")) {
            return null;
         } else {
            String fallbackOverlay = defaultIdentifier(villager).replace(".png", "_overlay.png");
            String overlayName = hair.replace(".png", "_overlay.png");
            return this.cached(overlayName, id -> {
               Identifier resolved;
               try {
                  resolved = Identifier.parse(id);
               } catch (IdentifierException var5x) {
                  resolved = Identifier.parse(fallbackOverlay);
               }

               if (this.canUse(resolved)) {
                  return resolved;
               } else {
                  Identifier fallback = Identifier.parse(fallbackOverlay);
                  return this.canUse(fallback) ? fallback : null;
               }
            });
         }
      }
   }

   private float[] getRainbow(Entity e, float tickDelta) {
      int n = Math.abs(e.tickCount) / 25 + e.getId();
      int o = DyeColor.values().length;
      int p = n % o;
      int q = (n + 1) % o;
      float r = (Math.abs(e.tickCount) % 25 + tickDelta) / 25.0F;
      int dp = DyeColor.byId(p).getTextureDiffuseColor();
      int dq = DyeColor.byId(q).getTextureDiffuseColor();
      float[] fs = new float[]{(dp >> 16 & 0xFF) / 255.0F, (dp >> 8 & 0xFF) / 255.0F, (dp & 0xFF) / 255.0F};
      float[] gs = new float[]{(dq >> 16 & 0xFF) / 255.0F, (dq >> 8 & 0xFF) / 255.0F, (dq & 0xFF) / 255.0F};
      return new float[]{fs[0] * (1.0F - r) + gs[0] * r, fs[1] * (1.0F - r) + gs[1] * r, fs[2] * (1.0F - r) + gs[2] * r};
   }

   @Override
   public int getColor(MCAHumanoidRenderState renderState, float tickDelta) {
      VillagerLike<?> villager = (VillagerLike<?>)renderState.villager;
      if (villager == null) {
         return -1;
      } else if (villager.getTraits().hasTrait(Traits.RAINBOW)) {
         float[] rC = this.getRainbow((Entity)villager, tickDelta);
         return 0xFF000000 | (int)(rC[0] * 255.0F) << 16 | (int)(rC[1] * 255.0F) << 8 | (int)(rC[2] * 255.0F);
      } else {
         int hairDye = villager.getHairDye();
         if ((hairDye & 16777215) != 0) {
            return hairDye;
         } else {
            float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1F : 1.0F;
            return ColorPalette.HAIR
               .getColor(villager.getGenetics().getGene(Genetics.EUMELANIN) * albinism, villager.getGenetics().getGene(Genetics.PHEOMELANIN) * albinism, 0.0F);
         }
      }
   }
}
