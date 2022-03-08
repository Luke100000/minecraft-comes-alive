package mca.mixin;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvent;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VillagerProfession.class)
public interface MixinVillagerProfession {
    @Invoker("<init>")
    static VillagerProfession init(String string, PointOfInterestType arg, ImmutableSet<Item> immutableSet, ImmutableSet<Block> immutableSet2, SoundEvent arg2) {
        return null;
    }
}
