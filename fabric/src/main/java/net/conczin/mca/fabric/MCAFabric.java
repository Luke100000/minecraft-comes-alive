package net.conczin.mca.fabric;

import net.conczin.mca.MCA;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.SensorsMCA;
import net.conczin.mca.fabric.resources.*;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.MessagesMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.registry.*;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.command.AdminCommand;
import net.conczin.mca.server.command.Command;
import net.conczin.mca.server.world.data.VillageManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class MCAFabric implements ModInitializer {
    static {
        MCA.platformHelper = new FabricPlatformHelper();
    }

    Network.Registrar fabricRegistrar = new Network.Registrar() {
        @Override
        public <T extends HandleablePayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean isServer) {
            if (isServer) {
                PayloadTypeRegistry.playC2S().register(type, codec);
                ServerPlayNetworking.registerGlobalReceiver(type, new ServerPlayNetworking.PlayPayloadHandler<T>() {
                    @Override
                    public void receive(T payload, ServerPlayNetworking.Context ctx) {
                        ctx.server().execute(new Runnable() {
                            @Override
                            public void run() {
                                payload.handle(ctx.player());
                            }
                        });
                    }
                });
            } else {
                PayloadTypeRegistry.playS2C().register(type, codec);
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                    ClientProxy.register(type);
                }
            }
        }
    };

    private static <T> void registerHelper(Registry<T> register, Consumer<MCA.RegisterHelper<T>> consumer) {
        consumer.accept(new MCA.RegisterHelper<T>() {
            @Override
            public void register(Identifier name, T value) {
                Registry.register(register, name, value);
            }
        });
    }

    @Override
    public void onInitialize() {
        registerHelper(BuiltInRegistries.ITEM, helper -> ItemsMCA.registerItems(helper));
        registerHelper(BuiltInRegistries.BLOCK, helper -> BlocksMCA.registerBlocks(helper));
        registerHelper(BuiltInRegistries.SOUND_EVENT, helper -> SoundsMCA.registerSounds(helper));
        registerHelper(BuiltInRegistries.PARTICLE_TYPE, helper -> ParticleTypesMCA.registerParticles(helper));
        registerHelper(BuiltInRegistries.ENTITY_TYPE, helper -> EntitiesMCA.registerEntities(helper));
        registerHelper(BuiltInRegistries.SENSOR_TYPE, helper -> SensorsMCA.registerSensors(helper));
        registerHelper(BuiltInRegistries.ACTIVITY, helper -> ActivitiesMCA.registerActivities(helper));
        registerHelper(BuiltInRegistries.MEMORY_MODULE_TYPE, helper -> MemoryModuleTypeMCA.registerTypes(helper));
        registerHelper(BuiltInRegistries.VILLAGER_PROFESSION, helper -> ProfessionsMCA.registerProfessions(helper));
        registerHelper(BuiltInRegistries.DATA_COMPONENT_TYPE, helper -> DataComponentsMCA.registerProfessions(helper));
        registerHelper(BuiltInRegistries.TRIGGER_TYPES, helper -> CriterionMCA.registerCriteria(helper));

        TradeOffersMCA.bootstrap();
        SchedulesMCA.bootstrap();
        TagsMCA.Blocks.bootstrap();
        TagsMCA.Items.bootstrap();

        BlockEntityTypesMCA.registerBlockEntityTypes(new BlockEntityTypesMCA.TriFunction() {
            @Override
            public BlockEntityType apply(Identifier name, BlockEntityTypesMCA.BlockEntitySupplier constructor, Block[] blocks) {
                BlockEntityType blockEntityType = createBlockEntityType(constructor, blocks);
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, name, blockEntityType);
                return blockEntityType;
            }
        });

        EntitiesMCA.registerAttributes(new MCA.AttributeRegisterHelper() {
            @Override
            public void register(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> entity, net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes) {
                FabricDefaultAttributeRegistry.register(entity, attributes);
            }
        });
        MessagesMCA.register(fabricRegistrar);
        Network.registerSender((player, payload) -> ServerPlayNetworking.send(player, payload));

        // Register resource reload listeners
        ResourceManagerHelper managerHelper = ResourceManagerHelper.get(PackType.SERVER_DATA);
        managerHelper.registerReloadListener(new ApiIdentifiableReloadListener());
        managerHelper.registerReloadListener(new FabricClothingList());
        managerHelper.registerReloadListener(new FabricHairList());
        managerHelper.registerReloadListener(new FabricGiftLoader());
        managerHelper.registerReloadListener(new FabricDialogues());
        managerHelper.registerReloadListener(new FabricTasks());
        managerHelper.registerReloadListener(new FabricNames());
        managerHelper.registerReloadListener(new FabricBuildingTypes());

        // Create the creative mode tab
        ResourceKey<CreativeModeTab> mcaTab = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), MCA.locate("mca_tab"));
        CreativeModeTab build = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.mca.mca_tab"))
                .icon(new java.util.function.Supplier<ItemStack>() {
                    @Override
                    public ItemStack get() {
                        return new ItemStack(ItemsMCA.ENGAGEMENT_RING);
                    }
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, mcaTab, build);
        ItemGroupEvents.modifyEntriesEvent(mcaTab).register(new ItemGroupEvents.ModifyEntries() {
            @Override
            public void modifyEntries(FabricItemGroupEntries itemGroup) {
                List<Item> reversed = new ArrayList<>(ItemsMCA.ITEMS.values());
                Collections.reverse(reversed);
                for (Item item : reversed) {
                    itemGroup.prepend(item);
                }
            }
        });

        // Register events
        ServerTickEvents.END_WORLD_TICK.register(new ServerTickEvents.EndWorldTick() {
            @Override
            public void onEndTick(net.minecraft.server.level.ServerLevel world) {
                VillageManager.get(world).tick();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
            @Override
            public void onEndTick(net.minecraft.server.MinecraftServer server) {
                ServerInteractionManager.getInstance().tick();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
            @Override
            public void onEndTick(net.minecraft.server.MinecraftServer server) {
                MCA.setServer(server);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerInteractionManager.getInstance().onPlayerJoin(handler.player)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdminCommand.register(dispatcher);
            Command.register(dispatcher);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockEntityType createBlockEntityType(BlockEntityTypesMCA.BlockEntitySupplier factory, Block[] blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::create, blocks).build();
    }

    private static final class ClientProxy {
        public static <T extends HandleablePayload> void register(HandleablePayload.Type<T> type) {
            ClientPlayNetworking.registerGlobalReceiver(type, new ClientPlayNetworking.PlayPayloadHandler<T>() {
                @Override
                public void receive(T payload, ClientPlayNetworking.Context ctx) {
                    ctx.client().execute(new Runnable() {
                        @Override
                        public void run() {
                            payload.handle(ctx.player());
                        }
                    });
                }
            });
        }
    }
}

