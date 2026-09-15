package net.conczin.mca.entity.ai;

import net.conczin.mca.util.InventoryUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Chore {
    NONE("none"),
    PROSPECT("prospecting"),
    HARVEST("harvesting", stack -> stack.is(ItemTags.HOES)),
    CHOP("chopping", stack -> stack.is(ItemTags.AXES)),
    HUNT("hunting", InventoryUtils::isWeapon),
    FISH("fishing", stack -> stack.getItem() instanceof FishingRodItem);

    private static final Chore[] VALUES = values();
    private static final Map<String, Chore> REGISTRY = Stream.of(VALUES).collect(Collectors.toMap(
            c -> c.friendlyName,
            Function.identity())
    );

    private final String friendlyName;
    private final Predicate<ItemStack> toolMatcher;

    Chore(String friendlyName) {
        this(friendlyName, stack -> false);
    }

    Chore(String friendlyName, Predicate<ItemStack> toolMatcher) {
        this.friendlyName = friendlyName;
        this.toolMatcher = toolMatcher;
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
        return toolMatcher.test(stack);
    }
}

