package net.mca.mixin;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

@Mixin(VillagerProfession.class)
public interface MixinVillagerProfession {
    @Invoker("<init>")
    static VillagerProfession init(String id, Predicate<Holder<PoiType>> predicate, Predicate<Holder<PoiType>> predicate2, ImmutableSet<Item> immutableSet, ImmutableSet<Block> immutableSet2, @Nullable SoundEvent soundEvent) {
        return null;
    }
}
