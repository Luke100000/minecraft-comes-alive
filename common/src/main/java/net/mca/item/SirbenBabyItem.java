package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class SirbenBabyItem extends BabyItem {
    public SirbenBabyItem(Gender gender, Properties properties) {
        super(gender, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    protected VillagerEntityMCA birthChild(ItemStack stack, ServerLevel world, ServerPlayer player) {
        VillagerEntityMCA child = super.birthChild(stack, world, player);
        child.getTraits().addTrait(Traits.SIRBEN);
        return child;
    }
}
