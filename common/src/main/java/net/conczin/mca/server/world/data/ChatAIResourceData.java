package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ChatAIResourceData extends SavedData {
    private static final String DATA_ID = MCA.MOD_ID + "_chat_ai_resources";
    private static final String RESOURCES_KEY = "resources";

    private final Map<UUID, String> resourceNames;

    ChatAIResourceData(ServerLevel world) {
        this(Config.legacyInworldAIResourceNames());
        if (!resourceNames.isEmpty()) {
            setDirty();
        }
    }

    ChatAIResourceData(Map<UUID, String> resourceNames) {
        this.resourceNames = new HashMap<>(resourceNames);
    }

    ChatAIResourceData(CompoundTag nbt, HolderLookup.Provider provider) {
        this.resourceNames = new HashMap<>();
        CompoundTag resources = nbt.getCompound(RESOURCES_KEY);
        for (String key : resources.getAllKeys()) {
            try {
                String resourceName = resources.getString(key);
                if (!resourceName.isBlank()) {
                    resourceNames.put(UUID.fromString(key), resourceName);
                }
            } catch (IllegalArgumentException exception) {
                MCA.LOGGER.warn("Ignoring invalid ChatAI resource mapping for villager UUID '{}'", key);
            }
        }
    }

    public static ChatAIResourceData get(ServerLevel world) {
        return WorldUtils.loadData(
                world.getServer().overworld(),
                ChatAIResourceData::new,
                ChatAIResourceData::new,
                DATA_ID);
    }

    public String getResourceName(UUID villagerId) {
        return resourceNames.getOrDefault(villagerId, "");
    }

    public void putResourceName(UUID villagerId, String resourceName) {
        String previous;
        if (resourceName == null || resourceName.isBlank()) {
            previous = resourceNames.remove(villagerId);
        } else {
            previous = resourceNames.put(villagerId, resourceName);
        }
        if (!Objects.equals(previous, resourceName == null || resourceName.isBlank() ? null : resourceName)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        CompoundTag resources = new CompoundTag();
        resourceNames.forEach((uuid, resourceName) -> resources.putString(uuid.toString(), resourceName));
        nbt.put(RESOURCES_KEY, resources);
        return nbt;
    }
}
