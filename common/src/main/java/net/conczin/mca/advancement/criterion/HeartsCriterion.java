package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.conczin.mca.MCA;
import net.conczin.mca.registry.CriterionMCA;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.advancements.criterion.MinMaxBounds.Ints;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.server.level.ServerPlayer;

public class HeartsCriterion extends SimpleCriterionTrigger<HeartsCriterion.TriggerInstance> {
   public Codec<HeartsCriterion.TriggerInstance> codec() {
      return HeartsCriterion.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player, int hearts, int increase, String source) {
      this.trigger(player, conditions -> conditions.test(hearts, increase, source));
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player, Ints hearts, Ints increase, String source) implements SimpleInstance {
      public static final Codec<HeartsCriterion.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(triggerInstance -> triggerInstance.player()),
               Ints.CODEC.optionalFieldOf("hearts", Ints.ANY).forGetter(triggerInstance -> triggerInstance.hearts()),
               Ints.CODEC.optionalFieldOf("increase", Ints.ANY).forGetter(triggerInstance -> triggerInstance.increase()),
               Codec.STRING.optionalFieldOf("source", "").forGetter(triggerInstance -> triggerInstance.source())
            )
            .apply(instance, (player, hearts, increase, source) -> new HeartsCriterion.TriggerInstance(player, hearts, increase, source))
      );

      public static Criterion<HeartsCriterion.TriggerInstance> hearts(Ints hearts, Ints increase, String source) {
         return CriterionMCA.HEARTS.createCriterion(new HeartsCriterion.TriggerInstance(Optional.empty(), hearts, increase, source));
      }

      public boolean test(int hearts, int increase, String source) {
         return this.hearts.matches(hearts) && this.increase.matches(increase) && (MCA.isBlankString(this.source) || this.source.equals(source));
      }
   }
}
