package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;

public class ClothingLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private final String variant;

    public ClothingLayer(RenderLayerParent<S, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public ResourceLocation getSkin(S state) {
        return getSkin(VillagerVisualSnapshot.require(state));
    }

    public ResourceLocation getSkin(VillagerVisualSnapshot visuals) {
        String identifier = visuals.clothes();
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        String v = visuals.burned() ? "burnt" : variant;
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return cached(identifier + v, clothes -> {
            ResourceLocation id = ResourceLocation.parse(visuals.clothes());
            ResourceLocation idNew = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("normal", v));
            return canUse(idNew) ? idNew : id;
        });
    }
}
