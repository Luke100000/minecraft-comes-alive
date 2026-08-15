package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
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
    abstract void loadEffect(ResourceLocation id);

    @Shadow
    @Final
    Minecraft minecraft;

    @Shadow
    @Nullable
    PostChain postEffect;

    @Shadow
    public abstract void shutdownEffect();

    @Unique
    private Tuple<String, ResourceLocation> currentShader;

    @Inject(method = "tick", at = @At("TAIL"))
    public void onCameraSet(CallbackInfo ci) {
        if (MCAClient.areShadersAllowed() && this.minecraft.cameraEntity != null) {
            VillagerLike<?> villagerLike = CommonVillagerModel.getVillager(this.minecraft.cameraEntity);
            if (villagerLike != null) {
                if (postEffect == null) {
                    if (currentShader != null) {
                        this.loadEffect(currentShader.getB());
                    } else {
                        Config.getInstance().shaderLocationsMap.entrySet().stream()
                                .filter(entry -> villagerLike.getTraits().hasTrait(entry.getKey()))
                                .filter(entry -> MCAClient.areShadersAllowed(entry.getKey() + "_shader"))
                                .findFirst().ifPresent(entry -> {
                                    ResourceLocation shaderId = new ResourceLocation(entry.getValue());
                                    currentShader = new Tuple<>(entry.getKey(), shaderId);
                                    this.loadEffect(shaderId);
                                });
                    }
                } else if (currentShader != null && !villagerLike.getTraits().hasTrait(currentShader.getA())) {
                    shutdownEffect();
                    this.currentShader = null;
                }
            }
        }
    }
}
