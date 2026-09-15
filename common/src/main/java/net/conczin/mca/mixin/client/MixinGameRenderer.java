package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.resources.data.SerializablePair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    @Final
    Minecraft minecraft;

    @Shadow
    public abstract java.util.List<Identifier> getRequestedPostEffects();

    @Unique
    private static @Nullable VillagerLike<?> mca$getCameraVillager(Entity entity) {
        if (entity instanceof VillagerLike<?> villager) {
            return villager;
        }
        if (entity instanceof Player player) {
            return MCAClient.getPlayerData(player.getUUID()).orElse(null);
        }
        return null;
    }

    @Unique
    private static Identifier mca$normalizePostEffectId(String id) {
        Identifier identifier = Identifier.parse(id);
        String path = identifier.getPath();
        if (path.startsWith("shaders/post/") && path.endsWith(".json")) {
            String name = path.substring("shaders/post/".length(), path.length() - ".json".length());
            return Identifier.fromNamespaceAndPath(identifier.getNamespace(), name);
        }
        if (path.startsWith("post_effect/") && path.endsWith(".json")) {
            String name = path.substring("post_effect/".length(), path.length() - ".json".length());
            return Identifier.fromNamespaceAndPath(identifier.getNamespace(), name);
        }
        return identifier;
    }

    @Unique
    private static @Nullable SerializablePair<String, Identifier> mca$findShader(VillagerLike<?> villager) {
        for (var entry : Config.getInstance().shaderLocationsMap.entrySet()) {
            if (villager.getTraits().hasTrait(entry.getKey()) && MCAClient.areShadersAllowed(entry.getKey() + "_shader")) {
                return new SerializablePair<>(entry.getKey(), mca$normalizePostEffectId(entry.getValue()));
            }
        }
        return null;
    }

    @Inject(method = "update", at = @At("TAIL"))
    public void mca$injectUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        Entity cameraEntity = minecraft.getCameraEntity();
        if (!MCAClient.areShadersAllowed() || cameraEntity == null) {
            return;
        }

        VillagerLike<?> villagerLike = mca$getCameraVillager(cameraEntity);
        if (villagerLike != null) {
            SerializablePair<String, Identifier> shader = mca$findShader(villagerLike);
            if (shader != null && !getRequestedPostEffects().contains(shader.right())) {
                getRequestedPostEffects().add(shader.right());
            }
        }
    }
}
