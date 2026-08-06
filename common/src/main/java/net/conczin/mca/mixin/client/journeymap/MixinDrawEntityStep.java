package net.conczin.mca.mixin.client.journeymap;

import net.conczin.mca.client.render.JourneyMapIconBridge;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.LivingEntity;
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
    private DynamicTexture entityTexture;

    @Shadow(remap = false)
    private DynamicTexture locatorTexture;

    @Shadow(remap = false)
    private DynamicTexture locatorBGTexture;

    @Shadow(remap = false)
    private boolean showOutline;

    @Shadow(remap = false)
    private boolean useDots;

    @Inject(
            method = "update(Ljourneymap/client/ui/minimap/EntityDisplay;Lnet/minecraft/client/renderer/texture/DynamicTexture;Lnet/minecraft/client/renderer/texture/DynamicTexture;Lnet/minecraft/client/renderer/texture/DynamicTexture;IIZZZZF)V",
            at = @At("TAIL"),
            remap = false
    )
    private void mca$useVillagerFaceTexture(CallbackInfo ci) {
        LivingEntity entity = this.entityRef == null ? null : this.entityRef.get();
        if (!(entity instanceof VillagerLike<?> villager)) {
            return;
        }

        DynamicTexture faceTexture = JourneyMapIconBridge.getOrCreateFaceTexture(villager);
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
