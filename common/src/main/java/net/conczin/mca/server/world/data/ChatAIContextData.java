package net.conczin.mca.server.world.data;

import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChatAIContextData extends SavedData {
    private static final String DATA_ID = "mca_chat_ai_context";
    private static final String WORLD_PROMPT_KEY = "worldPrompt";

    private String worldPrompt = "";

    private ChatAIContextData() {
    }

    private ChatAIContextData(CompoundTag nbt) {
        worldPrompt = nbt.getString(WORLD_PROMPT_KEY);
    }

    public static ChatAIContextData get(MinecraftServer server) {
        return WorldUtils.loadData(
                server.overworld(),
                (nbt, provider) -> new ChatAIContextData(nbt),
                ignored -> new ChatAIContextData(),
                DATA_ID
        );
    }

    public String getWorldPrompt() {
        return worldPrompt;
    }

    public void setWorldPrompt(String worldPrompt) {
        this.worldPrompt = worldPrompt;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putString(WORLD_PROMPT_KEY, worldPrompt);
        return nbt;
    }
}
