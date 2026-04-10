package net.conczin.mca.util;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class PlayerMessageHelper {
    private PlayerMessageHelper() {
    }

    public static void displayClientMessage(Player player, Component message, boolean overlay) {
        player.displayClientMessage(message, overlay);
    }
}
