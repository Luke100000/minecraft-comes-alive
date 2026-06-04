package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class BabyDroppedCriterion extends SimpleCriterionTrigger<BabyDroppedCriterion.TriggerInstance> {
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int c) {
        trigger(player, (conditions) -> conditions.test(c));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  MinMaxBounds.Ints count) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::count)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean test(int c) {
            return count.matches(c);
        }
    }
}
