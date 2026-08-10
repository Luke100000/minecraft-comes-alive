package net.mca.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record EquipmentSet(String mainHand, String offHand, String head, String chest, String legs, String feet) {
    public static final EquipmentSet NAKED = new EquipmentSet("air", "air", "air", "air", "air", "air");

    public static final EquipmentSet GUARD_0 = new EquipmentSet("iron_sword", "air", "air", "iron_chestplate", "leather_leggings", "leather_boots");
    public static final EquipmentSet GUARD_0_LEFT = new EquipmentSet("air", "iron_sword", "air", "iron_chestplate", "leather_leggings", "leather_boots");
    public static final EquipmentSet GUARD_1 = new EquipmentSet("iron_sword", "shield", "iron_helmet", "iron_chestplate", "leather_leggings", "iron_boots");
    public static final EquipmentSet GUARD_2 = new EquipmentSet("diamond_sword", "shield", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots");

    public static final EquipmentSet ARCHER_0 = new EquipmentSet("bow", "air", "air", "leather_chestplate", "air", "air");
    public static final EquipmentSet ARCHER_0_LEFT = new EquipmentSet("air", "bow", "air", "leather_chestplate", "air", "air");
    public static final EquipmentSet ARCHER_1 = new EquipmentSet("bow", "air", "air", "iron_chestplate", "leather_leggings", "leather_boots");
    public static final EquipmentSet ARCHER_1_LEFT = new EquipmentSet("air", "bow", "air", "iron_chestplate", "leather_leggings", "leather_boots");
    public static final EquipmentSet ARCHER_2 = new EquipmentSet("bow", "air", "air", "diamond_chestplate", "leather_leggings", "iron_boots");
    public static final EquipmentSet ARCHER_2_LEFT = new EquipmentSet("air", "bow", "air", "diamond_chestplate", "leather_leggings", "iron_boots");

    public static final EquipmentSet ELITE = new EquipmentSet("netherite_sword", "netherite_sword", "diamond_helmet", "netherite_chestplate", "golden_leggings", "netherite_boots");
    public static final EquipmentSet ROYAL = new EquipmentSet("trident", "diamond_axe", "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots");

    public Item getMainHand() {
        return getItem(mainHand);
    }

    public Item getGetOffHand() {
        return getItem(offHand);
    }

    public Item getHead() {
        return getItem(head);
    }

    public Item getChest() {
        return getItem(chest);
    }

    public Item getLegs() {
        return getItem(legs);
    }

    public Item getFeet() {
        return getItem(feet);
    }

    public static Item getItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return Items.AIR;
        }

        try {
            return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(itemName)).orElse(Items.AIR);
        } catch (RuntimeException ignored) {
            return Items.AIR;
        }
    }
}
