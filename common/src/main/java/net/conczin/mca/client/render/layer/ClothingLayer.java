package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import static net.conczin.mca.client.model.CommonVillagerModel.getVillager;

public class ClothingLayer<S extends HumanoidRenderState & VillagerStateHolder, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private final String variant;

    public ClothingLayer(RenderLayerParent<S, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public Identifier getSkin(S state) {
        String v = getVillager(state).isBurned() ? "burnt" : variant;
        String identifier = getVillager(state).getClothes();
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return cached(identifier + v, clothes -> {
            Identifier id = Identifier.parse(getVillager(state).getClothes());

            Identifier idNew = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("normal", v));
            if (canUse(idNew)) {
                return idNew;
            }

            return id;
        });
    }
}
