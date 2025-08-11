package net.mca;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.mca.item.ItemsMCA;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

public class TradeOffersMCA {
    public static void bootstrap() {
        try {
            TradeOffers.PROFESSION_TO_LEVELED_TRADE.put(ProfessionsMCA.ADVENTURER.get(), new Int2ObjectOpenHashMap<>(
                ImmutableMap.of(1, new TradeOffers.Factory[] {
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
                        2, new TradeOffers.Factory[] {},
                        3, new TradeOffers.Factory[] {},
                        4, new TradeOffers.Factory[] {},
                        5, new TradeOffers.Factory[] {}
                )));

            TradeOffers.PROFESSION_TO_LEVELED_TRADE.put(ProfessionsMCA.CULTIST.get(), new Int2ObjectOpenHashMap<>(
                ImmutableMap.of(1, new TradeOffers.Factory[] {
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
                        2, new TradeOffers.Factory[] {},
                        3, new TradeOffers.Factory[] {},
                        4, new TradeOffers.Factory[] {},
                        5, new TradeOffers.Factory[] {}
                )));
        } catch (IllegalStateException e) {
            // Handle missing trades for villager types from other mods (e.g., Nature's Spirit)
            // Log the error but don't crash the game
            if (e.getMessage() != null && e.getMessage().contains("Missing trade for villager type")) {
                MCA.LOGGER.warn("Ignoring missing trade error for compatibility with other mods: {}", e.getMessage());
            } else {
                throw e; // Re-throw if it's a different error
            }
        }
    }


    static class BuyForOneEmeraldFactory implements TradeOffers.Factory {
        private final Item buy;
        private final int price;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public BuyForOneEmeraldFactory(ItemConvertible item, int price, int maxUses, int experience) {
            this.buy = item.asItem();
            this.price = price;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = 0.05f;
        }

        @Override
        public TradeOffer create(Entity entity, Random random) {
            ItemStack itemStack = new ItemStack(this.buy, this.price);
            return new TradeOffer(itemStack, new ItemStack(Items.EMERALD), this.maxUses, this.experience, this.multiplier);
        }
    }

    static class SellItemFactory implements TradeOffers.Factory {
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
        public TradeOffer create(Entity entity, Random random) {
            return new TradeOffer(new ItemStack(Items.EMERALD, this.price), new ItemStack(this.sell.getItem(), this.count), this.maxUses, this.experience, this.multiplier);
        }
    }
}
