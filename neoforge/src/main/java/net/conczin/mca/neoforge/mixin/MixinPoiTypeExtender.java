package net.conczin.mca.neoforge.mixin;

import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.poi.PoiTypeExtender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = PoiTypeExtender.class, remap = false)
public abstract class MixinPoiTypeExtender {
    @Shadow
    private static void register(ResourceKey<PoiType> typeKey, Set<BlockState> states) {
        throw new AssertionError();
    }

    @Inject(method = "extendPoiTypes", at = @At("RETURN"))
    private static void mca$registerUnclaimedBedPois(CallbackInfo ci) {
        // Wait until every mod has extended its POIs before adding fallback HOME states.
        Set<BlockState> states = BedPoiCompatibility.unregisteredModdedHomeStates();
        if (!states.isEmpty()) {
            register(PoiTypes.HOME, states);
        }
    }
}
