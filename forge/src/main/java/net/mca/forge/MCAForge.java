package net.mca.forge;

import dev.architectury.platform.forge.EventBuses;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.mca.MCA;
import net.mca.ParticleTypesMCA;
import net.mca.SoundsMCA;
import net.mca.TradeOffersMCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.block.BlocksMCA;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.interaction.gifts.GiftLoader;
import net.mca.forge.cobalt.network.NetworkHandlerImpl;
import net.mca.item.ItemsMCA;
import net.mca.network.MessagesMCA;
import net.mca.resources.*;
import net.minecraft.village.TradeOffers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Arrays;

@Mod(MCA.MOD_ID)
@Mod.EventBusSubscriber(modid = MCA.MOD_ID, bus = Bus.MOD)
public final class MCAForge {
    @SuppressWarnings("removal")
    public MCAForge() {

        EventBuses.registerModEventBus(MCA.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        new NetworkHandlerImpl();
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(this::onVillagerTrades);

        BlocksMCA.bootstrap();
        ItemsMCA.bootstrap();
        SoundsMCA.bootstrap();
        ParticleTypesMCA.bootstrap();
        EntitiesMCA.bootstrap();
        MessagesMCA.bootstrap();
        CriterionMCA.bootstrap();
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ApiReloadListener());
        event.addListener(new ClothingList());
        event.addListener(new HairList());
        event.addListener(new GiftLoader());
        event.addListener(new Dialogues());
        event.addListener(new Tasks());
        event.addListener(new Names());
        event.addListener(new BuildingTypes());
    }

    private void onVillagerTrades(VillagerTradesEvent event) {
        Int2ObjectMap<TradeOffers.Factory[]> trades = TradeOffersMCA.createTradeMap().get(event.getType());
        if (trades == null) {
            return;
        }

        trades.int2ObjectEntrySet().forEach(entry ->
                event.getTrades().get(entry.getIntKey()).addAll(Arrays.asList(entry.getValue()))
        );
    }
}
