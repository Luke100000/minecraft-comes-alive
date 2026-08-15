package net.conczin.mca.client.render;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.util.MaxSizeHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds complete MCA player/villager skins on demand for integrations that need a single vanilla-style texture,
 * such as JourneyMap entity icons.
 */
public final class DynamicSkinCache {
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final String IMMERSIVE_LIBRARY_PREFIX = "immersive_library:";
    private static final ResourceLocation EMPTY_LIBRARY_TEXTURE = MCA.locate("skins/empty.png");

    private static final Set<SkinKey> INCOMPLETE_CACHE = new HashSet<>();
    private static final Set<SkinKey> INCOMPLETE_FACE_CACHE = new HashSet<>();
    private static final Map<SkinKey, ResourceLocation> CACHE = new MaxSizeHashMap<>(128) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SkinKey, ResourceLocation> eldest) {
            boolean remove = super.removeEldestEntry(eldest);
            if (remove) {
                releaseDynamicTexture(eldest.getValue());
                INCOMPLETE_CACHE.remove(eldest.getKey());
            }
            return remove;
        }
    };
    private static final Map<SkinKey, ResourceLocation> FACE_CACHE = new MaxSizeHashMap<>(128) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SkinKey, ResourceLocation> eldest) {
            boolean remove = super.removeEldestEntry(eldest);
            if (remove) {
                releaseDynamicTexture(eldest.getValue());
                INCOMPLETE_FACE_CACHE.remove(eldest.getKey());
            }
            return remove;
        }
    };

    private DynamicSkinCache() {
    }

    public static void clear() {
        CACHE.values().forEach(DynamicSkinCache::releaseDynamicTexture);
        FACE_CACHE.values().forEach(DynamicSkinCache::releaseDynamicTexture);
        CACHE.clear();
        FACE_CACHE.clear();
        INCOMPLETE_CACHE.clear();
        INCOMPLETE_FACE_CACHE.clear();
    }

    public static ResourceLocation getOrCreateStitchedSkin(Entity entity) {
        if (!(entity instanceof VillagerLike<?> villager)) {
            return STEVE;
        }

        SkinKey key = SkinKey.from(entity, villager);
        boolean missingAssets = isMissingImmersiveLibraryAssets(villager);
        ResourceLocation cachedId = CACHE.get(key);
        if (cachedId != null) {
            if (!missingAssets && INCOMPLETE_CACHE.remove(key)) {
                releaseDynamicTexture(cachedId);
                CACHE.remove(key);
            } else {
                return cachedId;
            }
        }

        ResourceLocation generated = generateStitchedSkin(entity, villager, key);
        CACHE.put(key, generated);
        if (missingAssets) {
            INCOMPLETE_CACHE.add(key);
        } else {
            INCOMPLETE_CACHE.remove(key);
        }
        return generated;
    }

    public static ResourceLocation getOrCreateCroppedFace(Entity entity) {
        if (!(entity instanceof VillagerLike<?> villager)) {
            return null;
        }

        SkinKey key = SkinKey.from(entity, villager);
        boolean missingAssets = isMissingImmersiveLibraryAssets(villager);

        ResourceLocation cachedId = FACE_CACHE.get(key);
        if (cachedId != null) {
            if (!missingAssets && INCOMPLETE_FACE_CACHE.remove(key)) {
                releaseDynamicTexture(cachedId);
                FACE_CACHE.remove(key);
            } else {
                return cachedId;
            }
        }

        if (!Minecraft.getInstance().isSameThread()) {
            return null;
        }

        ResourceLocation generated = generateCroppedFace(entity, key);
        if (generated == null) {
            return null;
        }
        FACE_CACHE.put(key, generated);
        if (missingAssets) {
            INCOMPLETE_FACE_CACHE.add(key);
        } else {
            INCOMPLETE_FACE_CACHE.remove(key);
        }
        return generated;
    }

    private static ResourceLocation generateStitchedSkin(Entity entity, VillagerLike<?> villager, SkinKey key) {
        try {
            String clothesVariant = villager.isBurned() ? "burnt" : entity instanceof ZombieVillagerEntityMCA ? "zombie" : "normal";
            ResourceLocation id = MCA.locate("dynamic/stitched/" + key.id());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(SkinExporter.createSkin(villager, clothesVariant)));
            return id;
        } catch (Exception exception) {
            MCA.LOGGER.error("Failed to generate dynamic MCA skin texture", exception);
            return STEVE;
        }
    }

    private static ResourceLocation generateCroppedFace(Entity entity, SkinKey key) {
        try {
            ResourceLocation stitchedId = getOrCreateStitchedSkin(entity);
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(stitchedId);
            if (!(texture instanceof DynamicTexture dynamicTexture) || dynamicTexture.getPixels() == null) {
                return null;
            }

            NativeImage base = dynamicTexture.getPixels();
            NativeImage face = new NativeImage(8, 8, true);
            try {
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        face.setPixelRGBA(x, y, base.getPixelRGBA(8 + x, 8 + y));
                    }
                }

                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        int overlayPixel = base.getPixelRGBA(40 + x, 8 + y);
                        SkinExporter.compositePixel(face, x, y, overlayPixel, 0xFFFFFFFF);
                    }
                }

                NativeImage scaled = new NativeImage(24, 24, true);
                boolean registered = false;
                try {
                    for (int x = 0; x < 24; x++) {
                        for (int y = 0; y < 24; y++) {
                            scaled.setPixelRGBA(x, y, face.getPixelRGBA(x / 3, y / 3));
                        }
                    }

                    ResourceLocation id = MCA.locate("dynamic/icon/" + key.id());
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(scaled));
                    registered = true;
                    return id;
                } finally {
                    if (!registered) {
                        scaled.close();
                    }
                }
            } finally {
                face.close();
            }
        } catch (Exception exception) {
            MCA.LOGGER.error("Failed to generate dynamic MCA face icon texture", exception);
            return null;
        }
    }

    private static void releaseDynamicTexture(ResourceLocation id) {
        if (id != null && id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/")) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    private static boolean isMissingImmersiveLibraryAssets(VillagerLike<?> villager) {
        if (isMissingImmersiveLibraryAsset(villager.getClothes()) || isMissingImmersiveLibraryAsset(villager.getHair())) {
            return true;
        }
        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            if (isMissingImmersiveLibraryAsset(villager.getLayeredHair(category))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMissingImmersiveLibraryAsset(String identifier) {
        Integer contentId = contentId(identifier);
        return contentId != null && EMPTY_LIBRARY_TEXTURE.equals(SkinCache.getTextureIdentifier(contentId));
    }

    private static Integer contentId(String identifier) {
        if (MCA.isBlankString(identifier) || !identifier.startsWith(IMMERSIVE_LIBRARY_PREFIX)) {
            return null;
        }
        try {
            return Integer.parseInt(identifier.substring(IMMERSIVE_LIBRARY_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record SkinKey(
            String gender,
            boolean albinism,
            boolean heterochromia,
            boolean rainbowEyes,
            boolean rainbowHair,
            float skinGene,
            float melaninGene,
            float hemoglobinGene,
            float faceGene,
            float eumelaninGene,
            float pheomelaninGene,
            String skin,
            String hair,
            String hairBase,
            String hairBangs,
            String hairBack,
            String hairFront,
            String hairExtra,
            int skinDye,
            int hairDye,
            int eyeDye,
            int eyeLeftDye,
            String clothes,
            boolean zombie,
            boolean burned,
            int infectionProgress
    ) {
        static SkinKey from(Entity entity, VillagerLike<?> villager) {
            return new SkinKey(
                    villager.getGenetics().getGender().getDataName(),
                    villager.getTraits().hasTrait(Traits.ALBINISM),
                    villager.getTraits().hasTrait(Traits.HETEROCHROMIA),
                    villager.getTraits().hasTrait(Traits.RAINBOW_EYES),
                    villager.getTraits().hasTrait(Traits.RAINBOW),
                    villager.getGenetics().getGene(Genetics.SKIN),
                    villager.getGenetics().getGene(Genetics.MELANIN),
                    villager.getGenetics().getGene(Genetics.HEMOGLOBIN),
                    villager.getGenetics().getGene(Genetics.FACE),
                    villager.getGenetics().getGene(Genetics.EUMELANIN),
                    villager.getGenetics().getGene(Genetics.PHEOMELANIN),
                    villager.getSkin(),
                    villager.getHair(),
                    villager.getHairBase(),
                    villager.getHairBangs(),
                    villager.getHairBack(),
                    villager.getHairFront(),
                    villager.getHairExtra(),
                    villager.getSkinDye(),
                    villager.getHairDye(),
                    villager.getEyeDye(),
                    villager.getEyeLeftDye(),
                    villager.getClothes(),
                    entity instanceof ZombieVillagerEntityMCA,
                    villager.isBurned(),
                    Math.round(villager.getInfectionProgress() * 100.0F)
            );
        }

        String id() {
            return UUID.nameUUIDFromBytes(toString().getBytes(StandardCharsets.UTF_8)).toString();
        }
    }
}
