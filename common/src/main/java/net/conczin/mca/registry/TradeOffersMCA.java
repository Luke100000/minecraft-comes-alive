package net.conczin.mca.registry;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.conczin.mca.MCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.trading.TradeSet;

public final class TradeOffersMCA {
    public static final ResourceKey<TradeSet> ADVENTURER_LEVEL_1 = createTradeSet("adventurer/level_1");
    public static final ResourceKey<TradeSet> CULTIST_LEVEL_1 = createTradeSet("cultist/level_1");
    public static final Int2ObjectMap<ResourceKey<TradeSet>> ADVENTURER_TRADES = Int2ObjectMap.ofEntries(
            Int2ObjectMap.entry(1, ADVENTURER_LEVEL_1)
    );
    public static final Int2ObjectMap<ResourceKey<TradeSet>> CULTIST_TRADES = Int2ObjectMap.ofEntries(
            Int2ObjectMap.entry(1, CULTIST_LEVEL_1)
    );

    private TradeOffersMCA() {
    }

    private static ResourceKey<TradeSet> createTradeSet(String path) {
        return ResourceKey.create(Registries.TRADE_SET, MCA.locate(path));
    }

    public static void bootstrap() {
        // 26.1 villager trades are data-driven. MCA trade data now lives under
        // common/src/main/resources/data/mca/{trade_set,tags/villager_trade,villager_trade}.
    }
}
