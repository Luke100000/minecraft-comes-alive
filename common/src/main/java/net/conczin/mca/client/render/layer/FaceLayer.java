package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.resources.ClientAppearanceCatalog;
import net.conczin.mca.client.resources.EyeTintPixel;
import net.conczin.mca.client.resources.EyeTextureLayers;
import net.conczin.mca.client.resources.EyeToneRendering;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.resources.EyeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class FaceLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final Map<ResourceLocation, EyeLayerTextures> EYE_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private final String variant;

    public FaceLayer(RenderLayerParent<T, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        model.setAllVisible(false);
        model.head.visible = true;
        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public void renderFinal(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0);
        ResourceLocation skin = getSkin(villager);
        if (isBlinking(villager)) {
            ResourceLocation blink = getBlinkSkin();
            if (canUse(blink)) {
                renderModel(transform, provider, light, model, OPAQUE_WHITE, blink, overlay, visible, glowing);
            }
            return;
        }

        if (canUse(skin)) {
            EyeDefinition definition = ClientAppearanceCatalog.eyeDefinition(skin);
            EyeLayerTextures layers = getOrGenerateEyeLayers(skin, definition);
            if (layers.modern()) {
                renderModern(transform, provider, light, villager, tickDelta, visible, glowing, overlay, definition, layers);
            } else {
                renderLegacy(transform, provider, light, villager, tickDelta, visible, glowing, overlay, layers);
            }
        }

        ResourceLocation extraOverlay = getOverlay(villager);
        if (!Objects.equals(skin, extraOverlay) && canUse(extraOverlay)) {
            renderModel(transform, provider, light, model, OPAQUE_WHITE, extraOverlay, overlay, visible, glowing);
        }
    }

    private void renderLegacy(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, boolean visible, boolean glowing, int overlay, EyeLayerTextures layers) {
        renderEyeModel(transform, provider, light, OPAQUE_WHITE, layers.fixed(), overlay, visible, glowing);
        renderEyeModel(transform, provider, light, EyeTextureLayers.DETAILS_TINT, layers.details(), overlay, visible, glowing);
        boolean heterochromia = getVillager(villager).getTraits().hasTrait(Traits.HETEROCHROMIA);
        if (heterochromia) {
            renderEyeModel(transform, provider, light, legacyEyeColor(villager, tickDelta, true), layers.primary(EyeTextureLayers.Side.LEFT), overlay, visible, glowing);
            renderEyeModel(transform, provider, light, legacyEyeColor(villager, tickDelta, false), layers.primary(EyeTextureLayers.Side.RIGHT), overlay, visible, glowing);
        } else {
            renderEyeModel(transform, provider, light, legacyEyeColor(villager, tickDelta, false), layers.primary(EyeTextureLayers.Side.FULL), overlay, visible, glowing);
        }
    }

    private void renderModern(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, boolean visible, boolean glowing, int overlay, EyeDefinition definition, EyeLayerTextures layers) {
        boolean heterochromia = getVillager(villager).getTraits().hasTrait(Traits.HETEROCHROMIA);
        if (heterochromia) {
            renderTones(transform, provider, light, visible, glowing, overlay, layers, tones(villager, tickDelta, true, definition), EyeTextureLayers.Side.LEFT);
            renderTones(transform, provider, light, visible, glowing, overlay, layers, tones(villager, tickDelta, false, definition), EyeTextureLayers.Side.RIGHT);
        } else {
            renderTones(transform, provider, light, visible, glowing, overlay, layers, tones(villager, tickDelta, false, definition), EyeTextureLayers.Side.FULL);
        }
        renderEyeModel(transform, provider, light, OPAQUE_WHITE, layers.fixed(), overlay, visible, glowing);
    }

    private void renderTones(PoseStack transform, MultiBufferSource provider, int light, boolean visible, boolean glowing, int overlay, EyeLayerTextures layers, EyeDefinition.Tones tones, EyeTextureLayers.Side side) {
        renderEyeModel(transform, provider, light, tones.shadow(), layers.shadow(side), overlay, visible, glowing);
        renderEyeModel(transform, provider, light, tones.primary(), layers.primary(side), overlay, visible, glowing);
        renderEyeModel(transform, provider, light, tones.highlight(), layers.highlight(side), overlay, visible, glowing);
    }

    private void renderEyeModel(PoseStack transform, MultiBufferSource provider, int light, int color, ResourceLocation texture, int overlay, boolean visible, boolean glowing) {
        if (texture != null) {
            renderModel(transform, provider, light, model, color, texture, overlay, visible, glowing);
        }
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        VillagerLike<?> villagerLike = getVillager(villager);
        return ClientAppearanceCatalog.resolveEye(variant, villagerLike.getEyeTexture());
    }

    private ResourceLocation getBlinkSkin() {
        return cached("skins/face/normal/blink.png", MCA::locate);
    }

    @Override
    protected ResourceLocation getOverlay(T villager) {
        return null;
    }

    public static void clearGeneratedEyeTextureCache() {
        var textures = Minecraft.getInstance().getTextureManager();
        EYE_TEXTURE_CACHE.values().stream().flatMap(EyeLayerTextures::ids).distinct().forEach(id -> {
            if (id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/eye/")) {
                textures.release(id);
            }
        });
        EYE_TEXTURE_CACHE.clear();
    }

    private EyeLayerTextures getOrGenerateEyeLayers(ResourceLocation id, EyeDefinition definition) {
        return EYE_TEXTURE_CACHE.computeIfAbsent(id, key -> generateEyeLayers(key, definition));
    }

    private EyeLayerTextures generateEyeLayers(ResourceLocation id, EyeDefinition definition) {
        try {
            return generateSingle(id, definition);
        } catch (Exception exception) {
            MCA.LOGGER.warn("Failed to generate eye texture layers for {}", id, exception);
            return EyeLayerTextures.invalid();
        }
    }

    private EyeLayerTextures generateSingle(ResourceLocation id, EyeDefinition definition) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow(() -> new IllegalStateException("Missing eye texture " + id));
        try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
            EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(image);
            return definition.fixedColor() || EyeTextureLayers.hasExplicitTintMarker(image)
                    ? generateModern(id, image, bounds, definition.fixedColor())
                    : generateLegacy(id, image, bounds);
        }
    }

    private EyeLayerTextures generateModern(ResourceLocation id, NativeImage mask, EyeTextureLayers.Bounds bounds, boolean fixedColor) {
        List<NativeImage> images = new ArrayList<>();
        List<ResourceLocation> registered = new ArrayList<>();
        try {
            int width = mask.getWidth(), height = mask.getHeight(), splitX = bounds.minX() + bounds.width() / 2;
            NativeImage fixed = image(images, width, height);
            NativeImage[] full = {image(images, width, height), image(images, width, height), image(images, width, height)};
            NativeImage[] left = {image(images, width, height), image(images, width, height), image(images, width, height)};
            NativeImage[] right = {image(images, width, height), image(images, width, height), image(images, width, height)};
            for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
                int pixel = mask.getPixelRGBA(x, y), alpha = FastColor.ABGR32.alpha(pixel);
                if (alpha == 0) {
                    continue;
                }
                if (fixedColor || !EyeTintPixel.isIrisMarker(alpha)) {
                    fixed.setPixelRGBA(x, y, pixel);
                    continue;
                }
                EyeTintPixel.Mask decoded = EyeTintPixel.decodeMarkedMask(pixel);
                int tone = decoded.tone().ordinal(), neutral = EyeToneRendering.neutralMaskPixel(decoded);
                full[tone].setPixelRGBA(x, y, neutral);
                (x >= splitX ? left[tone] : right[tone]).setPixelRGBA(x, y, neutral);
            }
            ResourceLocation fixedId = register(id, "fixed", EyeTextureLayers.Side.FULL, fixed, images, registered);
            return new EyeLayerTextures(true,
                    fixedId,
                    register(id, "shadow", EyeTextureLayers.Side.FULL, full[0], images, registered), register(id, "primary", EyeTextureLayers.Side.FULL, full[1], images, registered), register(id, "highlight", EyeTextureLayers.Side.FULL, full[2], images, registered),
                    register(id, "shadow", EyeTextureLayers.Side.LEFT, left[0], images, registered), register(id, "primary", EyeTextureLayers.Side.LEFT, left[1], images, registered), register(id, "highlight", EyeTextureLayers.Side.LEFT, left[2], images, registered),
                    register(id, "shadow", EyeTextureLayers.Side.RIGHT, right[0], images, registered), register(id, "primary", EyeTextureLayers.Side.RIGHT, right[1], images, registered), register(id, "highlight", EyeTextureLayers.Side.RIGHT, right[2], images, registered), null, null, null, null);
        } catch (RuntimeException exception) {
            registered.forEach(Minecraft.getInstance().getTextureManager()::release);
            throw exception;
        } finally {
            images.forEach(NativeImage::close);
        }
    }

    private EyeLayerTextures generateLegacy(ResourceLocation id, NativeImage image, EyeTextureLayers.Bounds bounds) {
        List<NativeImage> images = new ArrayList<>();
        List<ResourceLocation> registered = new ArrayList<>();
        try {
            int width = image.getWidth(), height = image.getHeight(), splitX = bounds.minX() + bounds.width() / 2;
            NativeImage sclera = image(images, width, height), details = image(images, width, height), full = image(images, width, height), left = image(images, width, height), right = image(images, width, height);
            for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
                int pixel = image.getPixelRGBA(x, y); EyeTextureLayers.Layer layer = EyeTextureLayers.layerForPixel(pixel);
                if (layer == null) continue;
                switch (layer) {
                    case SCLERA -> sclera.setPixelRGBA(x, y, pixel);
                    case DETAILS -> details.setPixelRGBA(x, y, pixel);
                    case IRIS -> { full.setPixelRGBA(x, y, pixel); (x >= splitX ? left : right).setPixelRGBA(x, y, pixel); }
                }
            }
            return new EyeLayerTextures(false, register(id, "sclera", EyeTextureLayers.Side.FULL, sclera, images, registered), null, null, null, null, null, null, null, null, null,
                    register(id, "details", EyeTextureLayers.Side.FULL, details, images, registered), register(id, "iris", EyeTextureLayers.Side.FULL, full, images, registered), register(id, "iris", EyeTextureLayers.Side.LEFT, left, images, registered), register(id, "iris", EyeTextureLayers.Side.RIGHT, right, images, registered));
        } catch (RuntimeException exception) {
            registered.forEach(Minecraft.getInstance().getTextureManager()::release);
            throw exception;
        } finally {
            images.forEach(NativeImage::close);
        }
    }

    private static NativeImage image(List<NativeImage> images, int width, int height) {
        NativeImage image = new NativeImage(width, height, true);
        images.add(image);
        return image;
    }

    private static ResourceLocation register(ResourceLocation original, String material, EyeTextureLayers.Side side, NativeImage image, List<NativeImage> pending, List<ResourceLocation> registered) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MCA.MOD_ID, "dynamic/eye/" + side.name().toLowerCase(Locale.ROOT) + "/" + material + "/" + original.getNamespace() + "_" + original.getPath().replace("/", "_"));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        pending.remove(image);
        registered.add(id);
        return id;
    }

    private int baseEyeColor(T villager, float tickDelta, boolean left) {
        return EyeTextureLayers.getBaseEyeColor(getVillager(villager), left, tickDelta);
    }

    private int legacyEyeColor(T villager, float tickDelta, boolean left) {
        return EyeToneRendering.legacyColor(baseEyeColor(villager, tickDelta, left), getVillager(villager).getGenetics().getGene(Genetics.EYE_BRIGHTNESS));
    }

    private EyeDefinition.Tones tones(T villager, float tickDelta, boolean left, EyeDefinition definition) {
        return EyeToneRendering.resolve(definition, baseEyeColor(villager, tickDelta, left), getVillager(villager).getGenetics().getGene(Genetics.EYE_BRIGHTNESS));
    }

    private boolean isBlinking(T villager) {
        int time = villager.tickCount / 2 + (int) (getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
        return time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDeadOrDying();
    }

    private VillagerLike<?> getVillager(T villager) {
        return CommonVillagerModel.getVillager(villager);
    }

    private record EyeLayerTextures(boolean modern, ResourceLocation fixed, ResourceLocation shadowFull, ResourceLocation primaryFull, ResourceLocation highlightFull, ResourceLocation shadowLeft, ResourceLocation primaryLeft, ResourceLocation highlightLeft, ResourceLocation shadowRight, ResourceLocation primaryRight, ResourceLocation highlightRight, ResourceLocation details, ResourceLocation irisFull, ResourceLocation irisLeft, ResourceLocation irisRight) {
        ResourceLocation shadow(EyeTextureLayers.Side side) {
            return side(side, shadowFull, shadowLeft, shadowRight);
        }

        ResourceLocation primary(EyeTextureLayers.Side side) {
            return side(side, primaryFull, primaryLeft, primaryRight);
        }

        ResourceLocation highlight(EyeTextureLayers.Side side) {
            return side(side, highlightFull, highlightLeft, highlightRight);
        }

        Stream<ResourceLocation> ids() {
            return Stream.of(fixed, shadowFull, primaryFull, highlightFull, shadowLeft, primaryLeft, highlightLeft, shadowRight, primaryRight, highlightRight, details, irisFull, irisLeft, irisRight).filter(Objects::nonNull);
        }

        static EyeLayerTextures invalid() {
            return new EyeLayerTextures(true, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        private static ResourceLocation side(EyeTextureLayers.Side side, ResourceLocation full, ResourceLocation left, ResourceLocation right) {
            return switch (side) {
                case FULL -> full;
                case LEFT -> left;
                case RIGHT -> right;
            };
        }
    }
}
