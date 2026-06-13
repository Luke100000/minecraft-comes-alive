package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    @Final
    Minecraft minecraft;

    @Unique
    private Tuple<String, ResourceLocation> mca$currentShader;

    @Shadow
    public abstract void clearPostEffect();

    @Shadow
    public abstract @Nullable ResourceLocation currentPostEffect();

    @Invoker("setPostEffect")
    public abstract void mca$invokeSetPostEffect(ResourceLocation id);

    @Unique
    private static ResourceLocation mca$normalizePostEffectId(String id) {
        ResourceLocation identifier = ResourceLocation.parse(id);
        String path = identifier.getPath();
        if (path.startsWith("shaders/post/") && path.endsWith(".json")) {
            String name = path.substring("shaders/post/".length(), path.length() - ".json".length());
            return ResourceLocation.fromNamespaceAndPath(identifier.getNamespace(), name);
        }
        if (path.startsWith("post_effect/") && path.endsWith(".json")) {
            String name = path.substring("post_effect/".length(), path.length() - ".json".length());
            return ResourceLocation.fromNamespaceAndPath(identifier.getNamespace(), name);
        }
        return identifier;
    }

    @Unique
    private static @Nullable Tuple<String, ResourceLocation> mca$findShader(VillagerLike<?> villager) {
        for (var entry : Config.getInstance().shaderLocationsMap.entrySet()) {
            if (villager.getTraits().hasTrait(entry.getKey()) && MCAClient.areShadersAllowed(entry.getKey() + "_shader")) {
                return new Tuple<>(entry.getKey(), mca$normalizePostEffectId(entry.getValue()));
            }
        }
        return null;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void mca$injectTick(CallbackInfo ci) {
        if (!MCAClient.areShadersAllowed() || minecraft.getCameraEntity() == null) {
            if (mca$currentShader != null) {
                clearPostEffect();
                this.mca$currentShader = null;
            }
            return;
        }

        VillagerLike<?> villagerLike = CommonVillagerModel.getVillager(minecraft.getCameraEntity());
        if (villagerLike != null) {
            if (currentPostEffect() == null) {
                if (mca$currentShader != null) {
                    mca$invokeSetPostEffect(mca$currentShader.getB());
                } else {
                    mca$currentShader = mca$findShader(villagerLike);
                    if (mca$currentShader != null) {
                        mca$invokeSetPostEffect(mca$currentShader.getB());
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
