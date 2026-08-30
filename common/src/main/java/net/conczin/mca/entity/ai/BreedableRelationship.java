package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.Status;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.gifts.GiftType;
import net.conczin.mca.entity.interaction.gifts.Response;
import net.conczin.mca.item.SpecialCaseGift;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.AnalysisResults;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.resources.data.Analysis;
import net.conczin.mca.util.InventoryUtils;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;

import java.util.Optional;

/**
 * I know you, you know me, we're all a big happy family.
 */
public class BreedableRelationship extends Relationship<VillagerEntityMCA> {

    private static final CDataParameter<Boolean> IS_PROCREATING = CParameter.create("IsProcreating", false);
    private static final CDataParameter<Integer> LAST_PROCREATION = CParameter.create("LastProcreation", 0);
    private final Pregnancy pregnancy;
    private int procreateTick = -1;

    public BreedableRelationship(VillagerEntityMCA entity) {
        super(entity);
        pregnancy = new Pregnancy(entity);
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return Relationship.createTrackedData(builder)
                .addAll(IS_PROCREATING, LAST_PROCREATION)
                .add(Pregnancy::createTrackedData);
    }

    public Pregnancy getPregnancy() {
        return pregnancy;
    }

    public boolean isProcreating() {
        return entity.getTrackedValue(IS_PROCREATING);
    }

    public boolean mayProcreateAgain(long time) {
        int intTime = (int) time;
        Integer trackedValue = entity.getTrackedValue(LAST_PROCREATION);
        int delta = intTime - trackedValue;
        return trackedValue == 0 || delta < 0 || delta > Config.getInstance().procreationCooldown;
    }

    public void startProcreating(long time) {
        procreateTick = 60;
        entity.setTrackedValue(IS_PROCREATING, true);
        entity.setTrackedValue(LAST_PROCREATION, (int) time);
    }

    public void tick(int age) {
        if (age % 20 == 0) {
            pregnancy.tick();
        }

        if (!isProcreating()) {
            return;
        }

        if (procreateTick > 0) {
            procreateTick--;
            entity.getNavigation().stop();
            entity.level().broadcastEntityEvent(entity, Status.VILLAGER_HEARTS);
        } else {
            getFamilyTree().getOrCreate(entity);
            getPartner().ifPresent(spouse -> {
                pregnancy.procreate(spouse);

                entity.setTrackedValue(IS_PROCREATING, false);
            });
        }
    }

    public void giveGift(ServerPlayer player, Memories memory) {
        ItemStack stack = player.getMainHandItem();

        if (!stack.isEmpty() && !handleSpecialCaseGift(player, stack)) {
            Optional<GiftType> gift = GiftType.bestMatching(entity, stack, player);

            // gift is unknown
            if (gift.isPresent()) {
                acceptGift(stack, gift.get(), player, memory);
            } else {
                gift = handleDynamicGift(stack);
                if (gift.isPresent()) {
                    acceptGift(stack, gift.get(), player, memory);
                } else {
                    rejectGift(player, "gift.fail");
                }
            }
        }
    }

    //returns estimated values for common item types, which the villager could use
    private Optional<GiftType> handleDynamicGift(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.has(DataComponents.WEAPON)) {
            double satisfaction = InventoryUtils.approximateDamage(stack, entity);
            satisfaction = (float) (Math.pow(satisfaction, 1.25) * 2);
            return Optional.of(new GiftType(item, (int) satisfaction, MCA.locate("swords")));
        }

        if (item instanceof ProjectileWeaponItem ranged) {
            float satisfaction = ranged.getDefaultProjectileRange();
            satisfaction = (float) (Math.pow(satisfaction, 1.25) * 2);
            return Optional.of(new GiftType(item, (int) satisfaction, MCA.locate("archery")));
        }

        if (stack.has(DataComponents.TOOL)) {
            var tool = stack.get(DataComponents.TOOL);
            float satisfaction = tool != null ? tool.defaultMiningSpeed() : 0.0F;
            satisfaction = (float) (Math.pow(satisfaction, 1.25) * 2);
            return Optional.of(new GiftType(item, (int) satisfaction, MCA.locate(
                    item instanceof AxeItem ? "swords" :
                            item instanceof HoeItem ? "hoes" :
                                    item instanceof ShovelItem ? "shovels" :
                                            "pickaxes"
            )));
        }

        if (stack.has(DataComponents.EQUIPPABLE)) {
            var equippable = stack.get(DataComponents.EQUIPPABLE);
            int satisfaction = equippable != null ? (int) (Math.max(1.0F, stack.getMaxDamage() / 10.0F) * 1.5F) : 0;
            return Optional.of(new GiftType(item, satisfaction, MCA.locate("armor")));
        }

