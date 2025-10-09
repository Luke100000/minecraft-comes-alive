package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.MCA;
import net.conczin.mca.registry.CriterionMCA;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class HeartsCriterion extends SimpleCriterionTrigger<HeartsCriterion.TriggerInstance> {
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int hearts, int increase, String source) {
        trigger(player, (conditions) -> conditions.test(hearts, increase, source));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints hearts,
                                  MinMaxBounds.Ints increase,
                                  String source) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("hearts", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::hearts),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("increase", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::increase),
                        Codec.STRING.optionalFieldOf("source", "").forGetter(TriggerInstance::source)
                ).apply(instance, TriggerInstance::new)
        );

        public static Criterion<TriggerInstance> hearts(MinMaxBounds.Ints hearts, MinMaxBounds.Ints increase, String source) {
            return CriterionMCA.HEARTS.createCriterion(new TriggerInstance(Optional.empty(), hearts, increase, source));
        }

        public boolean test(int hearts, int increase, String source) {
            return this.hearts.matches(hearts) && this.increase.matches(increase)
                   && (MCA.isBlankString(this.source) || this.source.equals(source));
        }
    }
}
