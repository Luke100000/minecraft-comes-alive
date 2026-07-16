package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.Serial;

public class RemoveCustomClothingMessage implements Message {
    @Serial
    private static final long serialVersionUID = 8751716740579401345L;

    final Type type;
    final String identifier;

    public RemoveCustomClothingMessage(Type type, Identifier identifier) {
        this.type = type;
        this.identifier = String.valueOf(identifier);
    }

    @Override
    public void receive(ServerPlayerEntity player) {
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
