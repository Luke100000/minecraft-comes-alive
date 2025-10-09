package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.resources.Rank;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class VillagerFateCriterion extends SimpleCriterionTrigger<VillagerFateCriterion.TriggerInstance> {
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation cause, Rank userRelation) {
        trigger(player, (conditions) -> conditions.test(cause, userRelation));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, ResourceLocation cause,
                                  Rank userRelation) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        ResourceLocation.CODEC.fieldOf("cause").forGetter(TriggerInstance::cause),
                        Codec.STRING.xmap(Rank::fromName, Rank::name).fieldOf("user_relation").forGetter(TriggerInstance::userRelation)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean test(ResourceLocation cause, Rank userRelation) {
            return this.cause.toString().equals(cause.toString()) && userRelation.isAtLeast(this.userRelation);
        }
    }
}
