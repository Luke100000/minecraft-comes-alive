package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;

import static net.conczin.mca.client.model.CommonVillagerModel.getVillager;

public class HairLayer<S extends HumanoidRenderState & VillagerStateHolder, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    public HairLayer(RenderLayerParent<S, M> renderer, M model) {
        super(renderer, model);
        if (model instanceof CommonVillagerModel<?> commonVillagerModel) {
            commonVillagerModel.setRenderMask(CommonVillagerModel.RenderMask.NO_LEGS);
        }
    }

    @Override
    protected void prepareModel(S state) {
        setAllVisible(this.model, true);
        this.model.leftLeg.visible = false;
        this.model.rightLeg.visible = false;
    }

    @Override
    public Identifier getSkin(S state) {
        String identifier = getVillager(state).getHair();
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return cached(identifier, Identifier::parse);
    }

    @Override
    protected Identifier getOverlay(S state) {
        return cached(getVillager(state).getHair().replace(".png", "_overlay.png"), Identifier::parse);
    }

    private int getRainbow(net.minecraft.world.entity.LivingEntity entity, float tickDelta) {
        int n = Math.abs(entity.tickCount) / 25 + entity.getId();
        int o = DyeColor.values().length;
        int p = n % o;
        int q = (n + 1) % o;
        float r = ((float) (Math.abs(entity.tickCount) % 25) + tickDelta) / 25.0f;
        return ARGB.srgbLerp(r, DyeColor.byId(p).getTextureDiffuseColor(), DyeColor.byId(q).getTextureDiffuseColor());
    }

    @Override
    public int getColor(S state, float tickDelta) {
        if (getVillager(state).getTraits().hasTrait(Traits.RAINBOW)) {
            return getRainbow((net.minecraft.world.entity.LivingEntity) getVillager(state), tickDelta);
        }

        int hairDye = getVillager(state).getHairDye();
        if (hairDye != 0xFF000000) {
            return hairDye;
        }

        float albinism = getVillager(state).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

        return ColorPalette.HAIR.getColor(
                getVillager(state).getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                getVillager(state).getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                0
        );
    }
}
