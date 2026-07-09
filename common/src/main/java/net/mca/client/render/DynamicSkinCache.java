package net.mca.client.render;

import net.mca.MCA;
import net.mca.client.gui.immersive_library.SkinCache;
import net.mca.client.resources.SkinExporter;
import net.mca.entity.VillagerLike;
import net.mca.entity.ZombieVillagerEntityMCA;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.mca.resources.data.skin.LayeredHair;
import net.mca.util.MaxSizeHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

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
    private static final Identifier STEVE = new Identifier("textures/entity/steve.png");
    private static final String IMMERSIVE_LIBRARY_PREFIX = "immersive_library:";
    private static final Identifier EMPTY_LIBRARY_TEXTURE = MCA.locate("skins/empty.png");

    private static final Set<SkinKey> INCOMPLETE_CACHE = new HashSet<>();
    private static final Set<SkinKey> INCOMPLETE_FACE_CACHE = new HashSet<>();
    private static final Map<SkinKey, Identifier> CACHE = new MaxSizeHashMap<>(128) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SkinKey, Identifier> eldest) {
            boolean remove = super.removeEldestEntry(eldest);
            if (remove) {
                releaseDynamicTexture(eldest.getValue());
                INCOMPLETE_CACHE.remove(eldest.getKey());
            }
            return remove;
        }
    };
    private static final Map<SkinKey, Identifier> FACE_CACHE = new MaxSizeHashMap<>(128) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SkinKey, Identifier> eldest) {
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

    public static Identifier getOrCreateStitchedSkin(Entity entity) {
        if (!(entity instanceof VillagerLike<?> villager)) {
            return STEVE;
        }

        SkinKey key = SkinKey.from(entity, villager);
        boolean missingAssets = isMissingImmersiveLibraryAssets(villager);
        Identifier cachedId = CACHE.get(key);
        if (cachedId != null) {
            if (!missingAssets && INCOMPLETE_CACHE.remove(key)) {
                releaseDynamicTexture(cachedId);
                CACHE.remove(key);
            } else {
                return cachedId;
            }
        }

        Identifier generated = generateStitchedSkin(entity, villager, key);
        CACHE.put(key, generated);
        if (missingAssets) {
            INCOMPLETE_CACHE.add(key);
        } else {
            INCOMPLETE_CACHE.remove(key);
        }
        return generated;
    }

    public static Identifier getOrCreateCroppedFace(Entity entity) {
        if (!(entity instanceof VillagerLike<?> villager)) {
            return null;
        }

        SkinKey key = SkinKey.from(entity, villager);
        boolean missingAssets = isMissingImmersiveLibraryAssets(villager);

        Identifier cachedId = FACE_CACHE.get(key);
        if (cachedId != null) {
            if (!missingAssets && INCOMPLETE_FACE_CACHE.remove(key)) {
                releaseDynamicTexture(cachedId);
                FACE_CACHE.remove(key);
            } else {
                return cachedId;
            }
        }

        if (!MinecraftClient.getInstance().isOnThread()) {
            return null;
        }

        Identifier generated = generateCroppedFace(entity, key);
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

    private static Identifier generateStitchedSkin(Entity entity, VillagerLike<?> villager, SkinKey key) {
        try {
            String clothesVariant = villager.isBurned() ? "burnt" : entity instanceof ZombieVillagerEntityMCA ? "zombie" : "normal";
            Identifier id = MCA.locate("dynamic/stitched/" + key.id());
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(SkinExporter.createSkin(villager, clothesVariant)));
            return id;
        } catch (Exception exception) {
            MCA.LOGGER.error("Failed to generate dynamic MCA skin texture", exception);
            return STEVE;
        }
    }

    private static Identifier generateCroppedFace(Entity entity, SkinKey key) {
        try {
            Identifier stitchedId = getOrCreateStitchedSkin(entity);
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(stitchedId);
            if (!(texture instanceof NativeImageBackedTexture dynamicTexture) || dynamicTexture.getImage() == null) {
                return null;
            }

            NativeImage base = dynamicTexture.getImage();
            NativeImage face = new NativeImage(8, 8, true);
            try {
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        face.setColor(x, y, base.getColor(8 + x, 8 + y));
                    }
                }

                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        int overlayPixel = base.getColor(40 + x, 8 + y);
                        SkinExporter.compositePixel(face, x, y, overlayPixel, 0xFFFFFFFF);
                    }
                }

                NativeImage scaled = new NativeImage(24, 24, true);
                boolean registered = false;
                try {
                    for (int x = 0; x < 24; x++) {
                        for (int y = 0; y < 24; y++) {
                            scaled.setColor(x, y, face.getColor(x / 3, y / 3));
                        }
                    }

                    Identifier id = MCA.locate("dynamic/icon/" + key.id());
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(scaled));
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

    private static void releaseDynamicTexture(Identifier id) {
        if (id != null && id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/")) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
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
