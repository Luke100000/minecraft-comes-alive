package net.conczin.mca.entity.ai;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Chore {
    NONE("none"),
    PROSPECT("prospecting"),
    HARVEST("harvesting", ItemTags.HOES),
    CHOP("chopping", ItemTags.AXES),
    HUNT("hunting"),
    FISH("fishing");

    private static final Chore[] VALUES = values();
    private static final Map<String, Chore> REGISTRY = Stream.of(VALUES).collect(Collectors.toMap(
            c -> c.friendlyName,
            Function.identity())
    );

    private final String friendlyName;

    @Nullable
    private final TagKey<Item> toolTag;

    Chore(String friendlyName) {
        this(friendlyName, null);
    }

    Chore(String friendlyName, @Nullable TagKey<Item> toolTag) {
        this.friendlyName = friendlyName;
        this.toolTag = toolTag;
    }

    public static Optional<Chore> byCommand(String action) {
        return Optional.ofNullable(REGISTRY.get(action.toLowerCase(Locale.ENGLISH)));
    }

    public static Chore byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return NONE;
        }
        return VALUES[id];
    }

    public Component getName() {
        return Component.translatable("gui.label." + friendlyName);
    }

    public boolean matchesTool(ItemStack stack) {
        return toolTag != null && stack.is(toolTag);
    }
}

