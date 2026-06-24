package net.conczin.mca.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.util.ImmersiveLibraryIds;
import net.conczin.mca.util.MaxSizeHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class DynamicSkinCache {
    private record SkinKey(
        String genderDataName,
        boolean female,
        boolean albinism,
        boolean heterochromia,
        float skinGene,
        float melaninGene,
        float hemoglobinGene,
        float faceGene,
        float eumelaninGene,
        float pheomelaninGene,
        float eyeColorGene,
        String skin,
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
        int infectionProgressKey
    ) {
        public static SkinKey from(VillagerVisuals visuals) {
            int infProgress = Math.round(visuals.infectionProgress() * 100.0f);
            return new SkinKey(
                visuals.genderDataName(),
                visuals.female(),
                visuals.albinism(),
                visuals.heterochromia(),
                visuals.skinGene(),
                visuals.melaninGene(),
                visuals.hemoglobinGene(),
                visuals.faceGene(),
                visuals.eumelaninGene(),
                visuals.pheomelaninGene(),
                visuals.eyeColorGene(),
                visuals.skin(),
                visuals.hairBase(),
                visuals.hairBangs(),
                visuals.hairBack(),
                visuals.hairFront(),
                visuals.hairExtra(),
                visuals.skinDye(),
                visuals.hairDye(),
                visuals.eyeDye(),
                visuals.eyeLeftDye(),
                visuals.clothes(),
                infProgress
            );
        }

        public String getUniqueId() {
            return genderDataName + "_" +
                   female + "_" +
                   albinism + "_" +
                   heterochromia + "_" +
                   skinGene + "_" +
                   melaninGene + "_" +
                   hemoglobinGene + "_" +
                   faceGene + "_" +
                   eumelaninGene + "_" +
                   pheomelaninGene + "_" +
                   eyeColorGene + "_" +
                   skin + "_" +
                   hairBase + "_" +
                   hairBangs + "_" +
                   hairBack + "_" +
                   hairFront + "_" +
                   hairExtra + "_" +
                   skinDye + "_" +
                   hairDye + "_" +
                   eyeDye + "_" +
                   eyeLeftDye + "_" +
                   clothes + "_" +
                   String.format(java.util.Locale.ROOT, "%05d", infectionProgressKey);
        }
    }

    private static final Map<SkinKey, Identifier> CACHE = new MaxSizeHashMap<>(128) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SkinKey, Identifier> eldest) {
            boolean remove = super.removeEldestEntry(eldest);
            if (remove) {
                Identifier id = eldest.getValue();
                Minecraft.getInstance().getTextureManager().release(id);
            }
            return remove;
        }
    };

    private static final Map<SkinKey, Identifier> FACE_CACHE = new MaxSizeHashMap<>(128) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SkinKey, Identifier> eldest) {
            boolean remove = super.removeEldestEntry(eldest);
            if (remove) {
                Identifier id = eldest.getValue();
                Minecraft.getInstance().getTextureManager().release(id);
            }
            return remove;
        }
    };

    private static boolean isMissingImmersiveLibraryAssets(VillagerVisuals visuals) {
        String clothes = visuals.clothes();
        if (!MCA.isBlankString(clothes)) {
            var contentId = ImmersiveLibraryIds.contentId(clothes);
            if (contentId.isPresent() && !SkinCache.isLoaded(contentId.getAsInt())) {
                return true;
            }
        }
        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            String hair = visuals.layeredHair(category);
            if (!MCA.isBlankString(hair)) {
                var contentId = ImmersiveLibraryIds.contentId(hair);
                if (contentId.isPresent() && !SkinCache.isLoaded(contentId.getAsInt())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Identifier getOrCreateStitchedSkin(VillagerVisuals visuals) {
        SkinKey key = SkinKey.from(visuals);
        Identifier cachedId = CACHE.get(key);
        if (cachedId != null) {
            return cachedId;
        }

        Identifier newId = generateStitchedSkin(visuals, key);
        if (!isMissingImmersiveLibraryAssets(visuals)) {
            CACHE.put(key, newId);
        }
        return newId;
    }

    public static Identifier getOrCreateCroppedFace(VillagerVisuals visuals) {
        SkinKey key = SkinKey.from(visuals);
        Identifier cachedId = FACE_CACHE.get(key);
        if (cachedId != null) {
            return cachedId;
        }

        Identifier newId = generateCroppedFace(visuals, key);
        if (!isMissingImmersiveLibraryAssets(visuals)) {
            FACE_CACHE.put(key, newId);
        }
        return newId;
    }

    public static boolean isDynamicFaceIdentifier(Identifier id) {
        return "mca".equals(id.getNamespace()) && id.getPath().startsWith("dynamic/icon/");
    }

    public static boolean isCachedFaceIdentifier(Identifier id) {
        return FACE_CACHE.containsValue(id);
    }

    private static Identifier generateCroppedFace(VillagerVisuals visuals, SkinKey key) {
        try {
            Identifier stitchedId = getOrCreateStitchedSkin(visuals);
            net.minecraft.client.renderer.texture.AbstractTexture texture =
                Minecraft.getInstance().getTextureManager().getTexture(stitchedId);
            if (!(texture instanceof DynamicTexture dynamicTexture)) {
                return Identifier.parse("textures/entity/steve.png");
            }

            NativeImage base = dynamicTexture.getPixels();
            if (base == null) {
                return Identifier.parse("textures/entity/steve.png");
            }

            NativeImage face = new NativeImage(8, 8, true);
            try {
                // Create 8x8 base face image
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        face.setPixel(x, y, base.getPixel(8 + x, 8 + y));
                    }
                }

                // Composite overlay (hat/hair/accessories layer) from (40, 8) to (47, 15)
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        int overlayPixel = base.getPixel(40 + x, 8 + y);
                        SkinExporter.compositePixel(face, x, y, overlayPixel, 0xFFFFFFFF);
                    }
                }

                // JourneyMap player face icons use a 24x24 nearest-neighbor crop.
                NativeImage scaled = new NativeImage(24, 24, true);
                for (int x = 0; x < 24; x++) {
                    for (int y = 0; y < 24; y++) {
                        scaled.setPixel(x, y, face.getPixel(x / 3, y / 3));
                    }
                }

                Identifier newId = Identifier.fromNamespaceAndPath("mca", "dynamic/icon/" + keyId(key.getUniqueId()));
                Minecraft.getInstance().getTextureManager().register(newId, new DynamicTexture(newId::toString, scaled));
                return newId;
            } finally {
                face.close();
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to generate dynamic cropped face icon", e);
            return Identifier.parse("textures/entity/steve.png");
        }
    }

    private static Identifier generateStitchedSkin(VillagerVisuals visuals, SkinKey key) {
        try {
            NativeImage base = new NativeImage(64, 64, true);

            Identifier skinId = SkinExporter.getSkin(visuals);
            int skinColor = SkinExporter.getSkinColor(visuals);
            SkinExporter.composite(base, skinId, skinColor);

            Identifier faceId = SkinExporter.getFace(visuals);
            SkinExporter.compositeFace(base, faceId, visuals);

            String variant = "normal";
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(visuals.entityId());
                if (entity instanceof ZombieVillagerEntityMCA) {
                    variant = "zombie";
                }
            }

            Identifier clothesId = SkinExporter.getClothes(visuals, variant);
            if (clothesId != null) {
                SkinExporter.composite(base, clothesId, 0xFFFFFFFF);
            }

            int hairColor = SkinExporter.getHairColor(visuals);
            for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
                String identifier = visuals.layeredHair(category);
                if (!MCA.isBlankString(identifier)) {
                    SkinExporter.composite(base, SkinExporter.getLibraryOrResourceIdentifier(identifier), hairColor);
                    Identifier overlay = getResourceOverlayIdentifier(identifier);
                    if (overlay != null) {
                        SkinExporter.composite(base, overlay, 0xFFFFFFFF);
                    }
                }
            }

            Identifier newId = Identifier.fromNamespaceAndPath("mca", "dynamic/stitched/" + keyId(key.getUniqueId()));
            Minecraft.getInstance().getTextureManager().register(newId, new DynamicTexture(newId::toString, base));
            return newId;
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to generate dynamic stitched skin", e);
            return Identifier.parse("textures/entity/steve.png");
        }
    }

    private static Identifier getResourceOverlayIdentifier(String identifier) {
        if (ImmersiveLibraryIds.contentId(identifier).isPresent() || !identifier.endsWith(".png")) {
            return null;
        }
        Identifier id = Identifier.parse(identifier);
        Identifier overlay = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace(".png", "_overlay.png"));
        return Minecraft.getInstance().getResourceManager().getResource(overlay).isPresent() ? overlay : null;
    }

    private static String keyId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
