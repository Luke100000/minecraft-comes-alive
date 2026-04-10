package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedList;
import java.util.List;

public class CivilRegistryManager extends SavedData implements WorldUtils.NbtSavedData {
    private final LinkedList<Component> entries = new LinkedList<>();

    CivilRegistryManager(ServerLevel world) {

    }

    CivilRegistryManager(CompoundTag nbt, HolderLookup.Provider provider) {
        entries.addAll(NbtHelper.toList(
                nbt.get("entries"),
                element -> ComponentSerialization.CODEC
                        .parse(provider.createSerializationContext(NbtOps.INSTANCE), element)
                        .result()
                        .orElse(Component.empty())
        ));
    }

    public static CivilRegistryManager get(ServerLevel world, Village village) {
        return WorldUtils.loadData(world.getServer().overworld(), CivilRegistryManager::new, CivilRegistryManager::new, "mca_civil_registry_" + village.getId());
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag elements = NbtHelper.fromList(entries, a -> ComponentSerialization.CODEC
                .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), a)
                .result()
                .orElseGet(() -> StringTag.valueOf("")));
        nbt.put("entries", elements);
        return nbt;
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
