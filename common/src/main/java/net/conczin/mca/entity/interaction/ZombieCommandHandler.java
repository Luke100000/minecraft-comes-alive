package net.conczin.mca.entity.interaction;

import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public class ZombieCommandHandler extends EntityCommandHandler<ZombieVillagerEntityMCA> {

    public ZombieCommandHandler(ZombieVillagerEntityMCA entity) {
        super(entity);
    }

    /**
     * Called on the server to respond to button events.
     */
    @Override
    public boolean handle(ServerPlayer player, String command) {
        switch (command) {
            case "gift" -> {
                // zombies only accept one type of gift, and for now it's not brains
                if (entity.mobInteract(player, InteractionHand.MAIN_HAND).consumesAction()) {
                    if (!player.getAbilities().instabuild) {
                        player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                    }
                }
                return true;
            }
        }

        return super.handle(player, command);
    }
}
