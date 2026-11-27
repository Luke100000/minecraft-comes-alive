package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemoriesParseTest {
    @Test
    void returnsNullForNullEmptyAndMalformedPlayerUuidTags() {
        assertNull(Memories.fromCNBT((VillagerEntityMCA) null, null));
        assertNull(Memories.fromCNBT((VillagerEntityMCA) null, new CompoundTag()));

        CompoundTag missingUuid = new CompoundTag();
        missingUuid.putInt("hearts", 25);
        missingUuid.putInt("interactionFatigue", 3);
        assertNull(Memories.fromCNBT((VillagerEntityMCA) null, missingUuid));

        CompoundTag invalidStringUuid = new CompoundTag();
        invalidStringUuid.putString("playerUUID", "not-a-valid-uuid");
        invalidStringUuid.putInt("hearts", 10);
        assertNull(Memories.fromCNBT((VillagerEntityMCA) null, invalidStringUuid));

        CompoundTag blankStringUuid = new CompoundTag();
        blankStringUuid.putString("playerUUID", "");
        assertNull(Memories.fromCNBT((VillagerEntityMCA) null, blankStringUuid));

        CompoundTag wrongTypeUuid = new CompoundTag();
        wrongTypeUuid.putInt("playerUUID", 12345);
        assertNull(Memories.fromCNBT((VillagerEntityMCA) null, wrongTypeUuid));

        CompoundTag truncatedIntArrayUuid = new CompoundTag();
        truncatedIntArrayUuid.putIntArray("playerUUID", new int[]{1, 2});
        assertNull(Memories.fromCNBT((VillagerEntityMCA) null, truncatedIntArrayUuid));
    }

    @Test
    void parsesValidStringAndIntArrayPlayerUuids() {
        UUID expectedUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putString("playerUUID", expectedUuid.toString());
        legacyTag.putInt("hearts", 40);
        legacyTag.putInt("interactionFatigue", 2);
        legacyTag.putInt("dialogueType", DialogueType.SPOUSE.ordinal());
        legacyTag.putLong("lastSeen", 7L);

        Memories fromLegacy = Memories.fromCNBT((VillagerEntityMCA) null, legacyTag);
        assertNotNull(fromLegacy);
        assertEquals(expectedUuid, fromLegacy.getPlayerUUID());
        assertEquals(40, fromLegacy.getHearts());
        assertEquals(2, fromLegacy.getInteractionFatigue());
        assertEquals(DialogueType.SPOUSE, fromLegacy.getDialogueType());
        assertEquals(7L, fromLegacy.getLastSeen());

        CompoundTag roundTripTag = fromLegacy.toCNBT();
        Memories fromModern = Memories.fromCNBT((VillagerEntityMCA) null, roundTripTag);
        assertNotNull(fromModern);
        assertEquals(expectedUuid, fromModern.getPlayerUUID());
        assertEquals(40, fromModern.getHearts());
        assertEquals(2, fromModern.getInteractionFatigue());
        assertEquals(DialogueType.SPOUSE, fromModern.getDialogueType());
        assertEquals(7L, fromModern.getLastSeen());
    }
}
