package net.mca.entity.ai;

import net.mca.Config;
import net.mca.MCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.Status;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.interaction.gifts.GiftType;
import net.mca.entity.interaction.gifts.Response;
import net.mca.item.SpecialCaseGift;
import net.mca.network.s2c.AnalysisResults;
import net.mca.resources.data.analysis.IntAnalysis;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CDataParameter;
import net.mca.util.network.datasync.CParameter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;

import java.util.Optional;

/**
 * I know you, you know me, we're all a big happy family.
 */
public class BreedableRelationship extends Relationship<VillagerEntityMCA> {

    private static final CDataParameter<Boolean> IS_PROCREATING = CParameter.create("isProcreating", false);
    private static final CDataParameter<Integer> LAST_PROCREATION = CParameter.create("lastProcreation", 0);

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return Relationship.createTrackedData(builder)
                .addAll(IS_PROCREATING, LAST_PROCREATION)
                .add(Pregnancy::createTrackedData);
    }

    private int procreateTick = -1;

    private final Pregnancy pregnancy;

    public BreedableRelationship(VillagerEntityMCA entity) {
        super(entity);
        pregnancy = new Pregnancy(entity);
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
            entity.getWorld().sendEntityStatus(entity, Status.VILLAGER_HEARTS);
        } else {
            getFamilyTree().getOrCreate(entity);
            getPartner().ifPresent(spouse -> {
                pregnancy.procreate(spouse);

                entity.setTrackedValue(IS_PROCREATING, false);
            });
        }
    }

    public void giveGift(ServerPlayerEntity player, Memories memory) {
        ItemStack stack = player.getMainHandStack();

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
        if (stack.getItem() instanceof SwordItem sword) {
            //swords
            float satisfaction = sword.getAttackDamage();
            satisfaction = (float) (Math.pow(satisfaction, 1.25) * 2);
            return Optional.of(new GiftType(stack.getItem(), (int) satisfaction, MCA.locate("swords")));
        } else if (stack.getItem() instanceof RangedWeaponItem ranged) {
            //ranged weapons
            float satisfaction = ranged.getRange();
            satisfaction = (float) (Math.pow(satisfaction, 1.25) * 2);
            return Optional.of(new GiftType(stack.getItem(), (int) satisfaction, MCA.locate("archery")));
        } else if (stack.getItem() instanceof ToolItem tool) {
            //tools
            float satisfaction = tool.getMaterial().getMiningSpeedMultiplier();
            satisfaction = (float) (Math.pow(satisfaction, 1.25) * 2);
            return Optional.of(new GiftType(stack.getItem(), (int) satisfaction, MCA.locate(
                    stack.getItem() instanceof AxeItem ? "swords" :
                            stack.getItem() instanceof HoeItem ? "hoes" :
                                    stack.getItem() instanceof ShovelItem ? "shovels" :
                                            "pickaxes"
            )));
        } else if (stack.getItem() instanceof ArmorItem armor) {
            //armor
            int satisfaction = (int) (Math.pow(armor.getProtection(), 1.25) * 1.5 + armor.getMaterial().getToughness() * 5);
            return Optional.of(new GiftType(stack.getItem(), satisfaction, MCA.locate("armor")));
        } else if (stack.getItem().isFood()) {
            //food
            FoodComponent component = stack.getItem().getFoodComponent();
            if (component != null) {
                int satisfaction = (int) (component.getHunger() + component.getSaturationModifier() * 3);
                return Optional.of(new GiftType(stack.getItem(), satisfaction, MCA.locate("food")));
            }
        }
        return Optional.empty();
    }

    private void acceptGift(ItemStack stack, GiftType gift, ServerPlayerEntity player, Memories memory) {
        // inventory full
        if (!entity.getInventory().canInsert(stack)) {
            rejectGift(player, "villager.inventory.full");
            return;
        }

        IntAnalysis analysis = gift.getSatisfactionFor(entity, stack, player);
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
        desaturatedSatisfaction *= Config.getInstance().giftSatisfactionFactor;

        NetworkHandler.sendToPlayer(new AnalysisResults(analysis), player);

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
            entity.getWorld().sendEntityStatus(entity, Status.MCA_VILLAGER_POS_INTERACTION);
            entity.getInventory().addStack(stack.split(1));
        }

        //modify mood and hearts
        entity.getVillagerBrain().modifyMoodValue((int) (desaturatedSatisfaction * Config.getInstance().giftMoodEffect + Config.getInstance().baseGiftMoodEffect * MathHelper.sign(desaturatedSatisfaction)));
        CriterionMCA.HEARTS_CRITERION.trigger(player, memory.getHearts(), desaturatedSatisfaction, "gift");
        memory.modHearts(desaturatedSatisfaction);
    }

    private void rejectGift(PlayerEntity player, String dialogue) {
        entity.getWorld().sendEntityStatus(entity, Status.MCA_VILLAGER_NEG_INTERACTION);
        entity.sendChatMessage(player, dialogue);
    }

    private boolean handleSpecialCaseGift(ServerPlayerEntity player, ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof SpecialCaseGift specialCaseGift) {
            ActionResult result = specialCaseGift.handle(player, entity);
            if (result == ActionResult.CONSUME) {
                stack.decrement(1);
            }
            return result != ActionResult.PASS;
        }

        if (item == Items.CAKE && !entity.isBaby()) {
            if (pregnancy.tryStartGestation()) {
                player.getWorld().sendEntityStatus(entity, Status.VILLAGER_HEARTS);
                stack.decrement(1);
                entity.sendChatMessage(player, "gift.cake.success");
            } else {
                entity.sendChatMessage(player, "gift.cake.fail");
            }

            return true;
        }

        if (item == Items.GOLDEN_APPLE && entity.isInfected()) {
            entity.setInfected(false);
            entity.eatFood(entity.getWorld(), stack);
            return true;
        }

        if (item instanceof DyeItem dye) {
            entity.setHairDye(dye.getColor());
            stack.decrement(1);
            return true;
        }

        if (item == Items.WET_SPONGE) {
            entity.clearHairDye();
            stack.decrement(1);
            return true;
        }

        if (item == Items.NAME_TAG) {
            if (stack.hasCustomName()) {
                entity.setCustomSkin(stack.getName().getString());
            } else {
                entity.setCustomSkin("");
            }
            stack.decrement(1);
            return true;
        }

        if (item == Items.GOLDEN_APPLE && entity.isBaby()) {
            // increase age by 20 minutes
            entity.growUp(1200 * 20);
            stack.decrement(1);
            return true;
        }

        return false;
    }
}
