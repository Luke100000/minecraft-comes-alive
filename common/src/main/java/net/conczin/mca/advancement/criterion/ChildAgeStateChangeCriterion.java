package net.conczin.mca.advancement.criterion;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.conczin.mca.MCA;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ChildAgeStateChangeCriterion extends SimpleCriterionTrigger<ChildAgeStateChangeCriterion.Conditions> {
    private static final ResourceLocation ID = MCA.locate("child_age_state_change");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Conditions createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext deserializer) {
        String event = json.has("state") ? json.get("state").getAsString() : "";
        return new Conditions(player, event);
    }

    public void trigger(ServerPlayer player, String event) {
        trigger(player, (conditions) -> conditions.test(event));
    }

    public static class Conditions extends AbstractCriterionTriggerInstance {
        private final String event;

        public Conditions(ContextAwarePredicate player, String event) {
            super(ChildAgeStateChangeCriterion.ID, player);
            this.event = event;
        }

        public boolean test(String event) {
            return this.event.equals(event);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext serializer) {
            JsonObject json = super.serializeToJson(serializer);
            json.add("state", new JsonPrimitive(event));
            return json;
        }
    }
}
