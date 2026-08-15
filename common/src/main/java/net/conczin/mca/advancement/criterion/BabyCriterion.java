package net.conczin.mca.advancement.criterion;

import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class BabyCriterion extends SimpleCriterionTrigger<BabyCriterion.Conditions> {
    private static final ResourceLocation ID = MCA.locate("baby");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Conditions createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext deserializer) {
        MinMaxBounds.Ints c = MinMaxBounds.Ints.fromJson(json.get("count"));
        return new Conditions(player, c);
    }

    public void trigger(ServerPlayer player, int c) {
        trigger(player, (conditions) -> conditions.test(c));
    }

    public static class Conditions extends AbstractCriterionTriggerInstance {
        private final MinMaxBounds.Ints count;

        public Conditions(ContextAwarePredicate player, MinMaxBounds.Ints count) {
            super(BabyCriterion.ID, player);
            this.count = count;
        }

        public boolean test(int c) {
            return count.matches(c);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext serializer) {
            JsonObject json = super.serializeToJson(serializer);
            json.add("count", count.serializeToJson());
            return json;
        }
    }
}
