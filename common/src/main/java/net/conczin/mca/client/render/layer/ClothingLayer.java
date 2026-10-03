package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.render.DynamicSkinCache;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class ClothingLayer<T extends LivingEntity> extends VillagerLayer<T, VillagerOverlayModel<T>> {
    private final String variant;
    private final boolean slimTexture;

    public ClothingLayer(RenderLayerParent<T, PlayerModel<T>> renderer, VillagerOverlayModel<T> model, String variant) {
        this(renderer, model, variant, false);
    }

    public ClothingLayer(RenderLayerParent<T, PlayerModel<T>> renderer, VillagerOverlayModel<T> model, String variant, boolean slimTexture) {
        super(renderer, model);
        this.variant = variant;
        this.slimTexture = slimTexture;
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (villager instanceof VillagerLike<?> data && data.isSlim() != slimTexture) {
            return;
        }
        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
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
            return remapForSlim(SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18))));
        }
        ResourceLocation texture = cached(identifier + v, clothes -> {
            ResourceLocation id = ResourceLocation.parse(identifier);

            ResourceLocation idNew = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("normal", v));
            if (canUse(idNew)) {
                return idNew;
            }

            return id;
        });
        return remapForSlim(texture);
    }

    private ResourceLocation remapForSlim(ResourceLocation texture) {
        return slimTexture ? DynamicSkinCache.getOrCreateSlimTexture(texture) : texture;
    }
}
