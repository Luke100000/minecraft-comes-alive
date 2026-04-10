package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.resources.Rank;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class RankCriterion extends SimpleCriterionTrigger<RankCriterion.TriggerInstance> {
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Rank rank) {
        trigger(player, (conditions) -> conditions.test(rank));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  Rank rank) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Codec.STRING.xmap(Rank::fromName, Rank::name).fieldOf("rank").forGetter(TriggerInstance::rank)
                ).apply(instance, TriggerInstance::new)
        );

        public static Criterion<TriggerInstance> rank(Rank rank) {
            return CriterionMCA.RANK.createCriterion(new TriggerInstance(Optional.empty(), rank));
        }

        public boolean test(Rank rank) {
            return this.rank == rank;
        }
    }
}
