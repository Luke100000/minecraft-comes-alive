package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.FaceList;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SkinExporter {
    public static boolean export(VillagerEntityMCA villager) {
        return export(villager, null);
    }

    public static boolean export(VillagerEntityMCA villager, String customName) {
        try {
            try (NativeImage base = new NativeImage(64, 64, false)) {
                // Clear base canvas to transparent black
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        base.setPixelRGBA(x, y, 0);
                    }
                }

                // 1. Base skin
                ResourceLocation skinId = getSkin(villager);
                int skinColor = getSkinColor(villager);
                composite(base, skinId, skinColor);
                
                // 2. Face
                ResourceLocation faceId = getFace(villager);
                compositeFace(base, faceId, villager);
                
                // 3. Clothing
                ResourceLocation clothesId = getClothes(villager);
                composite(base, clothesId, 0xFFFFFFFF);
                
                // 4. Hair
                if (MCA.isBlankString(villager.getHair())) {
                    int hairColor = getHairColor(villager);
                    for (LayeredHair.Category category : new LayeredHair.Category[]{
                            LayeredHair.Category.BACK,
                            LayeredHair.Category.BASE,
                            LayeredHair.Category.BANGS,
                            LayeredHair.Category.FRONT,
                            LayeredHair.Category.EXTRA
                    }) {
                        String identifier = villager.getLayeredHair(category);
                        if (!MCA.isBlankString(identifier)) {
                            composite(base, ResourceLocation.parse(identifier), hairColor);
                        }
                    }
                } else {
                    String hair = villager.getHairStyleId();
                    if (!MCA.isBlankString(hair)) {
                        ResourceLocation texture = hair.startsWith("immersive_library:") 
                            ? SkinCache.getTextureIdentifier(Integer.parseInt(hair.substring(18)))
                            : ResourceLocation.parse(hair);
                        int hairColor = getHairColor(villager);
                        composite(base, texture, hairColor);
                        
                        if (!hair.startsWith("immersive_library:")) {
                            // Overlay
                            ResourceLocation overlay = ResourceLocation.parse(hair.replace(".png", "_overlay.png"));
                            composite(base, overlay, 0xFFFFFFFF);
                        }
                    }
                }
                
                // 5. Save the image
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
                            .withStyle(style -> style.withColor(ChatFormatting.GREEN))
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
                    
                    player.sendSystemMessage(message);
                    
                    // Close the MCA editor screen
                    Minecraft.getInstance().setScreen(null);
                }
                return true;
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to export villager skin", e);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable("chat.mca.skin_export_failure", e.getMessage()).withStyle(ChatFormatting.RED));
            }
            return false;
        }
    }

    private static ResourceLocation getSkin(VillagerEntityMCA villager) {
        if (!MCA.isBlankString(villager.getSkin())) {
            return ResourceLocation.parse(villager.getSkin());
        }
        int skin = (int) Math.min(4, Math.max(0, villager.getGenetics().getGene(Genetics.SKIN) * 5));
        return ResourceLocation.fromNamespaceAndPath("mca", "skins/skin/" + villager.getGenetics().getGender().getDataName() + "/" + skin + ".png");
    }

    private static int getSkinColor(VillagerEntityMCA villager) {
        int skinDye = villager.getSkinDye();
        if (skinDye != 0xFF000000) {
            return skinDye;
        }
        float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        return ColorPalette.SKIN.getColor(
                villager.getGenetics().getGene(Genetics.MELANIN) * albinism,
                villager.getGenetics().getGene(Genetics.HEMOGLOBIN) * albinism,
                villager.getInfectionProgress()
        );
    }

    private static ResourceLocation getFace(VillagerEntityMCA villager) {
        Gender gender = villager.getGenetics().getGender();
        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = (int) Math.min(21, Math.max(0, villager.getGenetics().getGene(Genetics.FACE) * 22));
            return ResourceLocation.fromNamespaceAndPath("mca", "skins/face/normal/" + gender.getDataName() + "/" + index + ".png");
        }
        return list.pick("normal", villager.getGenetics().getGene(Genetics.FACE));
    }

    private static ResourceLocation getClothes(VillagerEntityMCA villager) {
        String identifier = villager.getClothes();
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return ResourceLocation.parse(identifier);
    }

    private static int getHairColor(VillagerEntityMCA villager) {
        int hairDye = villager.getHairDye();
        if (hairDye != 0xFF000000) {
            return hairDye;
        }
        float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        return ColorPalette.HAIR.getColor(
                villager.getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                villager.getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                0
        );
    }

    private static NativeImage loadTexture(ResourceLocation id) {
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

    private static void composite(NativeImage base, ResourceLocation layerId, int tintColor) {
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

    public static void compositeFace(NativeImage base, ResourceLocation faceId, VillagerEntityMCA villager) {
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
                int pixel = face.getPixelRGBA(x, y); // ABGR
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha == 0 || sclera != EyeTextureLayers.isScleraPixel(alpha, pixel & 0xFF, (pixel >> 8) & 0xFF, (pixel >> 16) & 0xFF)) {
                    continue;
                }
                compositePixel(base, x, y, pixel, tintColor);
            }
        }
    }

    public static int getEyeColor(VillagerEntityMCA villager, boolean left) {
        if (villager.getTraits().hasTrait(Traits.RAINBOW_EYES)) {
            int offset = left && villager.getTraits().hasTrait(Traits.HETEROCHROMIA) ? (25 * DyeColor.values().length) / 2 : 0;
            int ticks = offset;
            int block = ticks / 25 + villager.getId();
            int count = DyeColor.values().length;
            int first = block % count;
            int second = (block + 1) % count;
            return FastColor.ARGB32.lerp(0.0f, Sheep.getColor(DyeColor.byId(first)), Sheep.getColor(DyeColor.byId(second)));
        }

        boolean heterochromia = villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
        int dye = left && heterochromia ? villager.getEyeLeftDye() : villager.getEyeDye();
        if (dye != 0xFFFFFFFF) {
            return dye;
        }

        if (villager.getTraits().hasTrait(Traits.ALBINISM)) {
            return 0xFFE8A0A0; // ALBINISM_EYE_COLOR
        }

        float eyeColor = Mth.frac(villager.getGenetics().getGene(Genetics.FACE) + (left && heterochromia ? 0.43F : 0.0F));
        int blueColor = 0xFF557FA6;
        int greenColor = 0xFF5B8756;
        int hazelColor = 0xFF8A6A35;
        int brownColor = 0xFF4A2B18;
        if (eyeColor < 0.35F) {
            return FastColor.ARGB32.lerp(eyeColor / 0.35F, blueColor, greenColor);
        }
        if (eyeColor < 0.70F) {
            return FastColor.ARGB32.lerp((eyeColor - 0.35F) / 0.35F, greenColor, hazelColor);
        }
        return FastColor.ARGB32.lerp((eyeColor - 0.70F) / 0.30F, hazelColor, brownColor);
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
