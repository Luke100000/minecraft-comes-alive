package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.FaceList;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SkinExporter {
    public static void export(VillagerEntityMCA villager) {
        export(villager, null);
    }

    public static void export(VillagerEntityMCA villager, String customName) {
        try {
            VillagerVisuals visuals = VillagerVisuals.capture(villager);
            
            try (NativeImage base = new NativeImage(64, 64, false)) {
                // Clear base canvas to transparent black
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        base.setPixel(x, y, 0);
                    }
                }

                // 1. Base skin
                ResourceLocation skinId = getSkin(visuals);
                int skinColor = getSkinColor(visuals);
                composite(base, skinId, skinColor);
                
                // 2. Face
                ResourceLocation faceId = getFace(visuals);
                composite(base, faceId, 0xFFFFFFFF);
                
                // 3. Clothing
                ResourceLocation clothesId = getClothes(visuals);
                composite(base, clothesId, 0xFFFFFFFF);
                
                // 4. Hair
                if (visuals.hasLayeredHair()) {
                    int hairColor = getHairColor(visuals);
                    for (LayeredHair.Category category : new LayeredHair.Category[]{
                            LayeredHair.Category.BACK,
                            LayeredHair.Category.BASE,
                            LayeredHair.Category.BANGS,
                            LayeredHair.Category.FRONT,
                            LayeredHair.Category.EXTRA
                    }) {
                        String identifier = visuals.layeredHair(category);
                        if (!MCA.isBlankString(identifier)) {
                            composite(base, ResourceLocation.parse(identifier), hairColor);
                        }
                    }
                } else {
                    String hair = visuals.hair();
                    if (!MCA.isBlankString(hair)) {
                        ResourceLocation texture = hair.startsWith("immersive_library:") 
                            ? SkinCache.getTextureIdentifier(Integer.parseInt(hair.substring(18)))
                            : ResourceLocation.parse(hair);
                        int hairColor = getHairColor(visuals);
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
                
                String villagerName = customName;
                if (MCA.isBlankString(villagerName)) {
                    villagerName = villager.getName().getString();
                }
                if (MCA.isBlankString(villagerName)) {
                    villagerName = "villager";
                }
                // Sanitize name
                villagerName = villagerName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                File destFile = new File(exportDir, villagerName + "_skin.png");
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
                                            .withClickEvent(new ClickEvent.OpenFile(destFile))))
                            .append(Component.literal(" "))
                            .append(Component.translatable("chat.mca.open_folder")
                                    .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent.OpenFile(exportDir))));
                    
                    player.sendSystemMessage(message);
                    
                    // Close the MCA editor screen
                    Minecraft.getInstance().setScreen(null);
                }
            }
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to export villager skin", e);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable("chat.mca.skin_export_failure", e.getMessage()).withStyle(ChatFormatting.RED));
            }
        }
    }

    private static ResourceLocation getSkin(VillagerVisuals visuals) {
        if (!MCA.isBlankString(visuals.skin())) {
            return ResourceLocation.parse(visuals.skin());
        }
        int skin = (int) Math.min(4, Math.max(0, visuals.skinGene() * 5));
        return ResourceLocation.fromNamespaceAndPath("mca", "skins/skin/" + visuals.genderDataName() + "/" + skin + ".png");
    }

    private static int getSkinColor(VillagerVisuals visuals) {
        if (!MCA.isBlankString(visuals.skin())) {
            BodySkinList list = BodySkinList.getInstance();
            BodySkin skin = list == null ? null : list.get(visuals.skin());
            if (skin == null || !skin.isTinted()) {
                return 0xFFFFFFFF;
            }
        }
        float albinism = visuals.albinism() ? 0.1f : 1.0f;
        return ColorPalette.SKIN.getColor(
                visuals.melaninGene() * albinism,
                visuals.hemoglobinGene() * albinism,
                visuals.infectionProgress()
        );
    }

    private static ResourceLocation getFace(VillagerVisuals visuals) {
        Gender gender = Gender.byName(visuals.genderDataName());
        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = (int) Math.min(21, Math.max(0, visuals.faceGene() * 22));
            return ResourceLocation.fromNamespaceAndPath("mca", "skins/face/normal/" + visuals.genderDataName() + "/" + index + ".png");
        }
        return list.pick("normal", gender, visuals.faceGene(), "");
    }

    private static ResourceLocation getClothes(VillagerVisuals visuals) {
        String identifier = visuals.clothes();
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return ResourceLocation.parse(identifier);
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
            int tr = FastColor.ARGB32.red(tintColor);
            int tg = FastColor.ARGB32.green(tintColor);
            int tb = FastColor.ARGB32.blue(tintColor);
            int ta = FastColor.ARGB32.alpha(tintColor);

            int width = Math.min(base.getWidth(), overlay.getWidth());
            int height = Math.min(base.getHeight(), overlay.getHeight());

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int overPixel = overlay.getPixel(x, y);
                    int overAlpha = FastColor.ARGB32.alpha(overPixel);
                    if (overAlpha == 0) {
                        continue;
                    }
                    
                    int overR = (FastColor.ARGB32.red(overPixel) * tr) / 255;
                    int overG = (FastColor.ARGB32.green(overPixel) * tg) / 255;
                    int overB = (FastColor.ARGB32.blue(overPixel) * tb) / 255;
                    int overA = (overAlpha * ta) / 255;
                    
                    if (overA == 0) {
                        continue;
                    } else if (overA == 255) {
                        base.setPixel(x, y, FastColor.ARGB32.color(255, overR, overG, overB));
                    } else {
                        int basePixel = base.getPixel(x, y);
                        int baseAlpha = FastColor.ARGB32.alpha(basePixel);
                        int baseR = FastColor.ARGB32.red(basePixel);
                        int baseG = FastColor.ARGB32.green(basePixel);
                        int baseB = FastColor.ARGB32.blue(basePixel);
                        
                        int outAlpha = overA + (baseAlpha * (255 - overA)) / 255;
                        if (outAlpha > 0) {
                            int outR = (overR * overA + baseR * baseAlpha * (255 - overA) / 255) / outAlpha;
                            int outG = (overG * overA + baseG * baseAlpha * (255 - overA) / 255) / outAlpha;
                            int outB = (overB * overA + baseB * baseAlpha * (255 - overA) / 255) / outAlpha;
                            base.setPixel(x, y, FastColor.ARGB32.color(outAlpha, outR, outG, outB));
                        }
                    }
                }
            }
        } finally {
            overlay.close();
        }
    }
}
