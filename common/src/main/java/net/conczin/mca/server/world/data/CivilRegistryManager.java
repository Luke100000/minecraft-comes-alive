package net.conczin.mca.server.world.data;

import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedList;
import java.util.List;

public class CivilRegistryManager extends SavedData {
    private final LinkedList<Component> entries = new LinkedList<>();

    CivilRegistryManager(ServerLevel world) {

    }

    CivilRegistryManager(CompoundTag nbt, HolderLookup.Provider provider) {
        entries.addAll(NbtHelper.toList(nbt.getListOrEmpty("entries"), element -> readComponent(element, provider)));
    }

    public static CivilRegistryManager get(ServerLevel world, Village village) {
        return WorldUtils.loadData(world.getServer().overworld(), CivilRegistryManager::new, CivilRegistryManager::new, "mca_civil_registry_" + village.getId());
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag elements = NbtHelper.fromList(entries, a -> writeComponent(a, provider));
        nbt.put("entries", elements);
        return nbt;
    }

    private static Component readComponent(net.minecraft.nbt.Tag element, HolderLookup.Provider provider) {
        String json = element.asString().orElseThrow();
        return ComponentSerialization.CODEC
                .parse(provider.createSerializationContext(JsonOps.INSTANCE), GsonHelper.parse(json))
                .resultOrPartial(MCA.LOGGER::error)
                .orElse(Component.empty());
    }

    private static StringTag writeComponent(Component component, HolderLookup.Provider provider) {
        return StringTag.valueOf(GsonHelper.toStableString(
                ComponentSerialization.CODEC.encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), component)
                        .resultOrPartial(MCA.LOGGER::error)
                        .orElseThrow()
        ));
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
