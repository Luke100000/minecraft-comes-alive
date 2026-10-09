package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.entity.ai.RecentVillagerEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueRecentEventExtensionTest {
    @Test
    void registeredAddonLifeEventsPersistAndAreAcceptedByDialogueConditions() {
        ResourceLocation id = ResourceLocation.parse("dialogue_test:visited_market");
        RecentVillagerEvents.register(id);
        RecentVillagerEvents original = new RecentVillagerEvents();
        original.record(id, 100L);
        CompoundTag tag = new CompoundTag();
        original.writeToNbt(tag);

        RecentVillagerEvents loaded = new RecentVillagerEvents();
        loaded.readFromNbt(tag);
        assertTrue(loaded.occurredWithin(id, 120L, 30L));
        assertTrue(DialogueCondition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"mca:recent_event\",\"event\":\"dialogue_test:visited_market\",\"within_ticks\":30}"))
                .result().isPresent());
    }
}
