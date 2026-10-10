package net.conczin.mca.entity.ai.navigation;

/**
 * Marks a multi-target movement intent that already belongs to ranged-combat escape.
 * Generic movement may yield foreign navigation during an emergency without stopping
 * the escape path that replaced it.
 */
public interface CombatEscapePositionTracker extends MultiTargetPositionTracker {
}
