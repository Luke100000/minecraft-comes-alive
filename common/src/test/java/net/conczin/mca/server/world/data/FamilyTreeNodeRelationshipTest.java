package net.conczin.mca.server.world.data;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeNodeRelationshipTest {
    private static final UUID PERSON = uuid(1);
    private static final UUID PARTNER = uuid(2);

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void widowKeepsHistoricalPartnerButNoLongerHasActivePartner() {
        FamilyTreeNode person = node(PERSON);
        FamilyTreeNode partner = node(PARTNER);
        person.updatePartner(partner);

        assertEquals(PARTNER, person.activePartner().orElseThrow());

        person.updatePartner(null, RelationshipState.WIDOW);

        assertEquals(PARTNER, person.partner());
        assertEquals(RelationshipState.WIDOW, person.getRelationshipState());
        assertTrue(person.activePartner().isEmpty());
    }

    @Test
    void malformedSavedProfessionFallsBackToNone() {
        CompoundTag saved = node(PERSON).save();
        saved.putString("profession", "invalid profession!");

        FamilyTreeNode restored = new FamilyTreeNode(null, saved);

        assertEquals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.NONE), restored.getProfessionId());
        assertEquals(VillagerProfession.NONE, restored.getProfession());
        assertEquals("mca.none", restored.getProfessionName());
        assertEquals("entity.minecraft.villager.mca.none", restored.getProfessionText().getString());
    }

    private static FamilyTreeNode node(UUID id) {
        return new FamilyTreeNode(
                null,
                id,
                "Person",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
    }
}
