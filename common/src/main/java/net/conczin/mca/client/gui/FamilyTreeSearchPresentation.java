package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public final class FamilyTreeSearchPresentation {
    private FamilyTreeSearchPresentation() {
    }

    public static Component displayName(FamilyTreeSearchEntry entry) {
        if (MCA.isBlankString(entry.name())) {
            return Component.translatable("gui.family_tree.unnamed_villager");
        }
        return Component.literal(entry.name());
    }

    public static Optional<Component> parentLine(FamilyTreeSearchEntry entry) {
        boolean fatherResolved = !MCA.isBlankString(entry.father());
        boolean motherResolved = !MCA.isBlankString(entry.mother());

        if (fatherResolved && motherResolved) {
            return Optional.of(Component.translatable("gui.family_tree.child_of_2", entry.father(), entry.mother()));
        }
        if (fatherResolved) {
            return Optional.of(Component.translatable("gui.family_tree.child_of_1", entry.father()));
        }
        if (motherResolved) {
            return Optional.of(Component.translatable("gui.family_tree.child_of_1", entry.mother()));
        }
        if (!entry.fatherRecorded() && !entry.motherRecorded()) {
            return Optional.of(Component.translatable("gui.family_tree.parents_not_recorded"));
        }
        return Optional.empty();
    }
}
