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

public class RankCriterion extends SimpleCriterionTrigger<RankCriterion.Conditions> {
    private static final ResourceLocation ID = MCA.locate("rank");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Conditions createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext deserializer) {
        Rank rank = Rank.fromName(json.get("rank").getAsString());
        return new Conditions(player, rank);
    }

    public void trigger(ServerPlayer player, Rank rank) {
        trigger(player, (conditions) -> conditions.test(rank));
    }

    public static class Conditions extends AbstractCriterionTriggerInstance {
        private final Rank rank;

        public Conditions(ContextAwarePredicate player, Rank rank) {
            super(RankCriterion.ID, player);
            this.rank = rank;
        }

        public boolean test(Rank rank) {
            return this.rank == rank;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext serializer) {
            JsonObject json = super.serializeToJson(serializer);
            json.add("rank", new JsonPrimitive(rank.name()));
            return json;
        }
    }
}
