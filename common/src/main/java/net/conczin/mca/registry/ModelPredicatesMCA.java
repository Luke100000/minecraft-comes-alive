package net.conczin.mca.registry;

import net.conczin.mca.item.BabyItem;
import net.conczin.mca.item.SirbenBabyItem;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public interface ModelPredicatesMCA {
    static void setup(CDataParameter.TriConsumer<Item, ResourceLocation, ClampedItemPropertyFunction> register) {
        register.accept(ItemsMCA.BABY_BOY, ResourceLocation.parse("invalidated"), (stack, world, entity, i) ->
                BabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );
        register.accept(ItemsMCA.BABY_GIRL, ResourceLocation.parse("invalidated"), (stack, world, entity, i) ->
                BabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );
        register.accept(ItemsMCA.SIRBEN_BABY_BOY, ResourceLocation.parse("invalidated"), (stack, world, entity, i) ->
                SirbenBabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );
        register.accept(ItemsMCA.SIRBEN_BABY_GIRL, ResourceLocation.parse("invalidated"), (stack, world, entity, i) ->
                SirbenBabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );

        register.accept(ItemsMCA.VILLAGER_TRACKER, ResourceLocation.parse("angle"), new CompassItemPropertyFunction((world, stack, entity) -> {
            return stack.get(DataComponentsMCA.TRACKER_POS);
        }));
    }
}
