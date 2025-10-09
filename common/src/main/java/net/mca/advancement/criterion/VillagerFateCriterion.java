package net.mca.advancement.criterion;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.mca.MCA;
import net.mca.resources.Rank;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class VillagerFateCriterion extends SimpleCriterionTrigger<VillagerFateCriterion.Conditions> {
    private static final ResourceLocation ID = MCA.locate("villager_fate");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public VillagerFateCriterion.Conditions createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext deserializer) {
        Rank userRelation = Rank.fromName(json.get("user_relation").getAsString());
        ResourceLocation cause = ResourceLocation.tryParse(json.get("cause").getAsString());
        return new Conditions(player, cause, userRelation);
    }

    public void trigger(ServerPlayer player, ResourceLocation cause, Rank userRelation) {
        trigger(player, (conditions) -> conditions.test(cause, userRelation));
    }

    public static class Conditions extends AbstractCriterionTriggerInstance {
        private final Rank userRelation;
        private final ResourceLocation cause;

        public Conditions(ContextAwarePredicate player, ResourceLocation cause, Rank userRelation) {
            super(VillagerFateCriterion.ID, player);
            this.userRelation = userRelation;
            this.cause = cause;
        }

        public boolean test(ResourceLocation cause, Rank userRelation) {
            return this.cause.toString().equals(cause.toString()) && userRelation.isAtLeast(this.userRelation);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext serializer) {
            JsonObject json = super.serializeToJson(serializer);
            json.add("cause", new JsonPrimitive(cause.toString()));
            json.add("user_relation", new JsonPrimitive(userRelation.name()));
            return json;
        }
    }
}
