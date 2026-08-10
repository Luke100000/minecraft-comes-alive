package net.mca.client.render.layer;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mca.MCA;
import net.mca.MCAClient;
import net.mca.client.model.PlayerEntityExtendedModel;
import net.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static net.mca.client.model.CommonVillagerModel.getVillager;

public abstract class VillagerLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final float[] DEFAULT_COLOR = new float[]{1, 1, 1};

    private static final Map<String, ResourceLocation> TEXTURE_CACHE = Maps.newHashMap();
    private static final Map<ResourceLocation, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();

    static {
        // the temp image is used for temporary canvases and definitely exists
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    public final M model;

    public VillagerLayer(RenderLayerParent<T, M> renderer, M model) {
        super(renderer);
        this.model = model;
    }

    @Nullable
    public ResourceLocation getSkin(T villager) {
        return null;
    }

    @Nullable
    protected ResourceLocation getOverlay(T villager) {
        return null;
    }

    public float[] getColor(T villager, float tickDelta) {
        return DEFAULT_COLOR;
    }

    protected boolean isTranslucent() {
        return false;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        Minecraft client = Minecraft.getInstance();
        boolean visible = !villager.isInvisible();
        boolean glowing = client.shouldEntityAppearGlowing(villager);

        if (getVillager(villager).hasCustomSkin()) {
            return;
        }

        if (villager instanceof Player && !MCAClient.useVillagerRenderer(villager.getUUID())) {
            return;
        }

        //primarily restores compatibility with Armourers Workshop
        if (model instanceof VillagerEntityModelMCA layer) {
            layer.copyVisibility(getParentModel());
        }
        if (model instanceof PlayerEntityExtendedModel layer) {
            layer.copyVisibility(getParentModel());
        }

        //copy the animation to this layers model
        getParentModel().copyPropertiesTo(model);

        renderFinal(transform, provider, light, villager, tickDelta, visible, glowing);
    }

    public void renderFinal(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int tint = LivingEntityRenderer.getOverlayCoords(villager, 0);

        ResourceLocation skin = getSkin(villager);
        if (canUse(skin)) {
            float[] color = getColor(villager, tickDelta);
            renderModel(transform, provider, light, model, color[0], color[1], color[2], skin, tint, visible, glowing);
        }

        ResourceLocation overlay = getOverlay(villager);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(transform, provider, light, model, 1, 1, 1, overlay, tint, visible, glowing);
        }
    }

    @Nullable
    protected RenderType getRenderLayer(ResourceLocation texture, boolean showBody, boolean translucent, boolean showOutline) {
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(texture);
        } else if (showBody) {
            return this.model.renderType(texture);
        } else {
            return showOutline ? RenderType.outline(texture) : null;
        }
    }

    protected void renderModel(PoseStack transform, MultiBufferSource provider, int light, M model, float r, float g, float b, ResourceLocation texture, int overlay, boolean visible, boolean glowing) {
        RenderType layer = getRenderLayer(texture, visible, isTranslucent(), glowing);
        if (layer == null) return;
        VertexConsumer buffer = provider.getBuffer(layer);
        model.renderToBuffer(transform, buffer, light, overlay, r, g, b, 1);
    }

    public final boolean canUse(ResourceLocation texture) {
        return TEXTURE_EXIST_CACHE.computeIfAbsent(texture, s -> {
            if (texture != null && texture.getNamespace().equals("immersive_library")) {
                return true;
            }
            return texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
        });
    }

    @Nullable
    protected final ResourceLocation cached(String name, Function<String, ResourceLocation> supplier) {
        return TEXTURE_CACHE.computeIfAbsent(name, s -> {
            try {
                return supplier.apply(s);
            } catch (ResourceLocationException ignored) {
                return null;
            }
        });
    }
}
