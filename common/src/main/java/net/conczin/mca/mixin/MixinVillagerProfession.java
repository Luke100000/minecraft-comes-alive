package net.conczin.mca.mixin;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Predicate;

@Mixin(VillagerProfession.class)
public interface MixinVillagerProfession {
    @Invoker("<init>")
    static VillagerProfession init(Component name, Predicate<Holder<PoiType>> predicate, Predicate<Holder<PoiType>> predicate2, ImmutableSet<Item> immutableSet, ImmutableSet<Block> immutableSet2, @Nullable SoundEvent soundEvent) {
        return null;
    }
}
