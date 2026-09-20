package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class ClothingLayer<T extends LivingEntity> extends VillagerLayer<T> {
    private final String variant;

    public ClothingLayer(RenderLayerParent<T, PlayerModel<T>> renderer, VillagerOverlayModel<T> model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    protected void configureModel(T villager) {
        model.applyMorphology(MCAClient.resolveVillager(villager));
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        var villagerData = MCAClient.resolveVillager(villager);
        String v = villagerData.isBurned() ? "burnt" : variant;
        String identifier = villagerData.getClothes();
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return cached(identifier + v, clothes -> {
            ResourceLocation id = ResourceLocation.parse(identifier);

            ResourceLocation idNew = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("normal", v));
            if (canUse(idNew)) {
                return idNew;
            }

            return id;
        });
    }
}
