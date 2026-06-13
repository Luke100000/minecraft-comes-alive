package net.mca.client.render.layer;

import com.google.common.collect.Maps;
import net.mca.MCA;
import net.mca.MCAClient;
import net.mca.client.model.PlayerEntityExtendedModel;
import net.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static net.mca.client.model.CommonVillagerModel.getVillager;

public abstract class VillagerLayer<T extends LivingEntity, M extends BipedEntityModel<T>> extends FeatureRenderer<T, M> {
    private static final float[] DEFAULT_COLOR = new float[]{1, 1, 1};

    private static final Map<String, Identifier> TEXTURE_CACHE = Maps.newHashMap();
    private static final Map<Identifier, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();

    static {
        // the temp image is used for temporary canvases and definitely exists
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    public final M model;

    public VillagerLayer(FeatureRendererContext<T, M> renderer, M model) {
        super(renderer);
        this.model = model;
    }

    @Nullable
    public Identifier getSkin(T villager) {
        return null;
    }

    @Nullable
    protected Identifier getOverlay(T villager) {
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
    public void render(MatrixStack transform, VertexConsumerProvider provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean visible = !villager.isInvisible();
        boolean glowing = client.hasOutline(villager);

        if (getVillager(villager).hasCustomSkin()) {
            return;
        }

        if (villager instanceof PlayerEntity && !MCAClient.useVillagerRenderer(villager.getUuid())) {
            return;
        }

        //primarily restores compatibility with Armourers Workshop
        if (model instanceof VillagerEntityModelMCA layer) {
            layer.copyVisibility(getContextModel());
        }
        if (model instanceof PlayerEntityExtendedModel layer) {
            layer.copyVisibility(getContextModel());
        }

        //copy the animation to this layers model
        getContextModel().copyBipedStateTo(model);

        renderFinal(transform, provider, light, villager, tickDelta, visible, glowing);
    }

    public void renderFinal(MatrixStack transform, VertexConsumerProvider provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int tint = LivingEntityRenderer.getOverlay(villager, 0);

        Identifier skin = getSkin(villager);
        if (canUse(skin)) {
            float[] color = getColor(villager, tickDelta);
            renderModel(transform, provider, light, model, color[0], color[1], color[2], skin, tint, visible, glowing);
        }

        Identifier overlay = getOverlay(villager);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(transform, provider, light, model, 1, 1, 1, overlay, tint, visible, glowing);
        }
    }

    @Nullable
    protected RenderLayer getRenderLayer(Identifier texture, boolean showBody, boolean translucent, boolean showOutline) {
        if (translucent) {
            return RenderLayer.getItemEntityTranslucentCull(texture);
        } else if (showBody) {
            return this.model.getLayer(texture);
        } else {
            return showOutline ? RenderLayer.getOutline(texture) : null;
        }
    }

    private void renderModel(MatrixStack transform, VertexConsumerProvider provider, int light, M model, float r, float g, float b, Identifier texture, int overlay, boolean visible, boolean glowing) {
        RenderLayer layer = getRenderLayer(texture, visible, isTranslucent(), glowing);
        if (layer == null) return;
        VertexConsumer buffer = provider.getBuffer(layer);
        model.render(transform, buffer, light, overlay, r, g, b, 1);
    }

    public final boolean canUse(Identifier texture) {
        return TEXTURE_EXIST_CACHE.computeIfAbsent(texture, s -> {
            if (texture != null && texture.getNamespace().equals("immersive_library")) {
                return true;
            }
            return texture != null && MinecraftClient.getInstance().getResourceManager().getResource(texture).isPresent();
        });
    }

    @Nullable
    protected final Identifier cached(String name, Function<String, Identifier> supplier) {
        return TEXTURE_CACHE.computeIfAbsent(name, s -> {
            try {
                return supplier.apply(s);
            } catch (InvalidIdentifierException ignored) {
                return null;
            }
        });
    }
}
