package net.mca.server.world.data;

import net.mca.util.NbtHelper;
import net.mca.util.WorldUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedList;
import java.util.List;

public class CivilRegistryManager extends SavedData {
    private final LinkedList<Component> entries = new LinkedList<>();

    public static CivilRegistryManager get(ServerLevel world, Village village) {
        return WorldUtils.loadData(world.getServer().overworld(), CivilRegistryManager::new, CivilRegistryManager::new, "mca_civil_registry_" + village.getId());
    }

    CivilRegistryManager(ServerLevel world) {

    }

    CivilRegistryManager(CompoundTag nbt) {
        entries.addAll(NbtHelper.toList(nbt.get("entries"), element -> Component.Serializer.fromJson(element.getAsString())));
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag elements = NbtHelper.fromList(entries, a -> StringTag.valueOf(Component.Serializer.toJson(a)));
        CompoundTag compound = new CompoundTag();
        compound.put("entries", elements);
        return compound;
    }

    public void addText(Component text) {
        entries.addFirst(text);
        setDirty();
    }

    public List<Component> getPage(int from, int to) {
        to = Math.min(entries.size(), to);
        return to <= from ? List.of() : entries.subList(from, to);
    }
}
