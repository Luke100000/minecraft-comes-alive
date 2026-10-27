package net.conczin.mca.entity.ai;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class TraitsLookupTest {
    @BeforeAll
    static void bootstrap() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void membershipReadsTheCurrentTrackedTagAndAcceptsConfiguredNames() {
        TrackedTraits state = new TrackedTraits();
        Traits traits = new Traits(state.villager());
        assertFalse(traits.hasTrait(Traits.ALBINISM));
        assertFalse(traits.hasTrait("ALBINISM"));

        CompoundTag updated = new CompoundTag();
        updated.putBoolean("mca:albinism", true);
        state.tag = updated;
        assertTrue(traits.hasTrait(Traits.ALBINISM));
        assertTrue(traits.hasTrait("ALBINISM"));
        assertTrue(traits.hasTrait("MCA:ALBINISM"));

        state.tag = new CompoundTag();
        assertFalse(traits.hasTrait(Traits.ALBINISM));
        assertFalse(traits.hasTrait("mca:albinism"));
        assertFalse(traits.hasTrait((Traits.Trait) null));
        assertFalse(traits.hasTrait("unknown_trait"));
    }

    @Test
    void mutationKeepsCanonicalKeysAndCopyOnWriteSemantics() {
        TrackedTraits state = new TrackedTraits();
        Traits traits = new Traits(state.villager());
        CompoundTag original = state.tag;
        traits.addTrait(Traits.RAINBOW);
        assertFalse(original.contains("mca:rainbow"));
        assertTrue(state.tag.getBooleanOr("mca:rainbow", false));
        assertFalse(state.tag.contains("rainbow"));

        CompoundTag added = state.tag;
        traits.removeTrait(Traits.RAINBOW);
        assertTrue(added.contains("mca:rainbow"));
        assertFalse(state.tag.contains("mca:rainbow"));
    }

    private static final class TrackedTraits {
        private CompoundTag tag = new CompoundTag();

        private VillagerLike<?> villager() {
            return (VillagerLike<?>) Proxy.newProxyInstance(VillagerLike.class.getClassLoader(),
                    new Class<?>[]{VillagerLike.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getTrackedValue" -> tag;
                        case "onTraitChanged" -> null;
                        case "setTrackedValue" -> {
                            tag = (CompoundTag) args[1];
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
