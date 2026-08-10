package net.mca.network.c2s;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mca.cobalt.network.Message;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.Hair;
import net.mca.resources.data.skin.SkinListEntry;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;

public class AddCustomClothingMessage implements Message {
    @Serial
    private static final long serialVersionUID = 4620788389788045910L;

    private final String identifier;
    private final boolean hair;
    private final String json;

    private AddCustomClothingMessage(String identifier, boolean hair, String json) {
        this.identifier = identifier;
        this.hair = hair;
        this.json = json;
    }

    public static AddCustomClothingMessage fromEntry(SkinListEntry entry) {
        return new AddCustomClothingMessage(entry.getIdentifier(), entry instanceof Hair, entry.toJson().toString());
    }

    @Override
    public void receive(ServerPlayer player) {
        if (!CustomClothingManager.canModifyGlobalContent(player)) {
            return;
        }

        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        if (hair) {
            CustomClothingManager.getHair().addEntry(identifier, new Hair(identifier, object));
        } else {
            CustomClothingManager.getClothing().addEntry(identifier, new Clothing(identifier, object));
        }
    }
}
