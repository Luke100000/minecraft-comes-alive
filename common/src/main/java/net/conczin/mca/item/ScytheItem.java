package net.conczin.mca.item;

import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.registry.*;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ScytheItem extends SwordItem {
    public ScytheItem(Properties settings) {
        super(ToolMaterial.GOLD, 3.0F, -2.4F, settings);
    }

    public static void setSoul(ItemStack stack, boolean soul) {
        stack.set(DataComponentsMCA.SCYTHE_HAS_SOUL, soul);
    }

    public static boolean hasSoul(ItemStack stack) {
        return stack.getOrDefault(DataComponentsMCA.SCYTHE_HAS_SOUL, false);
    }

    public static InteractionResult use(UseOnContext context, boolean cure) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);

        if (state.is(TagsMCA.Blocks.TOMBSTONES)) {
            return TombstoneBlock.Data.of(world.getBlockEntity(pos)).filter(TombstoneBlock.Data::hasEntity).map(data -> {
                if (!context.getLevel().isClientSide) {
                    CriterionMCA.GENERIC_EVENT.trigger((ServerPlayer) context.getPlayer(), cure ? "staffOfLife" : "scytheRevive");
                }

                if (!world.isClientSide && !data.isResurrecting()) {
                    data.startResurrecting(cure);
                    return InteractionResult.SUCCESS;
                }

                return InteractionResult.PASS;
            }).orElse(InteractionResult.FAIL);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.addAll(FlowingText.wrap(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY), 160));
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        boolean active = stack.getOrDefault(DataComponentsMCA.SCYTHE_ACTIVE, false);

        RandomSource r = entity.level().random;

        if (active != selected) {
            stack.set(DataComponentsMCA.SCYTHE_ACTIVE, selected);

            float baseVolume = selected ? 0.75F : 0.25F;
            entity.level().playSound(null, entity.blockPosition(), SoundsMCA.REAPER_SCYTHE_OUT, entity.getSoundSource(),
                    baseVolume + r.nextFloat() / 2F,
                    0.65F + r.nextFloat() / 10F
            );
        }

        if (selected) {
            if (living.swingTime == -1) {
                entity.level().playSound(null, entity.blockPosition(), SoundsMCA.REAPER_SCYTHE_SWING, entity.getSoundSource(), 0.25F, 1);
            }
        }
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        user.startUsingItem(hand);
        return super.use(world, user, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (hasSoul(context.getItemInHand())) {
            InteractionResult result = use(context, false);
            if (result == InteractionResult.SUCCESS) {
                setSoul(context.getItemInHand(), false);
            }
            if (result != InteractionResult.PASS) {
                return result;
            }
        }

        return super.useOn(context);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || hasSoul(stack);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level().random.nextInt(50) > 40) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 1000, 1));
        }

        SoundEvent sound = SoundsMCA.REAPER_SCYTHE_OUT;

        if (!hasSoul(stack) && target.isDeadOrDying() && (target.getType() == EntitiesMCA.MALE_VILLAGER || target.getType() == EntitiesMCA.FEMALE_VILLAGER)) {
            setSoul(stack, true);
            sound = SoundEvents.BELL_RESONATE;

            if (attacker instanceof ServerPlayer) {
                CriterionMCA.GENERIC_EVENT.trigger((ServerPlayer) attacker, "scytheKill");
            }
        }

        RandomSource r = attacker.level().random;
        attacker.level().playSound(null, attacker.blockPosition(), sound, attacker.getSoundSource(),
                0.75F + r.nextFloat() / 2F,
                0.75F + r.nextFloat() / 2F
        );

        return super.hurtEnemy(stack, target, attacker);
    }

}
