package net.conczin.mca.dialogue;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Authoritative server-side inputs used to evaluate dialogue requirements.
 *
 * <p>The event-availability predicate is deliberately separate from history. A missing addon event is
 * unavailable, while an installed but not-yet-completed event is an ordinary false history check. That
 * distinction is required so {@code mca:not} cannot turn a missing prerequisite into an eligible event.</p>
 */
public record DialogueContext(
        VillagerEntityMCA villager,
        ServerPlayer player,
        DialogueEventHistory history,
        Predicate<ResourceLocation> eventAvailable
) {
    public DialogueContext {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(eventAvailable, "eventAvailable");
    }

    public ServerLevel level() {
        return (ServerLevel) villager.level();
    }

    public Optional<Village> village() {
        return villager.getResidency().getHomeVillage();
    }

    public boolean isEventAvailable(ResourceLocation event) {
        return eventAvailable.test(event);
    }
}
