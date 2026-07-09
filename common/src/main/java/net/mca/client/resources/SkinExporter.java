package net.mca.client.resources;

import net.mca.MCA;
import net.mca.client.gui.immersive_library.SkinCache;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.FaceList;
import net.mca.resources.data.skin.LayeredHair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

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
                File exportDir = new File(MinecraftClient.getInstance().runDirectory, "mca/exported_skins");
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }

                File destFile = getAvailableExportFile(exportDir, customName);
                base.writeTo(destFile.toPath());

                // 6. Notify player with chat message
                var player = MinecraftClient.getInstance().player;
                if (player != null) {
                    var message = Text.translatable("chat.mca.skin_export_success", "mca/exported_skins/" + destFile.getName())
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(" "))
                            .append(Text.translatable("chat.mca.open_image")
                                    .styled(style -> style.withColor(Formatting.GREEN)
                                            .withUnderline(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, destFile.getAbsolutePath()))))
                            .append(Text.literal(" "))
                            .append(Text.translatable("chat.mca.open_folder")
                                    .styled(style -> style.withColor(Formatting.GOLD)
                                            .withUnderline(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, exportDir.getAbsolutePath()))));

                    player.sendMessage(message, false);

                    // Close the MCA editor screen
                    MinecraftClient.getInstance().setScreen(null);
                }
                return true;
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to export villager skin", e);
            var player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendMessage(Text.translatable("chat.mca.skin_export_failure", e.getMessage()).formatted(Formatting.RED), false);
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

    public static Identifier getSkin(VillagerLike<?> villager) {
        if (!MCA.isBlankString(villager.getSkin())) {
            return new Identifier(villager.getSkin());
        }
        int skin = (int) Math.min(4, Math.max(0, villager.getGenetics().getGene(Genetics.SKIN) * 5));
        return new Identifier("mca", "skins/skin/" + villager.getGenetics().getGender().getDataName() + "/" + skin + ".png");
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

    public static Identifier getFace(VillagerLike<?> villager) {
        Gender gender = villager.getGenetics().getGender();
        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = (int) Math.min(21, Math.max(0, villager.getGenetics().getGene(Genetics.FACE) * 22));
            return new Identifier("mca", "skins/face/normal/" + gender.getDataName() + "/" + index + ".png");
        }
        return list.pick("normal", villager.getGenetics().getGene(Genetics.FACE));
    }

    public static Identifier getClothes(VillagerLike<?> villager) {
        return getClothes(villager, "normal");
    }

    public static Identifier getClothes(VillagerLike<?> villager, String variant) {
        String identifier = villager.getClothes();
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        Identifier id = new Identifier(identifier);
        Identifier variantId = new Identifier(id.getNamespace(), id.getPath().replace("normal", variant));
        return MinecraftClient.getInstance().getResourceManager().getResource(variantId).isPresent() ? variantId : id;
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

    private static Identifier getLibraryOrResourceIdentifier(String identifier) {
        return identifier.startsWith("immersive_library:")
                ? SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)))
                : new Identifier(identifier);
    }

    private static Identifier getOverlayIdentifier(String identifier) {
        if (identifier.startsWith("immersive_library:") || !identifier.endsWith(".png")) {
            return null;
        }
        Identifier id = new Identifier(identifier);
        Identifier overlay = new Identifier(id.getNamespace(), id.getPath().replace(".png", "_overlay.png"));
        return MinecraftClient.getInstance().getResourceManager().getResource(overlay).isPresent() ? overlay : null;
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

    public static NativeImage loadTexture(Identifier id) {
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
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().getInputStream()) {
                    return NativeImage.read(stream);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public static void composite(NativeImage base, Identifier layerId, int tintColor) {
        if (layerId == null) return;
        NativeImage overlay = loadTexture(layerId);
        if (overlay == null) return;

        try {
            int width = Math.min(base.getWidth(), overlay.getWidth());
            int height = Math.min(base.getHeight(), overlay.getHeight());

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int overPixel = overlay.getColor(x, y);
                    compositePixel(base, x, y, overPixel, tintColor);
                }
            }
        } finally {
            overlay.close();
        }
    }

    public static void compositeFace(NativeImage base, Identifier faceId, VillagerLike<?> villager) {
        NativeImage face = loadTexture(faceId);
        if (face == null) {
            return;
        }

        try {
            EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(face);
            int splitX = bounds.minX() + bounds.width() / 2;
            compositeEyeLayer(base, face, true, EyeTextureLayers.Side.FULL, splitX, 0xFFFFFFFF);
            if (villager.getTraits().hasTrait(Traits.HETEROCHROMIA)) {
                compositeEyeLayer(base, face, false, EyeTextureLayers.Side.LEFT, splitX, getEyeColor(villager, true));
                compositeEyeLayer(base, face, false, EyeTextureLayers.Side.RIGHT, splitX, getEyeColor(villager, false));
            } else {
                compositeEyeLayer(base, face, false, EyeTextureLayers.Side.FULL, splitX, getEyeColor(villager, false));
            }
        } finally {
            face.close();
        }
    }

    public static void compositeEyeLayer(NativeImage base, NativeImage face, boolean sclera, EyeTextureLayers.Side side, int splitX, int tintColor) {
        int width = Math.min(base.getWidth(), face.getWidth());
        int height = Math.min(base.getHeight(), face.getHeight());
        for (int x = 0; x < width; x++) {
            if (!EyeTextureLayers.isInSide(x, splitX, side)) {
                continue;
            }
            for (int y = 0; y < height; y++) {
                int pixel = face.getColor(x, y); // ABGR
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha == 0 || sclera != EyeTextureLayers.isScleraPixel(alpha, pixel & 0xFF, (pixel >> 8) & 0xFF, (pixel >> 16) & 0xFF)) {
                    continue;
                }
                compositePixel(base, x, y, pixel, tintColor);
            }
        }
    }

    public static int getEyeColor(VillagerLike<?> villager, boolean left) {
        if (villager.getTraits().hasTrait(Traits.RAINBOW_EYES)) {
            int offset = left && villager.getTraits().hasTrait(Traits.HETEROCHROMIA) ? (25 * DyeColor.values().length) / 2 : 0;
            return getRainbow(villager, offset);
        }

        boolean heterochromia = villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
        int dye = left && heterochromia ? villager.getEyeLeftDye() : villager.getEyeDye();
        if (dye != 0xFFFFFFFF) {
            return dye;
        }

        if (villager.getTraits().hasTrait(Traits.ALBINISM)) {
            return 0xFFE8A0A0; // ALBINISM_EYE_COLOR
        }

        float eyeColor = MathHelper.fractionalPart(villager.getGenetics().getGene(Genetics.FACE) + (left && heterochromia ? 0.43F : 0.0F));
        int blueColor = 0xFF557FA6;
        int greenColor = 0xFF5B8756;
        int hazelColor = 0xFF8A6A35;
        int brownColor = 0xFF4A2B18;
        if (eyeColor < 0.35F) {
            return ColorHelper.Argb.lerp(eyeColor / 0.35F, blueColor, greenColor);
        }
        if (eyeColor < 0.70F) {
            return ColorHelper.Argb.lerp((eyeColor - 0.35F) / 0.35F, greenColor, hazelColor);
        }
        return ColorHelper.Argb.lerp((eyeColor - 0.70F) / 0.30F, hazelColor, brownColor);
    }

    private static int getRainbow(VillagerLike<?> villager, int offset) {
        int ticks = Math.abs(villager.asEntity().age) + offset;
        int block = ticks / 25 + villager.asEntity().getId();
        int count = DyeColor.values().length;
        int first = block % count;
        int second = (block + 1) % count;
        float mix = (float) (ticks % 25) / 25.0F;
        return ColorHelper.Argb.lerp(mix, rgbToArgb(SheepEntity.getRgbColor(DyeColor.byId(first))), rgbToArgb(SheepEntity.getRgbColor(DyeColor.byId(second))));
    }

    private static int rgbToArgb(float[] rgb) {
        return ColorHelper.Argb.getArgb(
                255,
                MathHelper.clamp(Math.round(rgb[0] * 255.0f), 0, 255),
                MathHelper.clamp(Math.round(rgb[1] * 255.0f), 0, 255),
                MathHelper.clamp(Math.round(rgb[2] * 255.0f), 0, 255)
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
            base.setColor(x, y, 0xFF000000 | (overB << 16) | (overG << 8) | overR);
        } else {
            int basePixel = base.getColor(x, y);
            int baseAlpha = (basePixel >> 24) & 0xFF;
            int baseR = basePixel & 0xFF;
            int baseG = (basePixel >> 8) & 0xFF;
            int baseB = (basePixel >> 16) & 0xFF;

            int outAlpha = overA + (baseAlpha * (255 - overA)) / 255;
            if (outAlpha > 0) {
                int outR = (overR * overA + baseR * baseAlpha * (255 - overA) / 255) / outAlpha;
                int outG = (overG * overA + baseG * baseAlpha * (255 - overA) / 255) / outAlpha;
                int outB = (overB * overA + baseB * baseAlpha * (255 - overA) / 255) / outAlpha;
                base.setColor(x, y, (outAlpha << 24) | (outB << 16) | (outG << 8) | outR);
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
