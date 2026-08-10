package net.mca.client.render;

import net.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class JourneyMapIconBridge {
    private JourneyMapIconBridge() {
    }

    public static ResourceLocation getOrCreateFaceIcon(VillagerLike<?> villager) {
        Entity entity = villager.asEntity();
        return DynamicSkinCache.getOrCreateCroppedFace(entity);
    }

    public static DynamicTexture getOrCreateFaceTexture(VillagerLike<?> villager) {
        ResourceLocation icon = getOrCreateFaceIcon(villager);
        if (icon == null) {
            return null;
        }

        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(icon);
        return texture instanceof DynamicTexture dynamicTexture ? dynamicTexture : null;
    }
}
