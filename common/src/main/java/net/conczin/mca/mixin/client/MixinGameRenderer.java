package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
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
    @Nullable
    public abstract Identifier currentPostEffect();

    @Shadow
    protected abstract void setPostEffect(Identifier identifier);

    @Shadow
    public abstract void clearPostEffect();

    @Unique
    private Tuple<String, Identifier> mca$currentShader;

    @Inject(method = "tick", at = @At("TAIL"))
    public void mca$injectTick(CallbackInfo ci) {
        if (MCAClient.areShadersAllowed() && minecraft.getCameraEntity() != null) {
            VillagerLike<?> villagerLike = CommonVillagerModel.getVillager(minecraft.getCameraEntity());
            if (villagerLike != null) {
                if (currentPostEffect() == null) {
                    if (mca$currentShader != null) {
                        setPostEffect(mca$currentShader.getB());
                    } else {
                        Config.getInstance().shaderLocationsMap.entrySet().stream()
                                .filter(entry -> villagerLike.getTraits().hasTrait(entry.getKey()))
                                .filter(entry -> MCAClient.areShadersAllowed(entry.getKey() + "_shader"))
                                .findFirst().ifPresent(entry -> {
                                    String shaderPath = Config.normalizeShaderLocation(entry.getValue());
                                    if (shaderPath == null || shaderPath.isBlank()) {
                                        return;
                                    }
                                    Identifier shaderId = Identifier.parse(shaderPath);
                                    mca$currentShader = new Tuple<>(entry.getKey(), shaderId);
                                    setPostEffect(shaderId);
                                });
                    }
                } else if (mca$currentShader != null && !villagerLike.getTraits().hasTrait(mca$currentShader.getA())) {
                    clearPostEffect();
                    this.mca$currentShader = null;
                }
            }
        }
    }
}
