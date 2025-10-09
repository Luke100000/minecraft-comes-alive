package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.item.BabyItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.io.Serial;

public class BabyNamingVillagerMessage implements Message {
    @Serial
    private static final long serialVersionUID = -7160822837267592011L;

    private final int slot;
    private final String name;

    public BabyNamingVillagerMessage(int slot, String name) {
        this.slot = slot;
        this.name = name;
    }

    @Override
    public void receive(ServerPlayer player) {
        ItemStack stack = player.getInventory().getItem(slot);
        BabyItem.getBabyNbt(stack).putString("babyName", name);
    }
}
