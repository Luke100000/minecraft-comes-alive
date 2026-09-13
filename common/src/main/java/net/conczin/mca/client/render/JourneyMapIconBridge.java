package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class JourneyMapIconBridge {
    private JourneyMapIconBridge() {
    }

    public static Identifier getOrCreateFaceIcon(VillagerLike<?> villager) {
        return DynamicSkinCache.getOrCreateCroppedFace(VillagerVisuals.capture(villager));
    }

    public static DynamicTexture getOrCreateFaceTexture(VillagerLike<?> villager) {
        Identifier icon = getOrCreateFaceIcon(villager);
        if (icon == null) {
            return null;
        }

        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(icon);
        return texture instanceof DynamicTexture dynamicTexture ? dynamicTexture : null;
    }
}
