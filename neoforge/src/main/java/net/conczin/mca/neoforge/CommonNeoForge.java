package net.conczin.mca.neoforge;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.conczin.mca.MCA;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.SensorsMCA;
import net.conczin.mca.entity.interaction.gifts.GiftLoader;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.MessagesMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.AppearanceCatalogSync;
import net.conczin.mca.registry.*;
import net.conczin.mca.resources.*;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.command.AdminCommand;
import net.conczin.mca.server.command.Command;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Arrays;
import java.util.function.Consumer;

@Mod(MCA.MOD_ID)
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class CommonNeoForge {
    static {
        MCA.platformHelper = new NeoforgePlatformHelper();
    }

    private static <T> void registerHelper(RegisterEvent event, Registry<T> register, Consumer<MCA.RegisterHelper<T>> consumer) {
        event.register(register.key(), registry -> consumer.accept(registry::register));
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        registerHelper(event, BuiltInRegistries.ITEM, ItemsMCA::registerItems);
        registerHelper(event, BuiltInRegistries.BLOCK, BlocksMCA::registerBlocks);
        registerHelper(event, BuiltInRegistries.SOUND_EVENT, SoundsMCA::registerSounds);
        registerHelper(event, BuiltInRegistries.PARTICLE_TYPE, ParticleTypesMCA::registerParticles);
        registerHelper(event, BuiltInRegistries.ENTITY_TYPE, EntitiesMCA::registerEntities);
        registerHelper(event, BuiltInRegistries.SENSOR_TYPE, SensorsMCA::registerSensors);
        registerHelper(event, BuiltInRegistries.ACTIVITY, ActivitiesMCA::registerActivities);
        registerHelper(event, BuiltInRegistries.MEMORY_MODULE_TYPE, MemoryModuleTypeMCA::registerTypes);
        registerHelper(event, BuiltInRegistries.VILLAGER_PROFESSION, ProfessionsMCA::registerProfessions);
        registerHelper(event, BuiltInRegistries.DATA_COMPONENT_TYPE, DataComponentsMCA::registerProfessions);
        registerHelper(event, BuiltInRegistries.TRIGGER_TYPES, CriterionMCA::registerCriteria);

        if (event.getRegistryKey() == Registries.BLOCK_ENTITY_TYPE) {
            event.register(Registries.BLOCK_ENTITY_TYPE, helper ->
                    BlockEntityTypesMCA.registerBlockEntityTypes((name, factory, block) -> {
                        //noinspection DataFlowIssue
                        BlockEntityType<?> build = BlockEntityType.Builder.of(factory::create, block).build(null);
                        helper.register(name, build);
                        return build;
                    }));
        }

        if (event.getRegistryKey() == Registries.CREATIVE_MODE_TAB) {
            CreativeModeTab tab = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mca.mca_tab"))
                    .icon(() -> new ItemStack(ItemsMCA.ENGAGEMENT_RING))
                    .displayItems((params, output) -> {
                        for (Item item : ItemsMCA.ITEMS.values()) {
                            output.accept(new ItemStack(item));
                        }
                    })
                    .build();
            event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(MCA.locate("mca_tab"), tab));
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ApiReloadListener());
        event.addListener(new BodySkinList());
        event.addListener(new ClothingList());
        event.addListener(new HairStyleList());
        event.addListener(new LayeredHairList());
        event.addListener(new EyeCatalog());
        event.addListener(new GiftLoader());
        event.addListener(new Dialogues());
        event.addListener(new Tasks());
        event.addListener(new Names());
        event.addListener(new BuildingTypes());
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> trades = TradeOffersMCA.createTradeMap().get(event.getType());
        if (trades == null) {
            return;
        }

        trades.int2ObjectEntrySet().forEach(entry ->
                event.getTrades().get(entry.getIntKey()).addAll(Arrays.asList(entry.getValue()))
        );
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        AdminCommand.register(event.getDispatcher());
        Command.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            VillageManager.get(serverLevel).tick();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerInteractionManager.getInstance().tick();
        MCA.setServer(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        EyeCatalog eyeCatalog = EyeCatalog.getInstance();
        if (event.getPlayer() == null && eyeCatalog != null) {
            eyeCatalog.repairLoaded(event.getPlayerList().getServer());
        }
        event.getRelevantPlayers().forEach(AppearanceCatalogSync::send);
    }

    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerInteractionManager.getInstance().onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public static void createDefaultAttributes(EntityAttributeCreationEvent event) {
        EntitiesMCA.registerAttributes((type, supplier) -> event.put(type, supplier.build()));
    }

    @SubscribeEvent
    public static void registerNetwork(final RegisterPayloadHandlersEvent event) {
        MessagesMCA.register(new NeoForgeRegistrar(event.registrar("1")));
        Network.registerSender(PacketDistributor::sendToPlayer);
        Network.registerClientSender(PacketDistributor::sendToServer);
    }

    static class NeoForgeRegistrar implements Network.Registrar {
        PayloadRegistrar registrar;

        public NeoForgeRegistrar(PayloadRegistrar registrar) {
            this.registrar = registrar;
        }

        @Override
        public <T extends HandleablePayload> void register(HandleablePayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean isServer) {
            if (isServer) {
                registrar.playToServer(type, codec, (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(ctx.player())));
            } else {
                registrar.playToClient(type, codec, (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(ctx.player())));
            }
        }
    }
}
