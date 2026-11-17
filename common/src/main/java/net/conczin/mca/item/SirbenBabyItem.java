package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
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
    protected void configureChild(VillagerEntityMCA child) {
        child.getTraits().addTrait(Traits.SIRBEN);
    }
}
