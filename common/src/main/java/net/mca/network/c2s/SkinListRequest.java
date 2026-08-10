package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.SkinListResponse;
import net.mca.resources.ClothingList;
import net.mca.resources.HairList;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.Hair;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

public class SkinListRequest implements Message {
    @Serial
    private static final long serialVersionUID = -6508206556519152120L;

    private <T> HashMap<String, T> merge(Map<String, T> a, Map<String, T> b) {
        HashMap<String, T> map = new HashMap<>();
        map.putAll(a);
        map.putAll(b);
        return map;
    }

    @Override
    public void receive(ServerPlayer player) {
        Map<String, Clothing> clothing = CustomClothingManager.getClothing().getEntries();
        Map<String, Hair> hair = CustomClothingManager.getHair().getEntries();
        NetworkHandler.sendToPlayer(new SkinListResponse(merge(ClothingList.getInstance().clothing, clothing), merge(HairList.getInstance().hair, hair)), player);
    }
}
