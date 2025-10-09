package net.mca.advancement.criterion;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.mca.MCA;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class GenericEventCriterion extends SimpleCriterionTrigger<GenericEventCriterion.Conditions> {
    private static final ResourceLocation ID = MCA.locate("generic_event");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Conditions createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext deserializer) {
        String event = json.has("event") ? json.get("event").getAsString() : "";
        return new Conditions(player, event);
    }

    public void trigger(ServerPlayer player, String event) {
        trigger(player, (conditions) -> conditions.test(event));
    }

    public static class Conditions extends AbstractCriterionTriggerInstance {
        private final String event;

        public Conditions(ContextAwarePredicate player, String event) {
            super(GenericEventCriterion.ID, player);
            this.event = event;
        }

        public boolean test(String event) {
            return this.event.equals(event);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext serializer) {
            JsonObject json = super.serializeToJson(serializer);
            json.add("event", new JsonPrimitive(event));
            return json;
        }
    }
}
