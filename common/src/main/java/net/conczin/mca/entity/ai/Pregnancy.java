package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.util.WorldUtils;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The progenator. Preg-genator? Preg-genator.
 */
public class Pregnancy {
    private static final CDataParameter<Boolean> HAS_BABY = CParameter.create("HasBaby", false);
    private static final CDataParameter<Boolean> IS_BABY_MALE = CParameter.create("IsBabyMale", false);
    private static final CDataParameter<Integer> BABY_AGE = CParameter.create("BabyAge", 0);
    private final VillagerEntityMCA mother;

    Pregnancy(VillagerEntityMCA entity) {
        this.mother = entity;
    }

    public static void forEachTrackedParameter(Consumer<CParameter<?, ?>> consumer) {
        consumer.accept(HAS_BABY);
        consumer.accept(IS_BABY_MALE);
        consumer.accept(BABY_AGE);
    }

    public boolean isPregnant() {
        return mother.getTrackedValue(HAS_BABY);
    }

    public void setPregnant(boolean pregnant) {
        mother.setTrackedValue(HAS_BABY, pregnant);
    }

    public int getBabyAge() {
        return mother.getTrackedValue(BABY_AGE);
    }

    public void setBabyAge(int age) {
        mother.setTrackedValue(BABY_AGE, age);
    }

    public Gender getGender() {
        return mother.getTrackedValue(IS_BABY_MALE) ? Gender.MALE : Gender.FEMALE;
    }

    public void tick() {
        if (!isPregnant()) {
            return;
        }

        setBabyAge(getBabyAge() + 60);

        if (getBabyAge() < Config.getInstance().babyItemGrowUpTime) {
            return;
        }

        setBabyAge(0);
        getFather().ifPresent(father -> {
            setPregnant(false);

            VillagerEntityMCA child = createChild(getGender(), father);

            child.setPos(mother.getX(), mother.getY(), mother.getZ());
            WorldUtils.spawnEntity(mother.level(), child, EntitySpawnReason.BREEDING);
        });
    }

    public boolean tryStartGestation() {
        // You can't get double-pregnant
        if (isPregnant()) {
            return false;
        }

        return getFather().map(father -> {
            // In case we're the father, impregnate the other
            if (mother.getGenetics().getGender() == Gender.MALE && father.getGenetics().getGender() != Gender.MALE) {
                return father.getRelationships().getPregnancy().tryStartGestation();
            }

            setPregnant(true);
            mother.setTrackedValue(IS_BABY_MALE, mother.level().random.nextBoolean());
            return true;
        }).orElse(false);
    }

    public VillagerEntityMCA createChild(Gender gender, VillagerEntityMCA partner) {
        VillagerEntityMCA child = Objects.requireNonNull(gender.getVillagerType().create(mother.level(), EntitySpawnReason.BREEDING));

        child.getGenetics().combine(partner.getGenetics(), mother.getGenetics());
        child.getTraits().inherit(partner.getTraits());
        child.getTraits().inherit(mother.getTraits());
        child.setBaby(true);
        child.setAgeState(AgeState.TODDLER);
        child.getRelationships().getFamilyEntry().assignParents(mother.getRelationships(), partner.getRelationships());

        // advancement
        child.getRelationships().getFamily(2, 0)
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .forEach(CriterionMCA.FAMILY::trigger);

        // civil entry
        mother.getResidency().getHomeVillage().flatMap(Village::getCivilRegistry).ifPresent(r -> r.addText(Component.translatable("events.baby", mother.getName(), partner.getName())));

        return child;
    }

    public VillagerEntityMCA createChild(Gender gender) {
        return createChild(gender, mother);
    }

    private Optional<VillagerEntityMCA> getFather() {
        return mother.getRelationships().getPartner()
                .filter(VillagerEntityMCA.class::isInstance)
                .map(VillagerEntityMCA.class::cast);
    }

    public void procreate(Entity spouse) {
        RandomSource random = mother.getRandom();

        //make sure this villager is registered in the family tree
        int count = 1;
        while (random.nextFloat() < Config.getInstance().twinBabyChance && count < 8) {
            count++;
        }

        // advancement
        if (spouse instanceof ServerPlayer player) {
            CriterionMCA.BABY.trigger(player, count);
        }

        long seed = random.nextLong();
        for (int i = 0; i < count; i++) {
            boolean flip = mother.getGenetics().getGender() == Gender.MALE;
            ItemStack stack = BabyItem.createItem(flip ? spouse : mother, flip ? mother : spouse, seed);
            if (!(spouse instanceof Player player && player.addItem(stack))) {
                mother.getInventory().addItem(stack);
            }
        }
    }
}
