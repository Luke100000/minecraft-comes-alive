package net.conczin.mca.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelPart.class)
public interface MixinModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> mca$getChildren();
}
