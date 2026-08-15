package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.cobalt.network.Message;

import java.util.UUID;

public class ChatAIContextResponse implements Message {
    private final String dimension;
    private final boolean hasVillager;
    private final UUID villagerUuid;
    private final String villagerName;
    private final String villagerPrompt;
    private final String villagerNickname;
    private final String playerName;
    private final String playerPrompt;
    private final boolean hasVillage;
    private final int villageId;
    private final String villageName;
    private final String villagePrompt;
    private final String worldPrompt;

    public ChatAIContextResponse(String dimension, boolean hasVillager, UUID villagerUuid, String villagerName,
                                 String villagerPrompt, String villagerNickname, String playerName, String playerPrompt,
                                 boolean hasVillage, int villageId, String villageName, String villagePrompt,
                                 String worldPrompt) {
        this.dimension = dimension;
        this.hasVillager = hasVillager;
        this.villagerUuid = villagerUuid;
        this.villagerName = villagerName;
        this.villagerPrompt = villagerPrompt;
        this.villagerNickname = villagerNickname;
        this.playerName = playerName;
        this.playerPrompt = playerPrompt;
        this.hasVillage = hasVillage;
        this.villageId = villageId;
        this.villageName = villageName;
        this.villagePrompt = villagePrompt;
        this.worldPrompt = worldPrompt;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleChatAIContextResponse(this);
    }

    public String dimension() { return dimension; }
    public boolean hasVillager() { return hasVillager; }
    public UUID villagerUuid() { return villagerUuid; }
    public String villagerName() { return villagerName; }
    public String villagerPrompt() { return villagerPrompt; }
    public String villagerNickname() { return villagerNickname; }
    public String playerName() { return playerName; }
    public String playerPrompt() { return playerPrompt; }
    public boolean hasVillage() { return hasVillage; }
    public int villageId() { return villageId; }
    public String villageName() { return villageName; }
    public String villagePrompt() { return villagePrompt; }
    public String worldPrompt() { return worldPrompt; }
}
