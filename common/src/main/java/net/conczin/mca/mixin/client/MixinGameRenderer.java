package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "tick", at = @At("TAIL"))
    public void mca$injectTick(CallbackInfo ci) {
        Entity cameraEntity = minecraft.getCameraEntity();
        if (MCAClient.areShadersAllowed() && cameraEntity != null) {
            VillagerLike<?> villagerLike = CommonVillagerModel.getVillager(cameraEntity);
            if (villagerLike != null) {
                if (currentPostEffect() == null) {
                    if (mca$currentShader != null) {
                        setPostEffect(mca$currentShader.getB());
                    } else {
                        Config.getInstance().shaderLocationsMap.entrySet().stream()
                                .filter(entry -> villagerLike.getTraits().hasTrait(entry.getKey()))
                                .filter(entry -> MCAClient.areShadersAllowed(entry.getKey() + "_shader"))
                                .findFirst().ifPresent(entry -> {
                                    Identifier shaderId = Identifier.parse(entry.getValue());
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
