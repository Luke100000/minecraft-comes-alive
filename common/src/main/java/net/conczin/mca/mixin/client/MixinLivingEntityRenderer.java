package net.conczin.mca.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface MixinLivingEntityRenderer {
   @Accessor("model")
   EntityModel<?> mca$getModel();

   @Accessor("model")
   void mca$setModel(EntityModel<?> var1);

   @Invoker("addLayer")
   boolean mca$addLayer(RenderLayer var1);
}
