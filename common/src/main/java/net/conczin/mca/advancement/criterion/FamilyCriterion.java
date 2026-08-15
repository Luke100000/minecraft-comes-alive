package net.conczin.mca.advancement.criterion;

import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class FamilyCriterion extends SimpleCriterionTrigger<FamilyCriterion.Conditions> {
    private static final ResourceLocation ID = MCA.locate("family");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Conditions createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext deserializer) {
        // quite limited, but I do not assume any more use cases
        MinMaxBounds.Ints c = MinMaxBounds.Ints.fromJson(json.get("children"));
        MinMaxBounds.Ints gc = MinMaxBounds.Ints.fromJson(json.get("grandchildren"));
        return new Conditions(player, c, gc);
    }

    public void trigger(ServerPlayer player) {
        FamilyTreeNode familyTree = FamilyTree.get(player.serverLevel()).getOrCreate(player);
        long c = familyTree.getRelatives(0, 1).count();
        long gc = familyTree.getRelatives(0, 2).count() - c;

        trigger(player, condition -> condition.test((int)c, (int)gc));
    }

    public static class Conditions extends AbstractCriterionTriggerInstance {
        private final MinMaxBounds.Ints children;
        private final MinMaxBounds.Ints grandchildren;

        public Conditions(ContextAwarePredicate player, MinMaxBounds.Ints children, MinMaxBounds.Ints grandchildren) {
            super(FamilyCriterion.ID, player);
            this.children = children;
            this.grandchildren = grandchildren;
        }

        public boolean test(int c, int gc) {
            return children.matches(c) && grandchildren.matches(gc);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext serializer) {
            JsonObject json = super.serializeToJson(serializer);
            json.add("children", children.serializeToJson());
            json.add("grandchildren", grandchildren.serializeToJson());
            return json;
        }
    }
}
