package net.conczin.mca.network.c2s;

import net.conczin.mca.entity.ai.Traits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class VillagerEditorSyncRequestTest {
    @Test
    void playerTraitRestrictionsAreEnforcedByTheServerMerge() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Method merge = playerMergeMethod();
        String restrictedTrait = Traits.LEFT_HANDED.getId().toString();
        String allowedTrait = Traits.BISEXUAL.getId().toString();

        CompoundTag current = playerDataWithTraits(restrictedTrait, allowedTrait);
        CompoundTag requested = playerDataWithTraits();

        CompoundTag restricted = (CompoundTag) merge.invoke(null, current, requested, false);
        assertTrue(restricted.getCompound("Traits").contains(restrictedTrait));
        assertFalse(restricted.getCompound("Traits").contains(allowedTrait));

        CompoundTag bypassed = (CompoundTag) merge.invoke(null, current, requested, true);
        assertFalse(bypassed.getCompound("Traits").contains(restrictedTrait));
        assertFalse(bypassed.getCompound("Traits").contains(allowedTrait));
    }

    private static Method playerMergeMethod() {
        try {
            Method method = VillagerEditorSyncRequest.class.getDeclaredMethod(
                    "mergePlayerEditorPatch", CompoundTag.class, CompoundTag.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            fail("player editor patches must have a server-authoritative trait merge");
            return null;
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
