package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class ClothingLayer<S extends HumanoidRenderState & VillagerStateHolder, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private final String variant;

    public ClothingLayer(RenderLayerParent<S, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public Identifier getSkin(S state) {
        var visuals = net.conczin.mca.client.model.CommonVillagerModel.getVisuals(state);
        String v = visuals.burned() ? "burnt" : variant;
        String identifier = visuals.clothes();
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return cached(identifier + v, clothes -> {
            Identifier id = Identifier.parse(visuals.clothes());

            Identifier idNew = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("normal", v));
            if (canUse(idNew)) {
                return idNew;
            }

            return id;
        });
    }
}
