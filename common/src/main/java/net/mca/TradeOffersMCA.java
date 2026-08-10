package net.mca;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.mca.item.ItemsMCA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import java.util.Map;

public class TradeOffersMCA {
    public static Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> createTradeMap() {
        return ImmutableMap.of(
                ProfessionsMCA.ADVENTURER.get(), new Int2ObjectOpenHashMap<>(
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
                        )),

                ProfessionsMCA.CULTIST.get(), new Int2ObjectOpenHashMap<>(
                        ImmutableMap.of(1, new VillagerTrades.ItemListing[]{
                                        new SellItemFactory(ItemsMCA.SIRBEN_BABY_BOY.get(), 5, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.SIRBEN_BABY_GIRL.get(), 5, 1, 1, 1),
                                        new BuyForOneEmeraldFactory(ItemsMCA.BABY_BOY.get(), 1, 1, 1),
                                        new BuyForOneEmeraldFactory(ItemsMCA.BABY_GIRL.get(), 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_CULT_0.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_CULT_0.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_CULT_1.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_CULT_1.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_CULT_2.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_CULT_2.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_DEATH.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_INFECTION.get(), 1, 1, 1, 1),
                                        new SellItemFactory(ItemsMCA.BOOK_SUPPORTERS.get(), 1, 1, 1, 1)
                                },
                                2, new VillagerTrades.ItemListing[]{},
                                3, new VillagerTrades.ItemListing[]{},
                                4, new VillagerTrades.ItemListing[]{},
                                5, new VillagerTrades.ItemListing[]{}
                        ))
        );
    }

    public static void bootstrap() {
        VillagerTrades.TRADES.putAll(createTradeMap());
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
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            ItemStack itemStack = new ItemStack(this.buy, this.price);
            return new MerchantOffer(itemStack, new ItemStack(Items.EMERALD), this.maxUses, this.experience, this.multiplier);
        }
    }

    static class SellItemFactory implements VillagerTrades.ItemListing {
        private final ItemStack sell;
        private final int price;
        private final int count;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public SellItemFactory(Block block, int price, int count, int maxUses, int experience) {
            this(new ItemStack(block), price, count, maxUses, experience);
        }

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
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            return new MerchantOffer(new ItemStack(Items.EMERALD, this.price), new ItemStack(this.sell.getItem(), this.count), this.maxUses, this.experience, this.multiplier);
        }
    }
}
