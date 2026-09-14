package net.conczin.mca.entity.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerCommandHandlerTest {
    @Test
    void lowHeartsTakePrecedence() {
        assertEquals(
                VillagerCommandHandler.ProcreateDecision.LOW_HEARTS,
                VillagerCommandHandler.decideProcreation(99, true, true)
        );
    }

    @Test
    void infertilityBlocksBeforeCooldownOrStart() {
        assertEquals(
                VillagerCommandHandler.ProcreateDecision.INFERTILE,
                VillagerCommandHandler.decideProcreation(100, true, true)
        );
    }

    @Test
    void fertileVillagerStartsWhenCooldownIsReady() {
        assertEquals(
                VillagerCommandHandler.ProcreateDecision.START,
                VillagerCommandHandler.decideProcreation(100, false, true)
        );
    }

    @Test
    void fertileVillagerReportsCooldownWhenNotReady() {
        assertEquals(
                VillagerCommandHandler.ProcreateDecision.TOO_SOON,
                VillagerCommandHandler.decideProcreation(100, false, false)
        );
    }
}
