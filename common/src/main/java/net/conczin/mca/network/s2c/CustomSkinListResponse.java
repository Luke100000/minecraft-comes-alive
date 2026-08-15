package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.resources.data.skin.*;
import net.conczin.mca.resources.data.skin.*;

import java.io.Serial;
import java.util.HashMap;

public class CustomSkinListResponse implements Message {
    @Serial
    private static final long serialVersionUID = -9061027280069160228L;

    private final HashMap<String, Clothing> clothing;
    private final HashMap<String, BodySkin> bodySkins;
    private final HashMap<String, LayeredHair> layeredHair;
    private final HashMap<String, HairStyle> hairStyles;
    private final HashMap<String, Hair> hair;

    public CustomSkinListResponse(HashMap<String, Clothing> clothing, HashMap<String, BodySkin> bodySkins, HashMap<String, LayeredHair> layeredHair, HashMap<String, HairStyle> hairStyles, HashMap<String, Hair> hair) {
        this.clothing = clothing;
        this.bodySkins = bodySkins;
        this.layeredHair = layeredHair;
        this.hairStyles = hairStyles;
        this.hair = hair;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleCustomSkinListResponse(this);
    }

    public HashMap<String, Clothing> clothing() {
        return clothing;
    }

    public HashMap<String, BodySkin> bodySkins() {
        return bodySkins;
    }

    public HashMap<String, LayeredHair> layeredHair() {
        return layeredHair;
    }

    public HashMap<String, HairStyle> hairStyles() {
        return hairStyles;
    }

    public HashMap<String, Hair> hair() {
        return hair;
    }
}
