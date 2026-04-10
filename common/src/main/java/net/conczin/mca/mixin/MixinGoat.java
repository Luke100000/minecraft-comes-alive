package net.conczin.mca.mixin;

import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Goat.class)
public abstract class MixinGoat extends Animal {
    protected MixinGoat(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "getMilkingSound()Lnet/minecraft/sounds/SoundEvent;", at = @At("HEAD"))
    protected void mca$injectGetMilkingSound(CallbackInfoReturnable<SoundEvent> cir) {
        if (!this.level().isClientSide() && this.level().isRaining()) {
            long time = this.level().getDayTime() % 24000;
            BlockPos pos = blockPosition();
            if (time > 16000 && time < 20000 && this.level().getBiome(pos).value().coldEnoughToSnow(pos, this.level().getSeaLevel()) && SpawnPlacements.isSpawnPositionOk(EntityType.WITHER_SKELETON, level(), pos)) {
                WitherSkeleton ancientCultist = EntityType.WITHER_SKELETON.create(level(), EntitySpawnReason.EVENT);
                if (ancientCultist != null) {
                    //place the ancient boi
                    ancientCultist.setPos(pos.getX(), pos.getY(), pos.getZ());
                    WorldUtils.spawnEntity(level(), ancientCultist, EntitySpawnReason.EVENT);

                    //drip
                    ancientCultist.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                    ancientCultist.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                    ancientCultist.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
                    ancientCultist.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
                    ancientCultist.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
                    ancientCultist.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ItemsMCA.BOOK_CULT_ANCIENT));
                    ancientCultist.setDropChance(EquipmentSlot.OFFHAND, 1.0f);

                    ancientCultist.setCustomName(Component.translatable("entity.mca.ancient_cultist"));

                    //advancement
                    ((ServerLevel) this.level()).players().stream().filter(p -> p.distanceTo(this) < 30).forEach(p -> {
                        CriterionMCA.GENERIC_EVENT.trigger(p, "ancient_cultists");
                    });

                    //remove the goat
                    kill((ServerLevel) this.level());

                    //extra spiciness
                    level().setSkyFlashTime(10);
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level(), EntitySpawnReason.EVENT);
                    if (bolt != null) {
                        bolt.setVisualOnly(true);
                        bolt.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        level().addFreshEntity(bolt);
                    }
                }
            }
        }
    }
}
