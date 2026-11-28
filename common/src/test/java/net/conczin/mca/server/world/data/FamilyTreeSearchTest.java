package net.conczin.mca.server.world.data;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static org.junit.jupiter.api.Assertions.*;

class FamilyTreeSearchTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void exactNameSearchRemainsCaseInsensitiveAndExcludesPartialMatches() {
        FamilyTree tree = new FamilyTree(null);
        tree.getOrCreate(uuid(31), "ALICE", Gender.FEMALE);
        tree.getOrCreate(uuid(32), "alice", Gender.FEMALE);
        tree.getOrCreate(uuid(33), "Malice", Gender.MALE);

        assertEquals(
                java.util.Set.of(uuid(31), uuid(32)),
                tree.getAllWithName("aLiCe").map(FamilyTreeNode::id).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void boundedNameSearchPrioritizesPrefixesAndHonorsLimit() {
        FamilyTree tree = new FamilyTree(null);
        tree.getOrCreate(uuid(1), "Malex", Gender.MALE);
        tree.getOrCreate(uuid(2), "Alexandra", Gender.FEMALE);
        tree.getOrCreate(uuid(3), "Alex", Gender.MALE);
        tree.getOrCreate(uuid(4), "Zalex", Gender.FEMALE);

        List<FamilyTreeNode> results = tree.getAllWithNameContaining("alex", 2);

        assertEquals(List.of("Alex", "Alexandra"), results.stream().map(FamilyTreeNode::getName).toList());
    }

    @Test
    void boundedNameSearchKeepsAccentInsensitiveDeterministicOrdering() {
        FamilyTree tree = new FamilyTree(null);
        tree.getOrCreate(uuid(10), "Álex", Gender.MALE);
        tree.getOrCreate(uuid(12), "Alex", Gender.MALE);
        tree.getOrCreate(uuid(11), "Bálex", Gender.FEMALE);

        List<FamilyTreeNode> results = tree.getAllWithNameContaining("alex", 3);

        assertEquals(3, results.size());
        assertEquals(uuid(10), results.get(0).id());
        assertEquals(uuid(12), results.get(1).id());
        assertEquals("Bálex", results.get(2).getName());
        assertTrue(tree.getAllWithNameContaining("   ", 16).isEmpty());
        assertTrue(tree.getAllWithNameContaining("alex", 0).isEmpty());
    }

    @Test
    void normalizedSearchNameIsCachedAndInvalidatedOnSetName() {
        FamilyTree tree = new FamilyTree(null);
        FamilyTreeNode node = tree.getOrCreate(uuid(41), "Émilie", Gender.FEMALE);

        String cached = node.normalizedSearchName();
        assertEquals("emilie", cached);
        assertSame(cached, node.normalizedSearchName(), "Normalized search name must be cached lazily");
        assertEquals(List.of(node), tree.getAllWithNameContaining("emilie", 16));

        node.setName("Zoë");

        assertEquals("zoe", node.normalizedSearchName(), "Renaming a node must invalidate cached normalized name");
        assertTrue(tree.getAllWithNameContaining("emilie", 16).isEmpty());
        assertEquals(List.of(node), tree.getAllWithNameContaining("zoe", 16));
    }
}
