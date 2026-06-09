package net.conczin.mca.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagValueInput.class)
public interface MixinTagValueInput {
    @Accessor("input")
    CompoundTag mca$getInput();
}
