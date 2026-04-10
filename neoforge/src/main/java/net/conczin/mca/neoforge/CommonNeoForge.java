package net.conczin.mca.neoforge;

import net.conczin.mca.MCA;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.SensorsMCA;
import net.conczin.mca.entity.interaction.gifts.GiftLoader;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.MessagesMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.registry.*;
import net.conczin.mca.resources.*;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.command.AdminCommand;
import net.conczin.mca.server.command.Command;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Mod(MCA.MOD_ID)
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class CommonNeoForge {
    static {
        MCA.platformHelper = new NeoforgePlatformHelper();
        try {
            java.lang.reflect.Field field = net.neoforged.neoforge.common.CommonHooks.class.getDeclaredField("EDA_CHECKED_CLASSES");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<Class<?>> edaCheckedClasses = (java.util.Set<Class<?>>) field.get(null);
            edaCheckedClasses.add(net.conczin.mca.util.network.datasync.CDataParameter.class);
            edaCheckedClasses.add(net.conczin.mca.util.network.datasync.CEnumParameter.class);
            edaCheckedClasses.add(net.conczin.mca.util.network.datasync.CDataManager.class);
            edaCheckedClasses.add(net.conczin.mca.util.network.datasync.CDataManager.Builder.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        registerHelper(event, NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, helper -> {
            helper.register(MCA.locate("compound_tag"), CParameter.COMPOUND_TAG_SERIALIZER);
            helper.register(MCA.locate("optional_uuid"), CParameter.OPTIONAL_UUID_SERIALIZER);
        });

        if (event.getRegistryKey() == Registries.BLOCK_ENTITY_TYPE) {
            event.register(Registries.BLOCK_ENTITY_TYPE, helper ->
                    BlockEntityTypesMCA.registerBlockEntityTypes((name, factory, block) -> {
                        BlockEntityType<?> build = createBlockEntityType(factory, block);
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
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(MCA.locate("api"), new ApiReloadListener());
        event.addListener(MCA.locate("clothing"), new ClothingList());
        event.addListener(MCA.locate("hair"), new HairList());
        event.addListener(MCA.locate("gifts"), new GiftLoader());
        event.addListener(MCA.locate("dialogues"), new Dialogues());
        event.addListener(MCA.locate("tasks"), new Tasks());
        event.addListener(MCA.locate("names"), new Names());
        event.addListener(MCA.locate("building_types"), new BuildingTypes());
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
        Network.registerClientSender(ClientPacketDistributor::sendToServer);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityTypesMCA.BlockEntitySupplier<T> factory, Block[] blocks) {
        try {
            Class<?> supplierType = Class.forName("net.minecraft.world.level.block.entity.BlockEntityType$BlockEntitySupplier");
            Object supplier = Proxy.newProxyInstance(
                    BlockEntityType.class.getClassLoader(),
                    new Class<?>[]{supplierType},
                    (proxy, method, args) -> {
                        if ("create".equals(method.getName())) {
                            return factory.create((net.minecraft.core.BlockPos) args[0], (net.minecraft.world.level.block.state.BlockState) args[1]);
                        }
                        if ("toString".equals(method.getName())) {
                            return "MCABlockEntitySupplier";
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        }
                        return null;
                    }
            );
            Constructor<BlockEntityType> constructor = BlockEntityType.class.getDeclaredConstructor(supplierType, Set.class);
            constructor.setAccessible(true);
            return (BlockEntityType<T>) constructor.newInstance(supplier, new HashSet<>(Arrays.asList(blocks)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create MCA block entity type", e);
        }
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
