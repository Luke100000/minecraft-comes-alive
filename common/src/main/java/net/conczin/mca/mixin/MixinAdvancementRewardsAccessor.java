package net.conczin.mca.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(net.minecraft.advancements.AdvancementRewards.class)
public interface MixinAdvancementRewardsAccessor {
    @Accessor("loot")
    List<ResourceKey<LootTable>> mca$getLoot();

    @Mutable
    @Accessor("loot")
    void mca$setLoot(List<ResourceKey<LootTable>> loot);
}
