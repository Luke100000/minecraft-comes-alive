package net.conczin.mca.entity.interaction;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

/** Authorizes built-in non-Talk commands independently of client-side GUI resources. */
public final class InteractionMenuCommandAccess {
    private InteractionMenuCommandAccess() {
    }

    public static boolean allows(VillagerLike<?> villager, ServerPlayer player, String command) {
        if ("gift".equals(command)) {
            // Zombie villagers have vanilla item curing, not an MCA Interact menu.
            return villager instanceof VillagerEntityMCA;
        }
        if (!(villager instanceof VillagerEntityMCA)) {
            return false;
        }
        return switch (command) {
            case "trade" -> Constraint.TRADER.test(villager, player) && Constraint.ADULT.test(villager, player);
            case "inventory" -> Constraint.FAMILY.test(villager, player) || Constraint.MERCENARY.test(villager, player);
            case "pick_up" -> Constraint.NOT_ADULT.test(villager, player) && Constraint.NOT_TEEN.test(villager, player);
            case "follow" -> Constraint.NOT_FOLLOWING.test(villager, player);
            case "stay" -> Constraint.NOT_STAYING.test(villager, player);
            case "move" -> Constraint.FOLLOWING.test(villager, player) || Constraint.STAYING.test(villager, player);
            case "execute" -> Constraint.NOT_OUTLAWED.test(villager, player) && Constraint.MAYOR.test(villager, player);
            case "pardon" -> Constraint.OUTLAWED.test(villager, player) && Constraint.MAYOR.test(villager, player);
            case "profession.none", "profession.guard", "profession.archer" ->
                    Constraint.ADULT.test(villager, player) && Constraint.MONARCH.test(villager, player);
            case "fishing", "harvesting", "hunting", "chopping", "stopworking" ->
                    (Constraint.FAMILY.test(villager, player) && Constraint.NOT_BABY.test(villager, player))
                            || (Constraint.NOT_FAMILY.test(villager, player)
                            && Constraint.MERCENARY.test(villager, player));
            case "ridehorse", "armor", "sethome", "setworkplace", "gohome" -> true;
            default -> false;
        };
    }
}
