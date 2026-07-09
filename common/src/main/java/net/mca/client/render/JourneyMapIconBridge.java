package net.mca.client.render;

import net.mca.entity.VillagerLike;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public final class JourneyMapIconBridge {
    private JourneyMapIconBridge() {
    }

    public static Identifier getOrCreateFaceIcon(VillagerLike<?> villager) {
        Entity entity = villager.asEntity();
        return DynamicSkinCache.getOrCreateCroppedFace(entity);
    }

    public static NativeImageBackedTexture getOrCreateFaceTexture(VillagerLike<?> villager) {
        Identifier icon = getOrCreateFaceIcon(villager);
        if (icon == null) {
            return null;
        }

        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(icon);
        return texture instanceof NativeImageBackedTexture dynamicTexture ? dynamicTexture : null;
    }
}
