package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class FamilyCriterion extends SimpleCriterionTrigger<FamilyCriterion.TriggerInstance> {
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        FamilyTreeNode familyTree = FamilyTree.get((ServerLevel) player.level()).getOrCreate(player);
        long c = familyTree.getRelatives(0, 1).count();
        long gc = familyTree.getRelatives(0, 2).count() - c;

        trigger(player, condition -> condition.test((int) c, (int) gc));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints children,
                                  MinMaxBounds.Ints grandchildren) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("children", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::children),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("grandchildren", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::grandchildren)
                ).apply(instance, TriggerInstance::new)
        );

        public static Criterion<TriggerInstance> family(MinMaxBounds.Ints children, MinMaxBounds.Ints grandchildren) {
            return CriterionMCA.FAMILY.createCriterion(new TriggerInstance(Optional.empty(), children, grandchildren));
        }

        public boolean test(int c, int gc) {
            return children.matches(c) && grandchildren.matches(gc);
        }
    }
}
