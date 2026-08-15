package net.conczin.mca.client.resources;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProfessionIcons {
    public static final Map<String, ItemStack> ICONS = new HashMap<>();

    static {
        ICONS.put(null, Items.APPLE.getDefaultInstance());
        ICONS.put("nitwit", Items.FLOWER_POT.getDefaultInstance());
        ICONS.put("armorer", Items.BLAST_FURNACE.getDefaultInstance());
        ICONS.put("butcher", Items.SMOKER.getDefaultInstance());
        ICONS.put("cartographer", Items.CARTOGRAPHY_TABLE.getDefaultInstance());
        ICONS.put("cleric", Items.BREWING_STAND.getDefaultInstance());
        ICONS.put("farmer", Items.COMPOSTER.getDefaultInstance());
        ICONS.put("fisherman", Items.BARREL.getDefaultInstance());
        ICONS.put("fletcher", Items.FLETCHING_TABLE.getDefaultInstance());
        ICONS.put("leatherworker", Items.CAULDRON.getDefaultInstance());
        ICONS.put("librarian", Items.LECTERN.getDefaultInstance());
        ICONS.put("mason", Items.STONECUTTER.getDefaultInstance());
        ICONS.put("shepherd", Items.LOOM.getDefaultInstance());
        ICONS.put("toolsmith", Items.SMITHING_TABLE.getDefaultInstance());
        ICONS.put("weaponsmith", Items.GRINDSTONE.getDefaultInstance());

        ICONS.put("mca.outlaw", Items.BLACK_BANNER.getDefaultInstance());
        ICONS.put("mca.guard", Items.IRON_SWORD.getDefaultInstance());
        ICONS.put("mca.archer", Items.BOW.getDefaultInstance());
        ICONS.put("mca.adventurer", Items.MAP.getDefaultInstance());
        ICONS.put("mca.mercenary", Items.EMERALD.getDefaultInstance());
        ICONS.put("mca.cultist", Items.BOOK.getDefaultInstance());
    }
}
