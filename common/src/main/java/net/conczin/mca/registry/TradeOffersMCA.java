package net.conczin.mca.registry;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

public class TradeOffersMCA {
    public static void bootstrap() {
        VillagerTrades.TRADES.put(professionKey(ProfessionsMCA.ADVENTURER), new Int2ObjectOpenHashMap<>(
                ImmutableMap.of(1, new VillagerTrades.ItemListing[]{
                                new SellItemFactory(Items.SLIME_BALL, 1, 1, 16, 1),
                                new SellItemFactory(Items.LEATHER_HORSE_ARMOR, 3, 1, 4, 10),
                                new SellItemFactory(Items.SADDLE, 4, 1, 3, 5),
                                new SellItemFactory(Items.IRON_HORSE_ARMOR, 5, 1, 2, 20),
                                new SellItemFactory(Items.DIAMOND, 10, 1, 8, 20),
                                new SellItemFactory(Items.GOLDEN_HORSE_ARMOR, 10, 1, 3, 30),
                                new SellItemFactory(Items.GOLDEN_APPLE, 5, 1, 8, 30),
                                new SellItemFactory(Items.DIAMOND_HORSE_ARMOR, 15, 1, 1, 30),
                                new SellItemFactory(Items.ENCHANTED_GOLDEN_APPLE, 32, 1, 3, 50),
                                new BuyForOneEmeraldFactory(Items.BREAD, 10, 10, 30)
                        },
                        2, new VillagerTrades.ItemListing[]{},
                        3, new VillagerTrades.ItemListing[]{},
                        4, new VillagerTrades.ItemListing[]{},
                        5, new VillagerTrades.ItemListing[]{}
                )));

        VillagerTrades.TRADES.put(professionKey(ProfessionsMCA.CULTIST), new Int2ObjectOpenHashMap<>(
                ImmutableMap.of(1, new VillagerTrades.ItemListing[]{
                                new SellItemFactory(ItemsMCA.SIRBEN_BABY_BOY, 5, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.SIRBEN_BABY_GIRL, 5, 1, 1, 1),
                                new BuyForOneEmeraldFactory(ItemsMCA.BABY_BOY, 1, 1, 1),
                                new BuyForOneEmeraldFactory(ItemsMCA.BABY_GIRL, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_CULT_0, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_CULT_0, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_CULT_1, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_CULT_1, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_CULT_2, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_CULT_2, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_DEATH, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_INFECTION, 1, 1, 1, 1),
                                new SellItemFactory(ItemsMCA.BOOK_SUPPORTERS, 1, 1, 1, 1)
                        },
                        2, new VillagerTrades.ItemListing[]{},
                        3, new VillagerTrades.ItemListing[]{},
                        4, new VillagerTrades.ItemListing[]{},
                        5, new VillagerTrades.ItemListing[]{}
                )));
    }

    private static net.minecraft.resources.ResourceKey<VillagerProfession> professionKey(VillagerProfession profession) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getResourceKey(profession).orElseThrow();
    }


    static class BuyForOneEmeraldFactory implements VillagerTrades.ItemListing {
        private final Item buy;
        private final int price;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public BuyForOneEmeraldFactory(ItemLike item, int price, int maxUses, int experience) {
            this.buy = item.asItem();
            this.price = price;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = 0.05f;
        }

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity entity, RandomSource random) {
            ItemCost cost = new ItemCost(this.buy, this.price);
            return new MerchantOffer(cost, new ItemStack(Items.EMERALD), this.maxUses, this.experience, this.multiplier);
        }
    }

    static class SellItemFactory implements VillagerTrades.ItemListing {
        private final ItemStack sell;
        private final int price;
        private final int count;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public SellItemFactory(Item item, int price, int count, int maxUses, int experience) {
            this(new ItemStack(item), price, count, maxUses, experience);
        }

        public SellItemFactory(ItemStack stack, int price, int count, int maxUses, int experience) {
            this(stack, price, count, maxUses, experience, 0.05f);
        }

        public SellItemFactory(ItemStack stack, int price, int count, int maxUses, int experience, float multiplier) {
            this.sell = stack;
            this.price = price;
            this.count = count;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity entity, RandomSource random) {
            return new MerchantOffer(new ItemCost(Items.EMERALD, this.price), new ItemStack(this.sell.getItem(), this.count), this.maxUses, this.experience, this.multiplier);
        }
    }
}
