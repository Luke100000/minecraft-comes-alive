package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.s2c.BabyNameResponse;
import net.conczin.mca.resources.Names;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;

public class BabyNameRequest implements Message {
    @Serial
    private static final long serialVersionUID = 4965378949498898298L;

    private final Gender gender;

    public BabyNameRequest(Gender gender) {
        this.gender = gender;
    }

    @Override
    public void receive(ServerPlayer player) {
        String name = Names.pickCitizenName(gender);
        NetworkHandler.sendToPlayer(new BabyNameResponse(name), player);
    }
}
