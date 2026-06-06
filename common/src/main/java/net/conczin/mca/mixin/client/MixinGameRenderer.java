package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Tuple;
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
    public abstract void clearPostEffect();

    @Shadow
    public abstract void setPostEffect(Identifier id);

    @Shadow
    public abstract @Nullable Identifier currentPostEffect();

    @Unique
    private Tuple<String, Identifier> mca$currentShader;

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
    private static @Nullable Tuple<String, Identifier> mca$findShader(VillagerLike<?> villager) {
        for (var entry : Config.getInstance().shaderLocationsMap.entrySet()) {
            if (villager.getTraits().hasTrait(entry.getKey()) && MCAClient.areShadersAllowed(entry.getKey() + "_shader")) {
                return new Tuple<>(entry.getKey(), mca$normalizePostEffectId(entry.getValue()));
            }
        }
        return null;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void mca$injectTick(CallbackInfo ci) {
        Entity cameraEntity = minecraft.getCameraEntity();
        if (!MCAClient.areShadersAllowed() || cameraEntity == null) {
            if (mca$currentShader != null) {
                clearPostEffect();
                this.mca$currentShader = null;
            }
            return;
        }

        VillagerLike<?> villagerLike = mca$getCameraVillager(cameraEntity);
        if (villagerLike != null) {
            if (currentPostEffect() == null) {
                if (mca$currentShader != null) {
                    setPostEffect(mca$currentShader.getB());
                } else {
                    mca$currentShader = mca$findShader(villagerLike);
                    if (mca$currentShader != null) {
                        setPostEffect(mca$currentShader.getB());
                    }
                }
            } else if (mca$currentShader != null && !villagerLike.getTraits().hasTrait(mca$currentShader.getA())) {
                clearPostEffect();
                this.mca$currentShader = null;
            }
        } else if (mca$currentShader != null) {
            clearPostEffect();
            this.mca$currentShader = null;
        }
    }
}
