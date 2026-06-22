package net.mca.mixin;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IronGolemEntity.class)
public abstract class MixinIronGolem extends LivingEntity {
    protected MixinIronGolem(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        if (target instanceof VillagerEntityMCA) {
            return false;
        }
        return super.canTarget(target);
    }
}
