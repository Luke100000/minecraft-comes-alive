package mca.network;

import mca.cobalt.network.Message;
import mca.server.world.data.BabyTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

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
    public void receive(PlayerEntity player) {
        ItemStack stack = player.getInventory().getStack(slot);
        BabyTracker.getState(stack, (ServerWorld)player.world).ifPresent(state -> {
            state.setName(name);
            state.writeToItem(stack);
        });
    }
}
