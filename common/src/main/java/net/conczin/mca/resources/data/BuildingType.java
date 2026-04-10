package net.conczin.mca.resources.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.util.RegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;

import java.util.*;

public final class BuildingType {
    public static final StreamCodec<FriendlyByteBuf, BuildingType> STREAM_CODEC = StreamCodec.of(
            (buf, buildingType) -> {
                buf.writeUtf(buildingType.name);
                buf.writeVarInt(buildingType.margin);
                buf.writeUtf(buildingType.color);
                buf.writeVarInt(buildingType.priority);
                buf.writeBoolean(buildingType.visible);
                buf.writeBoolean(buildingType.noBeds);
                buf.writeBoolean(buildingType.icon);
                buf.writeVarInt(buildingType.iconU);
                buf.writeVarInt(buildingType.iconV);
                buf.writeBoolean(buildingType.grouped);
                buf.writeVarInt(buildingType.mergeRange);

                // Serialize blocks map
                buf.writeVarInt(buildingType.blocks.size());
                buildingType.blocks.forEach((key, value) -> {
                    buf.writeUtf(key);
                    buf.writeVarInt(value);
                });
            },
            buf -> {
                String name = buf.readUtf();
                int margin = buf.readVarInt();
                String color = buf.readUtf();
                int priority = buf.readVarInt();
                boolean visible = buf.readBoolean();
                boolean noBeds = buf.readBoolean();
                boolean icon = buf.readBoolean();
                int iconU = buf.readVarInt();
                int iconV = buf.readVarInt();
                boolean grouped = buf.readBoolean();
                int mergeRange = buf.readVarInt();

                // Deserialize blocks map
                int blocksSize = buf.readVarInt();
                Map<String, Integer> blocks = new HashMap<>(blocksSize);
                for (int i = 0; i < blocksSize; i++) {
                    blocks.put(buf.readUtf(), buf.readVarInt());
                }

                return new BuildingType(name, margin, color, priority, visible, noBeds, icon, iconU, iconV, grouped, mergeRange, blocks);
            }
    );
    private final String name;
    private final int margin;
    private final String color;
    private final int priority;
    private final boolean visible;
    private final boolean noBeds;
    private final Map<String, Integer> blocks;
    private final boolean icon;
    private final int iconU;
    private final int iconV;
    private final boolean grouped;
    private final int mergeRange;
    private transient Map<Identifier, Identifier> blockToGroup;
    private transient Map<Identifier, Integer> groups;

    // Private constructor for deserialization
    private BuildingType(String name, int margin, String color, int priority, boolean visible, boolean noBeds,
                         boolean icon, int iconU, int iconV, boolean grouped, int mergeRange, Map<String, Integer> blocks) {
        this.name = name;
        this.margin = margin;
        this.color = color;
        this.priority = priority;
        this.visible = visible;
        this.noBeds = noBeds;
        this.icon = icon;
        this.iconU = iconU;
        this.iconV = iconV;
        this.grouped = grouped;
        this.mergeRange = mergeRange;
        this.blocks = blocks;
    }

    public BuildingType() {
        this.name = "?";
        this.margin = 0;
        this.color = "ffffffff";
        this.priority = 0;
        this.visible = true;
        this.noBeds = false;
        this.blocks = Map.of("#minecraft:beds", 1000000000);
        this.blockToGroup = null;
        this.icon = false;
        this.iconU = 0;
        this.iconV = 0;
        this.grouped = false;
        this.mergeRange = 32;
    }

    public BuildingType(String name, JsonObject value) {
        this.name = name;
        this.margin = GsonHelper.getAsInt(value, "margin", 0);
        this.color = GsonHelper.getAsString(value, "color", "ffffffff");
        this.priority = GsonHelper.getAsInt(value, "priority", 0);
        this.visible = GsonHelper.getAsBoolean(value, "visible", true);
        this.noBeds = GsonHelper.getAsBoolean(value, "noBeds", false);

        this.icon = GsonHelper.getAsBoolean(value, "icon", false);
        this.iconU = GsonHelper.getAsInt(value, "iconU", 0);
        this.iconV = GsonHelper.getAsInt(value, "iconV", 0);

        this.grouped = GsonHelper.getAsBoolean(value, "grouped", false);
        this.mergeRange = GsonHelper.getAsInt(value, "mergeRange", 0);

        this.blocks = new HashMap<>();
        if (GsonHelper.isObjectNode(value, "blocks")) {
            JsonObject blocks = GsonHelper.getAsJsonObject(value, "blocks");
            for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
                this.blocks.put(
                        entry.getKey(),
                        entry.getValue().getAsInt()
                );
            }
        }

        this.groups = new HashMap<>();
        if (GsonHelper.isObjectNode(value, "groups")) {
            JsonObject blocks = GsonHelper.getAsJsonObject(value, "groups");
            for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
                this.groups.put(
                        Identifier.parse(entry.getKey()),
                        entry.getValue().getAsInt()
                );
            }
        }
    }

    public String name() {
        return name;
    }

    public String color() {
        return color;
    }

    public int priority() {
        return priority;
    }

    public boolean visible() {
        return visible;
    }

    public int getColor() {
        return (int) Long.parseLong(color, 16);
    }

    /**
     * @return a mapping between block identifiers and groups (tags or individual blocks)
     */
    public Map<Identifier, Identifier> getBlockToGroup() {
        if (blockToGroup == null) {
            blockToGroup = new HashMap<>();
            groups = new HashMap<>();
            for (Map.Entry<String, Integer> requirement : blocks.entrySet()) {
                Identifier identifier;
                if (requirement.getKey().startsWith("#")) {
                    identifier = Identifier.parse(requirement.getKey().substring(1));
                    TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                    if (RegistryHelper.isTagEmpty(tag)) {
                        MCA.LOGGER.error("Unknown building type tag {}", identifier);
                    } else {
                        var entries = RegistryHelper.getEntries(tag);
                        entries.ifPresent(registryEntries -> {
                            for (Block b : registryEntries.stream().map(Holder::value).toList()) {
                                blockToGroup.putIfAbsent(BuiltInRegistries.BLOCK.getKey(b), identifier);
                            }
                        });
                    }
                } else {
                    identifier = Identifier.parse(requirement.getKey());
                    blockToGroup.put(identifier, identifier);
                }
                groups.put(identifier, requirement.getValue());
            }
        }
        return blockToGroup;
    }

    public Map<Identifier, Integer> getGroups() {
        getBlockToGroup();
        return groups;
    }

    /**
     * @param blocks the map of block positions per block type of building
     * @return a filtered and grouped map of block types relevant for this building type
     */
    public Map<Identifier, List<BlockPos>> getGroups(Map<Identifier, List<BlockPos>> blocks) {
        HashMap<Identifier, List<BlockPos>> available = new HashMap<>();
        for (Map.Entry<Identifier, List<BlockPos>> entry : blocks.entrySet()) {
            Optional.ofNullable(getBlockToGroup().get(entry.getKey())).ifPresent(v ->
                    available.computeIfAbsent(v, k -> new LinkedList<>()).addAll(entry.getValue())
            );
        }
        return available;
    }

    public boolean isIcon() {
        return icon;
    }

    public int iconU() {
        return iconU * 20;
    }

    public int iconV() {
        return iconV * 60;
    }

    public boolean grouped() {
        return grouped;
    }

    public int mergeRange() {
        return mergeRange;
    }

    public boolean noBeds() {
        return noBeds;
    }

    public int getMargin() {
        return margin;
    }

    public int getMinBlocks() {
        return blocks.values().stream().mapToInt(v -> v).sum();
    }
}
