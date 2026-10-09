package net.conczin.mca.entity.interaction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConstraintParsingTest {
    @Test
    void parsesKnownConstraints() {
        assertEquals(List.of(Constraint.ADULT, Constraint.NOT_RELATIVE), Constraint.fromStringList("adult,!relative"));
    }

    @Test
    void unknownConstraintsFailClosedWithoutThrowing() {
        List<Constraint> constraints = Constraint.fromStringList("adult,rumors_cooldown");

        assertEquals(List.of(Constraint.ADULT, Constraint.INVALID), constraints);
        assertFalse(Set.of(Constraint.ADULT).containsAll(constraints));
    }
}
