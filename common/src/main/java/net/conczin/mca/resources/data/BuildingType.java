package net.conczin.mca.resources.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.util.RegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
    private transient Map<ResourceLocation, ResourceLocation> blockToGroup;
    private transient Map<TagKey<Block>, ResourceLocation> tagToGroup;
    private transient Map<ResourceLocation, Integer> groups;
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
                this.blocks.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }

        this.groups = new HashMap<>();
        if (GsonHelper.isObjectNode(value, "groups")) {
            JsonObject blocks = GsonHelper.getAsJsonObject(value, "groups");
            for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
                this.groups.put(new ResourceLocation(entry.getKey()), entry.getValue().getAsInt());
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
    public Map<ResourceLocation, ResourceLocation> getBlockToGroup() {
        if (blockToGroup == null) {
            blockToGroup = new HashMap<>();
            tagToGroup = new HashMap<>();
            groups = new HashMap<>();
            for (Map.Entry<String, Integer> requirement : blocks.entrySet()) {
                ResourceLocation identifier;
                if (requirement.getKey().startsWith("#")) {
                    identifier = new ResourceLocation(requirement.getKey().substring(1));
                    TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                    if (tag == null || RegistryHelper.isTagEmpty(tag)) {
                        MCA.LOGGER.error("Unknown building type tag " + identifier);
                    }
                    tagToGroup.put(tag, identifier);
                } else {
                    identifier = new ResourceLocation(requirement.getKey());
                    blockToGroup.put(identifier, identifier);
                }
                groups.put(identifier, requirement.getValue());
            }
        }
        return blockToGroup;
    }

    private Optional<ResourceLocation> getGroupForBlock(ResourceLocation blockId) {
        getBlockToGroup();

        ResourceLocation directGroup = blockToGroup.get(blockId);
        if (directGroup != null) {
            return Optional.of(directGroup);
        }

        var entry = BuiltInRegistries.BLOCK.getHolder(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, blockId));
        if (entry.isEmpty()) {
            return Optional.empty();
        }

        for (Map.Entry<TagKey<Block>, ResourceLocation> tagEntry : tagToGroup.entrySet()) {
            if (entry.get().is(tagEntry.getKey())) {
                return Optional.of(tagEntry.getValue());
            }
        }

        return Optional.empty();
    }

    private Optional<ResourceLocation> getGroupForBlock(BlockState state) {
        getBlockToGroup();

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ResourceLocation directGroup = blockToGroup.get(blockId);
        if (directGroup != null) {
            return Optional.of(directGroup);
        }

        for (Map.Entry<TagKey<Block>, ResourceLocation> tagEntry : tagToGroup.entrySet()) {
            if (state.is(tagEntry.getKey())) {
                blockToGroup.put(blockId, tagEntry.getValue());
                return Optional.of(tagEntry.getValue());
            }
        }

        return Optional.empty();
    }

    public boolean matchesBlock(ResourceLocation blockId) {
        return getGroupForBlock(blockId).isPresent();
    }

    public boolean matchesBlock(BlockState state) {
        return getGroupForBlock(state).isPresent();
    }

    public Map<ResourceLocation, Integer> getGroups() {
        getBlockToGroup();
        return groups;
    }

    /**
     * @param blocks the map of block positions per block type of building
     *
     * @return a filtered and grouped map of block types relevant for this building type
     */
    public Map<ResourceLocation, List<BlockPos>> getGroups(Map<ResourceLocation, List<BlockPos>> blocks) {
        HashMap<ResourceLocation, List<BlockPos>> available = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<BlockPos>> entry : blocks.entrySet()) {
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
