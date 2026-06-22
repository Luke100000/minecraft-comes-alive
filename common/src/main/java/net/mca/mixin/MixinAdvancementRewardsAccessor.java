package net.mca.mixin;

import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementRewards.class)
public interface MixinAdvancementRewardsAccessor {
    @Accessor("loot")
    Identifier[] getLoot();

    @Accessor("loot")
    @Mutable
    void setLoot(Identifier[] loot);
}
