package net.conczin.mca.client.render.layer;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.VillagerLayerModel;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class VillagerLayer<
        T extends LivingEntity,
        M extends EntityModel<T> & VillagerLayerModel<T>
        > extends RenderLayer<T, PlayerModel<T>> {
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = Maps.newHashMap();
    private static final Map<ResourceLocation, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();

    static {
        // the temp image is used for temporary canvases and definitely exists
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    public final M model;

    public VillagerLayer(RenderLayerParent<T, PlayerModel<T>> renderer, M model) {
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

    public int getColor(T villager, float tickDelta) {
        return 0xFFFFFFFF;
    }

    protected boolean isTranslucent() {
        return false;
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (villager instanceof Player && !MCAClient.useVillagerRenderer(villager.getUUID())) {
            return;
        }

        model.copyFrom(getParentModel());
        configureModel(villager);

        Minecraft client = Minecraft.getInstance();
        boolean visible = !villager.isInvisible();
        boolean translucent = !visible && client.player != null && !villager.isInvisibleTo(client.player);
        boolean glowing = client.shouldEntityAppearGlowing(villager);
        renderFinal(transform, provider, light, villager, tickDelta, new Visibility(visible, translucent, glowing));
    }

    protected record Visibility(boolean visible, boolean translucent, boolean glowing) {
    }

    protected void configureModel(T villager) {
    }

    public void renderFinal(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, Visibility visibility) {
        int tint = LivingEntityRenderer.getOverlayCoords(villager, 0);

        ResourceLocation skin = getSkin(villager);
        if (canUse(skin)) {
            int color = getColor(villager, tickDelta);
            renderModel(transform, provider, light, color, skin, tint, visibility);
        }

        ResourceLocation overlay = getOverlay(villager);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(transform, provider, light, 0xFFFFFFFF, overlay, tint, visibility);
        }
    }

    @Nullable
    protected RenderType getRenderLayer(ResourceLocation texture, Visibility visibility) {
        if (visibility.translucent()) {
            return RenderType.itemEntityTranslucentCull(texture);
        }
        if (visibility.visible()) {
            return isTranslucent() ? RenderType.itemEntityTranslucentCull(texture) : this.model.renderType(texture);
        }
        return visibility.glowing() ? RenderType.outline(texture) : null;
    }

    protected void renderModel(PoseStack transform, MultiBufferSource provider, int light, int color, ResourceLocation texture, int overlay, Visibility visibility) {
        RenderType layer = getRenderLayer(texture, visibility);
        if (layer == null) return;
        VertexConsumer buffer = provider.getBuffer(layer);
        int tint = visibility.translucent() ? FastColor.ARGB32.multiply(color, 0x26FFFFFF) : color;
        model.renderToBuffer(transform, buffer, light, overlay, tint);
    }

    /** Resource packs can add textures or replace variant textures after a reload. */
    public static void clearTextureCaches() {
        TEXTURE_CACHE.clear();
        TEXTURE_EXIST_CACHE.clear();
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    public static boolean canUse(ResourceLocation texture) {
        if (texture == null) {
            return false;
        }
        return TEXTURE_EXIST_CACHE.computeIfAbsent(texture, s -> {
            return s.getNamespace().equals("immersive_library")
                    || (s.getNamespace().equals(MCA.MOD_ID) && s.getPath().startsWith("dynamic/"))
                    || Minecraft.getInstance().getResourceManager().getResource(s).isPresent();
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
