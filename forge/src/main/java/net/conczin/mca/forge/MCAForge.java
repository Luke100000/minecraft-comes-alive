package net.conczin.mca.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.conczin.mca.MCA;
import net.conczin.mca.ParticleTypesMCA;
import net.conczin.mca.ProfessionsMCA;
import net.conczin.mca.SoundsMCA;
import net.conczin.mca.TradeOffersMCA;
import net.conczin.mca.advancement.criterion.CriterionMCA;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.block.BlocksMCA;
import net.conczin.mca.entity.EntitiesMCA;
import net.conczin.mca.entity.ai.ActivityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.interaction.gifts.GiftLoader;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.forge.cobalt.network.NetworkHandlerImpl;
import net.conczin.mca.item.ItemsMCA;
import net.conczin.mca.network.MessagesMCA;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Arrays;
import java.util.function.Consumer;

@Mod(MCA.MOD_ID)
@Mod.EventBusSubscriber(modid = MCA.MOD_ID, bus = Bus.MOD)
public final class MCAForge {
    static {
        MCA.platformHelper = new ForgePlatformHelper();
    }

    @SuppressWarnings("removal")
    public MCAForge() {
        new NetworkHandlerImpl();
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(this::onVillagerTrades);

        BlocksMCA.bootstrap();
        ItemsMCA.bootstrap();
        EntitiesMCA.bootstrap();
        MessagesMCA.bootstrap();
        CriterionMCA.bootstrap();
    }

    private static <T> void registerHelper(RegisterEvent event,
                                           ResourceKey<? extends Registry<T>> registryKey,
                                           Consumer<MCA.RegisterHelper<T>> consumer) {
        event.register(registryKey, helper -> consumer.accept(helper::register));
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        registerHelper(event, Registries.BLOCK, BlocksMCA::registerBlocks);
        registerHelper(event, Registries.BLOCK_ENTITY_TYPE, BlockEntityTypesMCA::registerBlockEntityTypes);
        registerHelper(event, Registries.ENTITY_TYPE, EntitiesMCA::registerEntities);
        registerHelper(event, Registries.SOUND_EVENT, SoundsMCA::registerSounds);
        registerHelper(event, Registries.PARTICLE_TYPE, ParticleTypesMCA::registerParticles);
        registerHelper(event, Registries.SENSOR_TYPE, ActivityMCA::registerSensors);
        registerHelper(event, Registries.ACTIVITY, ActivityMCA::registerActivities);
        registerHelper(event, Registries.MEMORY_MODULE_TYPE, MemoryModuleTypeMCA::registerTypes);
        registerHelper(event, Registries.VILLAGER_PROFESSION, ProfessionsMCA::registerProfessions);
        registerHelper(event, Registries.ITEM, ItemsMCA::registerItems);
        registerHelper(event, Registries.CREATIVE_MODE_TAB, ItemsMCA::registerCreativeModeTab);
    }

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        EntitiesMCA.registerAttributes((type, builder) -> event.put(type, builder.build()));
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new net.conczin.mca.resources.ApiReloadListener());
        event.addListener(new net.conczin.mca.resources.ClothingList());
        event.addListener(new net.conczin.mca.resources.HairList());
        event.addListener(new net.conczin.mca.resources.BodySkinList());
        event.addListener(new net.conczin.mca.resources.LayeredHairList());
        event.addListener(new net.conczin.mca.resources.HairStyleList());
        event.addListener(new GiftLoader());
        event.addListener(new net.conczin.mca.resources.Dialogues());
        event.addListener(new net.conczin.mca.resources.Tasks());
        event.addListener(new net.conczin.mca.resources.Names());
        event.addListener(new BuildingTypes());
    }

    private void onVillagerTrades(VillagerTradesEvent event) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> trades = TradeOffersMCA.createTradeMap().get(event.getType());
        if (trades == null) {
            return;
        }

        trades.int2ObjectEntrySet().forEach(entry ->
                event.getTrades().get(entry.getIntKey()).addAll(Arrays.asList(entry.getValue()))
        );
    }
}
