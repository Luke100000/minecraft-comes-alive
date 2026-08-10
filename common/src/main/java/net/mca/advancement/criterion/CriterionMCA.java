package net.mca.advancement.criterion;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;

public interface CriterionMCA {
    BabyCriterion BABY_CRITERION = register(new BabyCriterion());
    BabyDroppedCriterion BABY_DROPPED_CRITERION = register(new BabyDroppedCriterion());
    BabySmeltedCriterion BABY_SMELTED_CRITERION = register(new BabySmeltedCriterion());
    BabySirbenSmeltedCriterion BABY_SIRBEN_SMELTED_CRITERION = register(new BabySirbenSmeltedCriterion());
    HeartsCriterion HEARTS_CRITERION = register(new HeartsCriterion());
    GenericEventCriterion GENERIC_EVENT_CRITERION = register(new GenericEventCriterion());
    ChildAgeStateChangeCriterion CHILD_AGE_STATE_CHANGE = register(new ChildAgeStateChangeCriterion());
    FamilyCriterion FAMILY = register(new FamilyCriterion());
    RankCriterion RANK = register(new RankCriterion());
    VillagerFateCriterion FATE = register(new VillagerFateCriterion());

    static <T extends CriterionTrigger<?>> T register(T obj) {
        return CriteriaTriggers.register(obj);
    }

    static void bootstrap() { }
}
