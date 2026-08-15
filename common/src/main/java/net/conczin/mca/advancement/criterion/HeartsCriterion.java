package net.conczin.mca.advancement.criterion;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.conczin.mca.MCA;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class HeartsCriterion extends SimpleCriterionTrigger<HeartsCriterion.Conditions> {
    private static final ResourceLocation ID = MCA.locate("hearts");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Conditions createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext deserializer) {
        MinMaxBounds.Ints hearts = MinMaxBounds.Ints.fromJson(json.get("hearts"));
        MinMaxBounds.Ints increase = MinMaxBounds.Ints.fromJson(json.get("increase"));
        String source = json.has("source") ? json.get("source").getAsString() : "";
        return new Conditions(player, hearts, increase, source);
    }

    public void trigger(ServerPlayer player, int hearts, int increase, String source) {
        trigger(player, (conditions) -> conditions.test(hearts, increase, source));
    }

    public static class Conditions extends AbstractCriterionTriggerInstance {
        private final MinMaxBounds.Ints hearts;
        private final MinMaxBounds.Ints increase;
        private final String source;

        public Conditions(ContextAwarePredicate player, MinMaxBounds.Ints hearts, MinMaxBounds.Ints increase, String source) {
            super(HeartsCriterion.ID, player);
            this.hearts = hearts;
            this.increase = increase;
            this.source = source;
        }

        public boolean test(int hearts, int increase, String source) {
            return this.hearts.matches(hearts) && this.increase.matches(increase)
                    && (MCA.isBlankString(this.source) || this.source.equals(source));
        }

        @Override
        public JsonObject serializeToJson(SerializationContext serializer) {
            JsonObject json = super.serializeToJson(serializer);
            json.add("hearts", hearts.serializeToJson());
            json.add("increase", increase.serializeToJson());
            json.add("source", new JsonPrimitive(source));
            return json;
        }
    }
}