        FoodProperties component = stack.get(DataComponents.FOOD);
        if (component != null) {
            int satisfaction = (int) (component.nutrition() + component.saturation() * 3);
            return Optional.of(new GiftType(item, satisfaction, MCA.locate("food")));
        }
        return Optional.empty();
    }

    private void acceptGift(ItemStack stack, GiftType gift, ServerPlayer player, Memories memory) {
        // inventory full
        if (!entity.getInventory().canAddItem(stack)) {
            rejectGift(player, "villager.inventory.full");
            return;
        }

        Analysis analysis = gift.getSatisfactionFor(entity, stack, player);
        int satisfaction = analysis.getTotal();
        Response response = gift.getResponse(satisfaction);

        // desaturation
        int occurrences = getGiftSaturation().get(stack);
        int penalty = (int) (occurrences * Config.getInstance().giftDesaturationFactor * Math.pow(Math.max(satisfaction, 0.0), Config.getInstance().giftDesaturationExponent));
        if (penalty != 0) {
            analysis.add("desaturation", -penalty);
        }
        int desaturatedSatisfaction = analysis.getTotal();
        Response desaturatedResponse = gift.getResponse(desaturatedSatisfaction);

        // adjust reward
        desaturatedSatisfaction = (int) (desaturatedSatisfaction * Config.getInstance().giftSatisfactionFactor);

        Network.sendToPlayer(new AnalysisResults(analysis), player);

        if (response == Response.FAIL) {
            rejectGift(player, gift.getDialogueFor(response));
        } else if (desaturatedResponse == Response.FAIL) {
            rejectGift(player, "gift.saturated");
        } else {
            entity.sendChatMessage(player, gift.getDialogueFor(response));
            if (response == Response.BEST) {
                entity.playSurprisedSound();
            }

            //take the gift
            getGiftSaturation().add(stack);
            entity.level().broadcastEntityEvent(entity, Status.MCA_VILLAGER_POS_INTERACTION);
            entity.getInventory().addItem(stack.split(1));
        }

        //modify mood and hearts
        entity.getVillagerBrain().modifyMoodValue((int) (desaturatedSatisfaction * Config.getInstance().giftMoodEffect + Config.getInstance().baseGiftMoodEffect * Mth.sign(desaturatedSatisfaction)));
        CriterionMCA.HEARTS.trigger(player, memory.getHearts(), desaturatedSatisfaction, "gift");
        memory.modHearts(desaturatedSatisfaction);
    }

    private void rejectGift(Player player, String dialogue) {
        entity.level().broadcastEntityEvent(entity, Status.MCA_VILLAGER_NEG_INTERACTION);
        entity.sendChatMessage(player, dialogue);
    }

    private boolean handleSpecialCaseGift(ServerPlayer player, ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof SpecialCaseGift specialCaseGift) {
            InteractionResult result = specialCaseGift.handle(player, entity);
            if (result == InteractionResult.CONSUME) {
                stack.shrink(1);
            }
            return result != InteractionResult.PASS;
        }

        if (item == Items.CAKE && !entity.isBaby()) {
            if (pregnancy.tryStartGestation()) {
                player.level().broadcastEntityEvent(entity, Status.VILLAGER_HEARTS);
                stack.shrink(1);
                entity.sendChatMessage(player, "gift.cake.success");
            } else {
                entity.sendChatMessage(player, "gift.cake.fail");
            }

            return true;
        }

        if (item == Items.GOLDEN_APPLE && entity.isInfected()) {
            entity.setInfected(false);
            entity.ate();
            return true;
        }

        if (item instanceof DyeItem dye) {
            DyeColor dyeColor = stack.get(DataComponents.DYE);
            if (dyeColor != null) {
                entity.setHairDye(dyeColor);
            }
            stack.shrink(1);
            return true;
        }

        if (item == Items.WET_SPONGE) {
            entity.clearHairDye();
            stack.shrink(1);
            return true;
        }

        if (entity.canToggleAgeLockWith(stack)) {
            AgeableMob.setAgeLocked(
                    entity,
                    entity::isAgeLocked,
                    player,
                    stack,
                    mob -> entity.toggleAgeLockedAtCurrentAge()
            );
            return true;
        }

        if (item == Items.GOLDEN_APPLE && entity.isBaby()) {
            // increase age by 20 minutes
            entity.ageUp(1200 * 20);
            stack.shrink(1);
            return true;
        }

        return false;
    }
}
