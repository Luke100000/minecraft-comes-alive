package net.mca.mixin.client.journeymap;

import net.mca.client.render.JourneyMapIconBridge;
import net.mca.entity.VillagerLike;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Pseudo
@Mixin(targets = "journeymap.client.render.draw.DrawEntityStep", remap = false)
public abstract class MixinDrawEntityStep {
    @Shadow(remap = false)
    private WeakReference<LivingEntity> entityRef;

    @Shadow(remap = false)
    private NativeImageBackedTexture entityTexture;

    @Shadow(remap = false)
    private NativeImageBackedTexture locatorTexture;

    @Shadow(remap = false)
    private NativeImageBackedTexture locatorBGTexture;

    @Shadow(remap = false)
    private boolean showOutline;

    @Shadow(remap = false)
    private boolean useDots;

    @Inject(method = "update", at = @At("TAIL"), remap = false, require = 0)
    private void mca$useVillagerFaceTexture(CallbackInfo ci) {
        LivingEntity entity = this.entityRef == null ? null : this.entityRef.get();
        if (!(entity instanceof VillagerLike<?> villager)) {
            return;
        }

        NativeImageBackedTexture faceTexture = JourneyMapIconBridge.getOrCreateFaceTexture(villager);
        if (faceTexture == null) {
            return;
        }

        this.entityTexture = faceTexture;
        this.locatorTexture = null;
        this.locatorBGTexture = null;
        this.showOutline = false;
        this.useDots = false;
    }
}
