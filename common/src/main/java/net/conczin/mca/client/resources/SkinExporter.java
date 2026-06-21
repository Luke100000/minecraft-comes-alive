package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.FaceList;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.util.ImmersiveLibraryIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Set;

public class SkinExporter {
    public static final Identifier BLINK_FACE = MCA.locate("skins/face/normal/blink.png");

    public static boolean export(VillagerEntityMCA villager) {
        return export(villager, null);
    }

    public static boolean export(VillagerEntityMCA villager, String customName) {
        try {
            VillagerVisuals visuals = VillagerVisuals.capture(villager);
            
            try (NativeImage base = new NativeImage(64, 64, true)) {
                Identifier skinId = getSkin(visuals);
                int skinColor = getSkinColor(visuals);
                composite(base, skinId, skinColor);
                
                Identifier faceId = getFace(visuals);
                compositeFace(base, faceId, visuals);
                
                Identifier clothesId = getClothes(visuals);
                composite(base, clothesId, 0xFFFFFFFF);
                
                int hairColor = getHairColor(visuals);
                for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
                    String identifier = visuals.layeredHair(category);
                    if (!MCA.isBlankString(identifier)) {
                        composite(base, getLibraryOrResourceIdentifier(identifier), hairColor);
                    }
                }
                
                File exportDir = new File(Minecraft.getInstance().gameDirectory, "mca/exported_skins");
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }
                
                File destFile = getAvailableExportFile(exportDir, customName);
                base.writeToFile(destFile.toPath());
                
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    var message = Component.translatable("chat.mca.skin_export_success", "mca/exported_skins/" + destFile.getName())
                            .withStyle(style -> style.withColor(ChatFormatting.GREEN))
                            .append(Component.literal(" "))
                            .append(Component.translatable("chat.mca.open_image")
                                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent.OpenFile(destFile))))
                            .append(Component.literal(" "))
                            .append(Component.translatable("chat.mca.open_folder")
                                    .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent.OpenFile(exportDir))));
                    
                    player.sendSystemMessage(message);
                }
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to export villager skin", e);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable("chat.mca.skin_export_failure", e.getMessage()).withStyle(ChatFormatting.RED));
            }
            return false;
        }
        return true;
    }

    private static File getAvailableExportFile(File exportDir, String customName) {
        String fileName;
        if (MCA.isBlankString(customName)) {
            fileName = "Exported_Skin";
        } else {
            fileName = customName.replaceAll("[^a-zA-Z0-9_\\-]", "_") + "_skin";
        }

        String[] listedFiles = exportDir.list();
        Set<String> existingFiles = listedFiles == null ? Set.of() : Set.of(listedFiles);
        String candidateName = fileName + ".png";
        for (int suffix = 2; existingFiles.contains(candidateName); suffix++) {
            candidateName = fileName + "_" + suffix + ".png";
        }
        return new File(exportDir, candidateName);
    }

    private static Identifier getSkin(VillagerVisuals visuals) {
        if (!MCA.isBlankString(visuals.skin())) {
            return Identifier.parse(visuals.skin());
        }
        int skin = (int) Math.min(4, Math.max(0, visuals.skinGene() * 5));
        return Identifier.fromNamespaceAndPath("mca", "skins/skin/" + visuals.genderDataName() + "/" + skin + ".png");
    }

    private static int getSkinColor(VillagerVisuals visuals) {
        if (!MCA.isBlankString(visuals.skin())) {
            BodySkinList list = BodySkinList.getInstance();
            BodySkin skin = list == null ? null : list.get(visuals.skin());
            if (skin == null || !skin.isTinted()) {
                return 0xFFFFFFFF;
            }
        }
        int skinDye = visuals.skinDye();
        if (skinDye != 0xFF000000) {
            return skinDye;
        }
        float albinism = visuals.albinism() ? 0.1f : 1.0f;
        return ColorPalette.SKIN.getColor(
                visuals.melaninGene() * albinism,
                visuals.hemoglobinGene() * albinism,
                visuals.infectionProgress()
        );
    }

    private static Identifier getFace(VillagerVisuals visuals) {
        FaceList list = FaceList.getInstance();
        return list == null ? BLINK_FACE : list.pick("normal", visuals.faceGene());
    }

    private static Identifier getClothes(VillagerVisuals visuals) {
        String identifier = visuals.clothes();
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        return getLibraryOrResourceIdentifier(identifier);
    }

    private static int getHairColor(VillagerVisuals visuals) {
        int hairDye = visuals.hairDye();
        if (hairDye != 0xFF000000) {
            return hairDye;
        }
        float albinism = visuals.albinism() ? 0.1f : 1.0f;
        return ColorPalette.HAIR.getColor(
                visuals.eumelaninGene() * albinism,
                visuals.pheomelaninGene() * albinism,
                0
        );
    }

    private static NativeImage loadTexture(Identifier id) {
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

    private static Identifier getLibraryOrResourceIdentifier(String identifier) {
        var contentId = ImmersiveLibraryIds.contentId(identifier);
        if (contentId.isPresent()) {
            return SkinCache.getTextureIdentifier(contentId.getAsInt());
        }
        return Identifier.parse(identifier);
    }

    private static void compositeFace(NativeImage base, Identifier faceId, VillagerVisuals visuals) {
        NativeImage face = loadTexture(faceId);
        if (face == null) {
            return;
        }

        try {
            EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(face);
            int splitX = bounds.minX() + bounds.width() / 2;
            compositeEyeLayer(base, face, true, EyeTextureLayers.Side.FULL, splitX, 0xFFFFFFFF);
            if (visuals.heterochromia()) {
                compositeEyeLayer(base, face, false, EyeTextureLayers.Side.LEFT, splitX, getEyeColor(visuals, true));
                compositeEyeLayer(base, face, false, EyeTextureLayers.Side.RIGHT, splitX, getEyeColor(visuals, false));
            } else {
                compositeEyeLayer(base, face, false, EyeTextureLayers.Side.FULL, splitX, getEyeColor(visuals, false));
            }
        } finally {
            face.close();
        }
    }

    private static int getEyeColor(VillagerVisuals visuals, boolean left) {
        return visuals.staticEyeColor(left);
    }

    private static void compositeEyeLayer(NativeImage base, NativeImage face, boolean sclera, EyeTextureLayers.Side side, int splitX, int tintColor) {
        int width = Math.min(base.getWidth(), face.getWidth());
        int height = Math.min(base.getHeight(), face.getHeight());
        for (int x = 0; x < width; x++) {
            if (!EyeTextureLayers.isInSide(x, splitX, side)) {
                continue;
            }
            for (int y = 0; y < height; y++) {
                int pixel = face.getPixel(x, y);
                int alpha = ARGB.alpha(pixel);
                if (alpha == 0 || sclera != EyeTextureLayers.isScleraPixel(alpha, ARGB.red(pixel), ARGB.green(pixel), ARGB.blue(pixel))) {
                    continue;
                }
                compositePixel(base, x, y, pixel, tintColor);
            }
        }
    }

    private static void composite(NativeImage base, Identifier layerId, int tintColor) {
        if (layerId == null) {
            return;
        }
        NativeImage overlay = loadTexture(layerId);
        if (overlay == null) {
            return;
        }
        
        try {
            int width = Math.min(base.getWidth(), overlay.getWidth());
            int height = Math.min(base.getHeight(), overlay.getHeight());

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int overPixel = overlay.getPixel(x, y);
                    compositePixel(base, x, y, overPixel, tintColor);
                }
            }
        } finally {
            overlay.close();
        }
    }

    private static void compositePixel(NativeImage base, int x, int y, int overPixel, int tintColor) {
        int tr = ARGB.red(tintColor);
        int tg = ARGB.green(tintColor);
        int tb = ARGB.blue(tintColor);
        int ta = ARGB.alpha(tintColor);
        int overAlpha = ARGB.alpha(overPixel);
        if (overAlpha == 0) {
            return;
        }

        int overR = (ARGB.red(overPixel) * tr) / 255;
        int overG = (ARGB.green(overPixel) * tg) / 255;
        int overB = (ARGB.blue(overPixel) * tb) / 255;
        int overA = (overAlpha * ta) / 255;

        if (overA == 0) {
            return;
        } else if (overA == 255) {
            base.setPixel(x, y, ARGB.color(255, overR, overG, overB));
        } else {
            int basePixel = base.getPixel(x, y);
            int baseAlpha = ARGB.alpha(basePixel);
            int baseR = ARGB.red(basePixel);
            int baseG = ARGB.green(basePixel);
            int baseB = ARGB.blue(basePixel);

            int outAlpha = overA + (baseAlpha * (255 - overA)) / 255;
            if (outAlpha > 0) {
                int outR = (overR * overA + baseR * baseAlpha * (255 - overA) / 255) / outAlpha;
                int outG = (overG * overA + baseG * baseAlpha * (255 - overA) / 255) / outAlpha;
                int outB = (overB * overA + baseB * baseAlpha * (255 - overA) / 255) / outAlpha;
                base.setPixel(x, y, ARGB.color(outAlpha, outR, outG, outB));
            }
        }
    }
}
