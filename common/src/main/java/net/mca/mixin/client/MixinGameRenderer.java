package net.mca.mixin.client;

import net.mca.Config;
import net.mca.MCAClient;
import net.mca.client.model.CommonVillagerModel;
import net.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
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
    @Nullable PostChain postEffect;

    @Shadow
    abstract void loadEffect(ResourceLocation resourceLocation);

    @Shadow
    public abstract void togglePostEffect();

    @Unique
    private Tuple<String, ResourceLocation> mca$currentShader;

    @Inject(method = "tick", at = @At("TAIL"))
    public void mca$injectTick(CallbackInfo ci) {
        if (MCAClient.areShadersAllowed() && minecraft.cameraEntity != null) {
            VillagerLike<?> villagerLike = CommonVillagerModel.getVillager(minecraft.cameraEntity);
            if (villagerLike != null) {
                if (postEffect == null) {
                    if (mca$currentShader != null) {
                        loadEffect(mca$currentShader.getB());
                    } else {
                        Config.getInstance().shaderLocationsMap.entrySet().stream()
                                .filter(entry -> villagerLike.getTraits().hasTrait(entry.getKey()))
                                .filter(entry -> MCAClient.areShadersAllowed(entry.getKey() + "_shader"))
                                .findFirst().ifPresent(entry -> {
                                    ResourceLocation shaderId = new ResourceLocation(entry.getValue());
                                    mca$currentShader = new Tuple<>(entry.getKey(), shaderId);
                                    loadEffect(shaderId);
                                });
                    }
                } else if (mca$currentShader != null && !villagerLike.getTraits().hasTrait(mca$currentShader.getA())) {
                    togglePostEffect();
                    this.mca$currentShader = null;
                }
            }
        }
    }
}
