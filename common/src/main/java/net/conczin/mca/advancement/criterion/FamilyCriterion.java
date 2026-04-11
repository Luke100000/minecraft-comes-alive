package net.conczin.mca.advancement.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.advancements.criterion.MinMaxBounds.Ints;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.server.level.ServerPlayer;

public class FamilyCriterion extends SimpleCriterionTrigger<FamilyCriterion.TriggerInstance> {
   public Codec<FamilyCriterion.TriggerInstance> codec() {
      return FamilyCriterion.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      FamilyTreeNode familyTree = FamilyTree.get(player.level()).getOrCreate(player);
      long c = familyTree.getRelatives(0, 1).count();
      long gc = familyTree.getRelatives(0, 2).count() - c;
      this.trigger(player, condition -> condition.test((int)c, (int)gc));
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player, Ints children, Ints grandchildren) implements SimpleInstance {
      public static final Codec<FamilyCriterion.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(triggerInstance -> triggerInstance.player()),
               Ints.CODEC.optionalFieldOf("children", Ints.ANY).forGetter(triggerInstance -> triggerInstance.children()),
               Ints.CODEC.optionalFieldOf("grandchildren", Ints.ANY).forGetter(triggerInstance -> triggerInstance.grandchildren())
            )
            .apply(instance, (player, children, grandchildren) -> new FamilyCriterion.TriggerInstance(player, children, grandchildren))
      );

      public static Criterion<FamilyCriterion.TriggerInstance> family(Ints children, Ints grandchildren) {
         return CriterionMCA.FAMILY.createCriterion(new FamilyCriterion.TriggerInstance(Optional.empty(), children, grandchildren));
      }

      public boolean test(int c, int gc) {
         return this.children.matches(c) && this.grandchildren.matches(gc);
      }
   }
}
