package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerEditorSyncRequestTest {
    @Test
    void playerTraitRestrictionsAreEnforcedByServerPatchValidation() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Method validator = VillagerEditorSyncRequest.class.getDeclaredMethod(
                "arePlayerTraitsValid", CompoundTag.class, CompoundTag.class);
        validator.setAccessible(true);
        String restrictedTrait = Traits.LEFT_HANDED.getId().toString();
        String allowedTrait = Traits.BISEXUAL.getId().toString();

        boolean previous = Config.SERVER.bypassTraitRestrictions.get();
        CompoundTag empty = playerDataWithTraits();
        CompoundTag restricted = playerDataWithTraits(restrictedTrait);
        CompoundTag allowed = playerDataWithTraits(allowedTrait);
        try {
            Config.SERVER.bypassTraitRestrictions.set(false);
            assertFalse((boolean) validator.invoke(null, empty, restricted));
            assertTrue((boolean) validator.invoke(null, empty, allowed));
            assertTrue((boolean) validator.invoke(null, restricted, restricted));
            assertTrue((boolean) validator.invoke(null, restricted, empty));

            Config.SERVER.bypassTraitRestrictions.set(true);
            assertTrue((boolean) validator.invoke(null, empty, restricted));
            assertFalse((boolean) validator.invoke(null, empty, playerDataWithTraits("mca:unknown_test_trait")));
        } finally {
            Config.SERVER.bypassTraitRestrictions.set(previous);
        }
    }

    private static CompoundTag playerDataWithTraits(String... traitIds) {
        CompoundTag data = new CompoundTag();
        CompoundTag traits = new CompoundTag();
        for (String traitId : traitIds) {
            traits.putBoolean(traitId, true);
        }
        data.put("Traits", traits);
        assertNotNull(data.get("Traits"));
        return data;
    }
}
