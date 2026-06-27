package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class GenericEventCriterion extends SimpleCriterionTrigger<GenericEventCriterion.TriggerInstance> {
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String event) {
        trigger(player, conditions -> conditions.test(event));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  String event) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Codec.STRING.optionalFieldOf("event", "").forGetter(TriggerInstance::event)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean test(String event) {
            return this.event.equals(event);
        }
    }
}
