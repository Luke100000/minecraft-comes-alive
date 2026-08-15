package net.conczin.mca.client.resources;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.FaceList;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SkinExporter {
    public static boolean export(VillagerEntityMCA villager) {
        return export(villager, null);
    }

    public static boolean export(VillagerEntityMCA villager, String customName) {
        try {
            try (NativeImage base = createSkin(villager, "normal")) {
                File exportDir = new File(Minecraft.getInstance().gameDirectory, "mca/exported_skins");
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }

                File destFile = getAvailableExportFile(exportDir, customName);
                base.writeToFile(destFile.toPath());

                // 6. Notify player with chat message
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    var message = Component.translatable("chat.mca.skin_export_success", "mca/exported_skins/" + destFile.getName())
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(" "))
                            .append(Component.translatable("chat.mca.open_image")
                                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, destFile.getAbsolutePath()))))
                            .append(Component.literal(" "))
                            .append(Component.translatable("chat.mca.open_folder")
                                    .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, exportDir.getAbsolutePath()))));

                    player.displayClientMessage(message, false);

                    // Close the MCA editor screen
                    Minecraft.getInstance().setScreen(null);
                }
                return true;
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to export villager skin", e);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("chat.mca.skin_export_failure", e.getMessage()).withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
    }

    public static NativeImage createSkin(VillagerLike<?> villager, String clothesVariant) {
        NativeImage base = new NativeImage(64, 64, true);
        composite(base, getSkin(villager), getSkinColor(villager));
        compositeFace(base, getFace(villager), villager);
        composite(base, getClothes(villager, clothesVariant), 0xFFFFFFFF);
        compositeHair(base, villager);
        return base;
    }

    public static ResourceLocation getSkin(VillagerLike<?> villager) {
        if (!MCA.isBlankString(villager.getSkin())) {
            return new ResourceLocation(villager.getSkin());
        }
        int skin = (int) Math.min(4, Math.max(0, villager.getGenetics().getGene(Genetics.SKIN) * 5));
        return new ResourceLocation("mca", "skins/skin/" + villager.getGenetics().getGender().getDataName() + "/" + skin + ".png");
    }

    public static int getSkinColor(VillagerLike<?> villager) {
        int skinDye = villager.getSkinDye();
        if (skinDye != 0xFF000000) {
            return skinDye;
        }
        float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        return rgbToArgb(ColorPalette.SKIN.getColor(
                villager.getGenetics().getGene(Genetics.MELANIN) * albinism,
                villager.getGenetics().getGene(Genetics.HEMOGLOBIN) * albinism,
                villager.getInfectionProgress()
        ));
    }

    public static ResourceLocation getFace(VillagerLike<?> villager) {
        Gender gender = villager.getGenetics().getGender();
        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = (int) Math.min(21, Math.max(0, villager.getGenetics().getGene(Genetics.FACE) * 22));
            return new ResourceLocation("mca", "skins/face/normal/" + gender.getDataName() + "/" + index + ".png");
        }
        return list.pick("normal", villager.getGenetics().getGene(Genetics.FACE));
    }

    public static ResourceLocation getClothes(VillagerLike<?> villager) {
        return getClothes(villager, "normal");
    }

    public static ResourceLocation getClothes(VillagerLike<?> villager, String variant) {
        String identifier = villager.getClothes();
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        ResourceLocation id = new ResourceLocation(identifier);
        ResourceLocation variantId = new ResourceLocation(id.getNamespace(), id.getPath().replace("normal", variant));
        return Minecraft.getInstance().getResourceManager().getResource(variantId).isPresent() ? variantId : id;
    }

    public static int getHairColor(VillagerLike<?> villager) {
        if (villager.getTraits().hasTrait(Traits.RAINBOW)) {
            return getRainbow(villager, 0);
        }

        if (villager.hasHairDye()) {
            return villager.getHairDye();
        }
        float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        return rgbToArgb(ColorPalette.HAIR.getColor(
                villager.getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                villager.getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                0
        ));
    }

    private static ResourceLocation getLibraryOrResourceIdentifier(String identifier) {
        return identifier.startsWith("immersive_library:")
                ? SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)))
                : new ResourceLocation(identifier);
    }

    private static ResourceLocation getOverlayIdentifier(String identifier) {
        if (identifier.startsWith("immersive_library:") || !identifier.endsWith(".png")) {
            return null;
        }
        ResourceLocation id = new ResourceLocation(identifier);
        ResourceLocation overlay = new ResourceLocation(id.getNamespace(), id.getPath().replace(".png", "_overlay.png"));
        return Minecraft.getInstance().getResourceManager().getResource(overlay).isPresent() ? overlay : null;
    }

    private static void compositeHair(NativeImage base, VillagerLike<?> villager) {
        int hairColor = getHairColor(villager);
        boolean renderedLayeredHair = false;
        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            String identifier = villager.getLayeredHair(category);
            if (MCA.isBlankString(identifier)) {
                continue;
            }
            renderedLayeredHair = true;
            compositeHairLayer(base, identifier, hairColor);
        }

        if (!renderedLayeredHair && !MCA.isBlankString(villager.getHair())) {
            compositeHairLayer(base, villager.getHair(), hairColor);
        }
    }

    private static void compositeHairLayer(NativeImage base, String identifier, int hairColor) {
        composite(base, getLibraryOrResourceIdentifier(identifier), hairColor);
        composite(base, getOverlayIdentifier(identifier), 0xFFFFFFFF);
    }

    public static NativeImage loadTexture(ResourceLocation id) {
        if (id.getNamespace().equals("immersive_library")) {
            try {
                int contentId = Integer.parseInt(id.getPath());
                File file = new File("immersive_library/" + contentId + ".png");
                if (file.exists()) {
                    try (InputStream stream = new FileInputStream(file)) {
                        return NativeImage.read(stream);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    return NativeImage.read(stream);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public static void composite(NativeImage base, ResourceLocation layerId, int tintColor) {
        if (layerId == null) return;
        NativeImage overlay = loadTexture(layerId);
        if (overlay == null) return;

        try {
            int width = Math.min(base.getWidth(), overlay.getWidth());
            int height = Math.min(base.getHeight(), overlay.getHeight());

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int overPixel = overlay.getPixelRGBA(x, y);
                    compositePixel(base, x, y, overPixel, tintColor);
                }
            }
        } finally {
            overlay.close();
        }
    }

    public static void compositeFace(NativeImage base, ResourceLocation faceId, VillagerLike<?> villager) {
        NativeImage face = loadTexture(faceId);
        if (face == null) {
            return;
        }

        try {
            EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(face);
            int splitX = bounds.minX() + bounds.width() / 2;
            compositeEyeLayer(base, face, EyeTextureLayers.Layer.SCLERA, EyeTextureLayers.Side.FULL, splitX, 0xFFFFFFFF);
            compositeEyeLayer(base, face, EyeTextureLayers.Layer.DETAILS, EyeTextureLayers.Side.FULL, splitX, EyeTextureLayers.DETAILS_TINT);
            if (villager.getTraits().hasTrait(Traits.HETEROCHROMIA)) {
                compositeEyeLayer(base, face, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.LEFT, splitX, getEyeColor(villager, true));
                compositeEyeLayer(base, face, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.RIGHT, splitX, getEyeColor(villager, false));
            } else {
                compositeEyeLayer(base, face, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.FULL, splitX, getEyeColor(villager, false));
            }
        } finally {
            face.close();
        }
    }

    public static void compositeEyeLayer(NativeImage base, NativeImage face, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side, int splitX, int tintColor) {
        int width = Math.min(base.getWidth(), face.getWidth());
        int height = Math.min(base.getHeight(), face.getHeight());
        for (int x = 0; x < width; x++) {
            if (!EyeTextureLayers.isInSide(x, splitX, side)) {
                continue;
            }
            for (int y = 0; y < height; y++) {
                int pixel = face.getPixelRGBA(x, y); // ABGR
                int alpha = (pixel >> 24) & 0xFF;
                boolean includePixel = EyeTextureLayers.isPixelForLayer(
                        layer, alpha, pixel & 0xFF, (pixel >> 8) & 0xFF, (pixel >> 16) & 0xFF);
                if (!includePixel) {
                    continue;
                }
                compositePixel(base, x, y, pixel, tintColor);
            }
        }
    }

    public static int getEyeColor(VillagerLike<?> villager, boolean left) {
        int color;
        if (villager.getTraits().hasTrait(Traits.RAINBOW_EYES)) {
            int offset = left && villager.getTraits().hasTrait(Traits.HETEROCHROMIA) ? (25 * DyeColor.values().length) / 2 : 0;
            color = getRainbow(villager, offset);
        } else {
            color = EyeTextureLayers.getStaticEyeColor(villager, left);
        }

        return EyeTextureLayers.applyBrightness(color, villager.getGenetics().getGene(Genetics.EYE_BRIGHTNESS));
    }

    private static int getRainbow(VillagerLike<?> villager, int offset) {
        int ticks = Math.abs(villager.asEntity().tickCount) + offset;
        int block = ticks / 25 + villager.asEntity().getId();
        int count = DyeColor.values().length;
        int first = block % count;
        int second = (block + 1) % count;
        float mix = (float) (ticks % 25) / 25.0F;
        return FastColor.ARGB32.lerp(mix, rgbToArgb(Sheep.getColorArray(DyeColor.byId(first))), rgbToArgb(Sheep.getColorArray(DyeColor.byId(second))));
    }

    private static int rgbToArgb(float[] rgb) {
        return FastColor.ARGB32.color(
                255,
                Mth.clamp(Math.round(rgb[0] * 255.0f), 0, 255),
                Mth.clamp(Math.round(rgb[1] * 255.0f), 0, 255),
                Mth.clamp(Math.round(rgb[2] * 255.0f), 0, 255)
        );
    }

    public static void compositePixel(NativeImage base, int x, int y, int overPixel, int tintColor) {
        int tr = (tintColor >> 16) & 0xFF;
        int tg = (tintColor >> 8) & 0xFF;
        int tb = tintColor & 0xFF;
        int ta = (tintColor >> 24) & 0xFF;

        int overAlpha = (overPixel >> 24) & 0xFF;
        if (overAlpha == 0) return;

        int overR = ((overPixel & 0xFF) * tr) / 255;
        int overG = (((overPixel >> 8) & 0xFF) * tg) / 255;
        int overB = (((overPixel >> 16) & 0xFF) * tb) / 255;
        int overA = (overAlpha * ta) / 255;

        if (overA == 0) {
            return;
        } else if (overA == 255) {
            base.setPixelRGBA(x, y, 0xFF000000 | (overB << 16) | (overG << 8) | overR);
        } else {
            int basePixel = base.getPixelRGBA(x, y);
            int baseAlpha = (basePixel >> 24) & 0xFF;
            int baseR = basePixel & 0xFF;
            int baseG = (basePixel >> 8) & 0xFF;
            int baseB = (basePixel >> 16) & 0xFF;

            int outAlpha = overA + (baseAlpha * (255 - overA)) / 255;
            if (outAlpha > 0) {
                int outR = (overR * overA + baseR * baseAlpha * (255 - overA) / 255) / outAlpha;
                int outG = (overG * overA + baseG * baseAlpha * (255 - overA) / 255) / outAlpha;
                int outB = (overB * overA + baseB * baseAlpha * (255 - overA) / 255) / outAlpha;
                base.setPixelRGBA(x, y, (outAlpha << 24) | (outB << 16) | (outG << 8) | outR);
            }
        }
    }

    private static File getAvailableExportFile(File exportDir, String customName) {
        String fileName;
        if (MCA.isBlankString(customName)) {
            fileName = "Exported_Skin";
        } else {
            fileName = customName.replaceAll("[^a-zA-Z0-9_\\-]", "_") + "_skin";
        }

        File candidateFile = new File(exportDir, fileName + ".png");
        for (int suffix = 2; candidateFile.exists(); suffix++) {
            candidateFile = new File(exportDir, fileName + "_" + suffix + ".png");
        }
        return candidateFile;
    }
}
