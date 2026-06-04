package net.conczin.mca.registry;

import net.conczin.mca.MCA;
import net.conczin.mca.advancement.criterion.*;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public interface CriterionMCA {
    Map<Identifier, CriterionTrigger<?>> TRIGGERS = new HashMap<>();

    BabyCriterion BABY = register("baby", new BabyCriterion());
    BabyDroppedCriterion BABY_DROPPED = register("baby_dropped", new BabyDroppedCriterion());
    BabySmeltedCriterion BABY_SMELTED = register("baby_smelted", new BabySmeltedCriterion());
    BabySirbenSmeltedCriterion BABY_SIRBEN_SMELTED = register("baby_sirben_smelted", new BabySirbenSmeltedCriterion());
    HeartsCriterion HEARTS = register("hearts", new HeartsCriterion());
    GenericEventCriterion GENERIC_EVENT = register("generic_event", new GenericEventCriterion());
    ChildAgeStateChangeCriterion CHILD_AGE_STATE_CHANGE = register("child_age_state_change", new ChildAgeStateChangeCriterion());
    FamilyCriterion FAMILY = register("family", new FamilyCriterion());
    RankCriterion RANK = register("rank", new RankCriterion());
    VillagerFateCriterion FATE = register("villager_fate", new VillagerFateCriterion());

    static <T extends CriterionTrigger<?>> T register(String name, T trigger) {
        TRIGGERS.put(MCA.locate(name), trigger);
        return trigger;
    }

    static void registerCriteria(MCA.RegisterHelper<CriterionTrigger<?>> helper) {
        TRIGGERS.forEach(helper::register);
    }
}
