package net.mca.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.mca.MCA;
import net.mca.ParticleTypesMCA;
import net.mca.ProfessionsMCA;
import net.mca.SoundsMCA;
import net.mca.TradeOffersMCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.block.BlockEntityTypesMCA;
import net.mca.block.BlocksMCA;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.interaction.gifts.GiftLoader;
import net.mca.forge.cobalt.network.NetworkHandlerImpl;
import net.mca.item.ItemsMCA;
import net.mca.network.MessagesMCA;
import net.mca.resources.*;
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
        event.addListener(new ApiReloadListener());
        event.addListener(new ClothingList());
        event.addListener(new HairList());
        event.addListener(new BodySkinList());
        event.addListener(new LayeredHairList());
        event.addListener(new HairStyleList());
        event.addListener(new GiftLoader());
        event.addListener(new Dialogues());
        event.addListener(new Tasks());
        event.addListener(new Names());
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
