package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;

public class RemoveCustomClothingMessage implements Message {
    @Serial
    private static final long serialVersionUID = 8751716740579401345L;

    final Type type;
    final String identifier;

    public RemoveCustomClothingMessage(Type type, ResourceLocation identifier) {
        this.type = type;
        this.identifier = String.valueOf(identifier);
    }

    @Override
    public void receive(ServerPlayer player) {
        if (!CustomClothingManager.canModifyGlobalContent(player)) {
            return;
        }

        if (type == Type.CLOTHING) {
            CustomClothingManager.getClothing().removeEntry(identifier);
        } else if (type == Type.HAIR) {
            CustomClothingManager.getHair().removeEntry(identifier);
        }
    }

    public enum Type {
        CLOTHING,
        HAIR
    }
}
