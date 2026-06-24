package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IronGolem.class)
public abstract class MixinIronGolem extends LivingEntity {
    protected MixinIronGolem(EntityType<? extends LivingEntity> type, Level world) {
        super(type, world);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof VillagerEntityMCA) {
            return false;
        }
        return super.canAttack(target);
    }
}
