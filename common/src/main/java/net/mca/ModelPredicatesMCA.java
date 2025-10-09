package net.mca;

import net.mca.item.BabyItem;
import net.mca.item.ItemsMCA;
import net.mca.item.SirbenBabyItem;
import net.mca.item.VillagerTrackerItem;
import net.mca.util.network.datasync.CDataParameter;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public interface ModelPredicatesMCA {
    static void setup(CDataParameter.TriConsumer<Item, ResourceLocation, ClampedItemPropertyFunction> register) {
        register.accept(ItemsMCA.BABY_BOY.get(), new ResourceLocation("invalidated"), (stack, world, entity, i) ->
                BabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );
        register.accept(ItemsMCA.BABY_GIRL.get(), new ResourceLocation("invalidated"), (stack, world, entity, i) ->
                BabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );
        register.accept(ItemsMCA.SIRBEN_BABY_BOY.get(), new ResourceLocation("invalidated"), (stack, world, entity, i) ->
                SirbenBabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );
        register.accept(ItemsMCA.SIRBEN_BABY_GIRL.get(), new ResourceLocation("invalidated"), (stack, world, entity, i) ->
                SirbenBabyItem.hasBeenInvalidated(stack) ? 1 : 0
        );

        register.accept(ItemsMCA.VILLAGER_TRACKER.get(), new ResourceLocation("angle"), new CompassItemPropertyFunction((world, stack, entity) -> {
            return VillagerTrackerItem.getTargetPos(stack);
        }));
    }
}
