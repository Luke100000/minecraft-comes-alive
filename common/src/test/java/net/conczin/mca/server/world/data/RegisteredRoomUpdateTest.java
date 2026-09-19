package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredRoomUpdateTest {
    @Test
    void oneEligibleTypeSkipsPolymorphReview() {
        RegisteredRoomUpdate update = updateWithTypes(List.of("workshop"));

        assertFalse(update.requiresTypeSelection());
    }

    @Test
    void twoEligibleTypesRequirePolymorphReview() {
        RegisteredRoomUpdate update = updateWithTypes(List.of("music_store", "workshop"));

        assertTrue(update.requiresTypeSelection());
    }

    @Test
    void forcedRoomKeepsItsTypeWithoutPolymorphReview() {
        Building replacement = new Building(BlockPos.ZERO);
        replacement.setTypeForced(true);
        RegisteredRoomUpdate update = updateWithTypes(
                replacement, List.of("music_store", "workshop"));

        assertFalse(update.requiresTypeSelection());
    }

    @Test
    void noEligibleTypesCanContinueWithoutPolymorphReview() {
        RegisteredRoomUpdate update = updateWithTypes(List.of());

        assertFalse(update.requiresTypeSelection());
    }

    private static RegisteredRoomUpdate updateWithTypes(List<String> matchingTypes) {
        return updateWithTypes(new Building(BlockPos.ZERO), matchingTypes);
    }

    private static RegisteredRoomUpdate updateWithTypes(Building replacement, List<String> matchingTypes) {
        return new RegisteredRoomUpdate(
                Building.validationResult.SUCCESS,
                BlockPos.ZERO,
                null,
                null,
                10,
                0,
                20,
                replacement,
                matchingTypes);
    }
}
