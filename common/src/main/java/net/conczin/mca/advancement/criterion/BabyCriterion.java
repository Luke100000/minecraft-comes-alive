package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.registry.CriterionMCA;
import net.minecraft.core.Holder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class BabyCriterion extends SimpleCriterionTrigger<BabyCriterion.TriggerInstance> {
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int c) {
        trigger(player, (conditions) -> conditions.test(c));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player,
                                  MinMaxBounds.Ints count) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::count)
                ).apply(instance, TriggerInstance::new)
        );

        public static Criterion<TriggerInstance> baby(MinMaxBounds.Ints count) {
            return CriterionMCA.BABY.createCriterion(new TriggerInstance(Optional.empty(), count));
        }

        public boolean test(int c) {
            return count.matches(c);
        }
    }
}
