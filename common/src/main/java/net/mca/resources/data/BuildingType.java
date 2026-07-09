package net.mca.resources.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mca.MCA;
import net.mca.util.RegistryHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public final class BuildingType implements Serializable {
    @Serial
    private static final long serialVersionUID = 2215455350801127280L;

    private final String name;
    private final int margin;
    private final String color;
    private final int priority;
    private final boolean visible;
    private final boolean noBeds;
    private final Map<String, Integer> blocks;
    private transient Map<Identifier, Identifier> blockToGroup;
    private transient Map<TagKey<Block>, Identifier> tagToGroup;
    private transient Map<Identifier, Integer> groups;
    private final boolean icon;
    private final int iconU;
    private final int iconV;
    private final boolean grouped;
    private final int mergeRange;

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
        this.margin = JsonHelper.getInt(value, "margin", 0);
        this.color = JsonHelper.getString(value, "color", "ffffffff");
        this.priority = JsonHelper.getInt(value, "priority", 0);
        this.visible = JsonHelper.getBoolean(value, "visible", true);
        this.noBeds = JsonHelper.getBoolean(value, "noBeds", false);

        this.icon = JsonHelper.getBoolean(value, "icon", false);
        this.iconU = JsonHelper.getInt(value, "iconU", 0);
        this.iconV = JsonHelper.getInt(value, "iconV", 0);

        this.grouped = JsonHelper.getBoolean(value, "grouped", false);
        this.mergeRange = JsonHelper.getInt(value, "mergeRange", 0);

        this.blocks = new HashMap<>();
        if (JsonHelper.hasJsonObject(value, "blocks")) {
            JsonObject blocks = JsonHelper.getObject(value, "blocks");
            for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
                this.blocks.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }

        this.groups = new HashMap<>();
        if (JsonHelper.hasJsonObject(value, "groups")) {
            JsonObject blocks = JsonHelper.getObject(value, "groups");
            for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
                this.groups.put(new Identifier(entry.getKey()), entry.getValue().getAsInt());
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
        return (int)Long.parseLong(color, 16);
    }

    /**
     * @return a mapping between block identifiers and groups (tags or individual blocks)
     */
    public Map<Identifier, Identifier> getBlockToGroup() {
        if (blockToGroup == null) {
            blockToGroup = new HashMap<>();
            tagToGroup = new HashMap<>();
            groups = new HashMap<>();
            for (Map.Entry<String, Integer> requirement : blocks.entrySet()) {
                Identifier identifier;
                if (requirement.getKey().startsWith("#")) {
                    identifier = new Identifier(requirement.getKey().substring(1));
                    TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, identifier);
                    if (tag == null || RegistryHelper.isTagEmpty(tag)) {
                        MCA.LOGGER.error("Unknown building type tag " + identifier);
                    }
                    tagToGroup.put(tag, identifier);
                } else {
                    identifier = new Identifier(requirement.getKey());
                    blockToGroup.put(identifier, identifier);
                }
                groups.put(identifier, requirement.getValue());
            }
        }
        return blockToGroup;
    }

    private Optional<Identifier> getGroupForBlock(Identifier blockId) {
        getBlockToGroup();

        Identifier directGroup = blockToGroup.get(blockId);
        if (directGroup != null) {
            return Optional.of(directGroup);
        }

        var entry = Registries.BLOCK.getEntry(net.minecraft.registry.RegistryKey.of(RegistryKeys.BLOCK, blockId));
        if (entry.isEmpty()) {
            return Optional.empty();
        }

        for (Map.Entry<TagKey<Block>, Identifier> tagEntry : tagToGroup.entrySet()) {
            if (entry.get().isIn(tagEntry.getKey())) {
                return Optional.of(tagEntry.getValue());
            }
        }

        return Optional.empty();
    }

    private Optional<Identifier> getGroupForBlock(BlockState state) {
        getBlockToGroup();

        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        Identifier directGroup = blockToGroup.get(blockId);
        if (directGroup != null) {
            return Optional.of(directGroup);
        }

        for (Map.Entry<TagKey<Block>, Identifier> tagEntry : tagToGroup.entrySet()) {
            if (state.isIn(tagEntry.getKey())) {
                blockToGroup.put(blockId, tagEntry.getValue());
                return Optional.of(tagEntry.getValue());
            }
        }

        return Optional.empty();
    }

    public boolean matchesBlock(Identifier blockId) {
        return getGroupForBlock(blockId).isPresent();
    }

    public boolean matchesBlock(BlockState state) {
        return getGroupForBlock(state).isPresent();
    }

    public Map<Identifier, Integer> getGroups() {
        getBlockToGroup();
        return groups;
    }

    /**
     * @param blocks the map of block positions per block type of building
     *
     * @return a filtered and grouped map of block types relevant for this building type
     */
    public Map<Identifier, List<BlockPos>> getGroups(Map<Identifier, List<BlockPos>> blocks) {
        HashMap<Identifier, List<BlockPos>> available = new HashMap<>();
        for (Map.Entry<Identifier, List<BlockPos>> entry : blocks.entrySet()) {
            getGroupForBlock(entry.getKey()).ifPresent(group -> available.computeIfAbsent(group, k -> new LinkedList<>()).addAll(entry.getValue()));
        }
        return available;
    }

    public boolean isIcon() {
        return icon;
    }

    /**
     * @return true when this building type has a renderable icon in textures/buildings.png.
     * Explicit icon=true keeps support for icons at texture coordinate 0,0.
     */
    public boolean hasIcon() {
        return icon || iconU != 0 || iconV != 0;
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
