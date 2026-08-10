package net.mca.mixin;

import net.mca.advancement.criterion.CriterionMCA;
import net.mca.item.ItemsMCA;
import net.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Goat.class)
public abstract class MixinGoatEntity extends Animal {
    protected MixinGoatEntity(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "getMilkingSound()Lnet/minecraft/sounds/SoundEvent;", at = @At("HEAD"))
    protected void getMilkingSound(CallbackInfoReturnable<SoundEvent> cir) {
        if (!this.level().isClientSide && this.level().isRaining()) {
            long time = this.level().getDayTime() % 24000;
            BlockPos pos = blockPosition();
            if (time > 16000 && time < 20000 && this.level().getBiome(pos).value().coldEnoughToSnow(pos) && NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, level(), pos, EntityType.WITHER_SKELETON)) {
                WitherSkeleton ancientCultist = EntityType.WITHER_SKELETON.create(level());
                if (ancientCultist != null) {
                    //place the ancient boi
                    ancientCultist.setPos(pos.getX(), pos.getY(), pos.getZ());
                    WorldUtils.spawnEntity(level(), ancientCultist, MobSpawnType.EVENT);

                    //drip
                    ancientCultist.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                    ancientCultist.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                    ancientCultist.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
                    ancientCultist.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
                    ancientCultist.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
                    ancientCultist.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ItemsMCA.BOOK_CULT_ANCIENT.get()));
                    ancientCultist.setDropChance(EquipmentSlot.OFFHAND, 1.0f);

                    ancientCultist.setCustomName(Component.translatable("entity.mca.ancient_cultist"));

                    //advancement
                    ((ServerLevel)this.level()).players().stream().filter(p -> p.distanceTo(this) < 30).forEach(p -> {
                        CriterionMCA.GENERIC_EVENT_CRITERION.trigger(p, "ancient_cultists");
                    });

                    //remove the goat
                    kill();

                    //extra spiciness
                    level().setSkyFlashTime(10);
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level());
                    if (bolt != null) {
                        bolt.setVisualOnly(true);
                        bolt.absMoveTo(pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F);
                        level().addFreshEntity(bolt);
                    }
                }
            }
        }
    }
}
