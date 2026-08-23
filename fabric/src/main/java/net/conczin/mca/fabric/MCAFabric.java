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
import net.conczin.mca.network.s2c.AppearanceCatalogSync;
import net.conczin.mca.registry.*;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.EyeCatalog;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.LayeredHairList;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.command.AdminCommand;
import net.conczin.mca.server.command.Command;
import net.conczin.mca.server.world.data.VillageManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
                ServerPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> ctx.server().execute(() -> payload.handle(ctx.player())));
            } else {
                PayloadTypeRegistry.playS2C().register(type, codec);
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                    ClientProxy.register(type);
                }
            }
        }
    };

    private static <T> void registerHelper(Registry<T> register, Consumer<MCA.RegisterHelper<T>> consumer) {
        consumer.accept((name, value) -> Registry.register(register, name, value));
    }

    private static void registerReloadListener(ResourceManagerHelper managerHelper, ResourceLocation id, PreparableReloadListener listener) {
        managerHelper.registerReloadListener(new FabricReloadListener<>(id, listener));
    }

    @Override
    public void onInitialize() {
        registerHelper(BuiltInRegistries.ITEM, ItemsMCA::registerItems);
        registerHelper(BuiltInRegistries.BLOCK, BlocksMCA::registerBlocks);
        registerHelper(BuiltInRegistries.SOUND_EVENT, SoundsMCA::registerSounds);
        registerHelper(BuiltInRegistries.PARTICLE_TYPE, ParticleTypesMCA::registerParticles);
        registerHelper(BuiltInRegistries.ENTITY_TYPE, EntitiesMCA::registerEntities);
        registerHelper(BuiltInRegistries.SENSOR_TYPE, SensorsMCA::registerSensors);
        registerHelper(BuiltInRegistries.ACTIVITY, ActivitiesMCA::registerActivities);
        registerHelper(BuiltInRegistries.MEMORY_MODULE_TYPE, MemoryModuleTypeMCA::registerTypes);
        registerHelper(BuiltInRegistries.VILLAGER_PROFESSION, ProfessionsMCA::registerProfessions);
        registerHelper(BuiltInRegistries.DATA_COMPONENT_TYPE, DataComponentsMCA::registerProfessions);
        registerHelper(BuiltInRegistries.TRIGGER_TYPES, CriterionMCA::registerCriteria);

        TradeOffersMCA.bootstrap();
        SchedulesMCA.bootstrap();
        TagsMCA.Blocks.bootstrap();
        TagsMCA.Items.bootstrap();

        BlockEntityTypesMCA.registerBlockEntityTypes((name, factory, blocks) ->
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, name, BlockEntityType.Builder.of(factory::create, blocks).build(null)));

        EntitiesMCA.registerAttributes(FabricDefaultAttributeRegistry::register);
        MessagesMCA.register(fabricRegistrar);
        Network.registerSender(ServerPlayNetworking::send);

        // Register resource reload listeners
        ResourceManagerHelper managerHelper = ResourceManagerHelper.get(PackType.SERVER_DATA);
        managerHelper.registerReloadListener(new ApiIdentifiableReloadListener());
        registerReloadListener(managerHelper, BodySkinList.ID, new BodySkinList());
        managerHelper.registerReloadListener(new FabricClothingList());
        registerReloadListener(managerHelper, HairStyleList.ID, new HairStyleList());
        registerReloadListener(managerHelper, LayeredHairList.ID, new LayeredHairList());
        registerReloadListener(managerHelper, EyeCatalog.ID, new EyeCatalog());
        managerHelper.registerReloadListener(new FabricGiftLoader());
        managerHelper.registerReloadListener(new FabricDialogues());
        managerHelper.registerReloadListener(new FabricTasks());
        managerHelper.registerReloadListener(new FabricNames());
        managerHelper.registerReloadListener(new FabricBuildingTypes());

        // Create the creative mode tab
        ResourceKey<CreativeModeTab> mcaTab = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), MCA.locate("mca_tab"));
        CreativeModeTab build = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.mca.mca_tab"))
                .icon(() -> new ItemStack(ItemsMCA.ENGAGEMENT_RING))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, mcaTab, build);
        ItemGroupEvents.modifyEntriesEvent(mcaTab).register(itemGroup -> {
            List<Item> reversed = new ArrayList<>(ItemsMCA.ITEMS.values());
            Collections.reverse(reversed);
            reversed.forEach(itemGroup::prepend);
        });

        // Register events
        ServerTickEvents.END_WORLD_TICK.register(w -> VillageManager.get(w).tick());
        ServerTickEvents.END_SERVER_TICK.register(s -> ServerInteractionManager.getInstance().tick());
        ServerTickEvents.END_SERVER_TICK.register(MCA::setServer);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            EyeCatalog eyeCatalog = EyeCatalog.getInstance();
            if (success && eyeCatalog != null) {
                eyeCatalog.repairLoaded(server);
            }
        });
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> AppearanceCatalogSync.send(player));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerInteractionManager.getInstance().onPlayerJoin(handler.player)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdminCommand.register(dispatcher);
            Command.register(dispatcher);
        });
    }

    private static final class ClientProxy {
        public static <T extends HandleablePayload> void register(HandleablePayload.Type<T> type) {
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> ctx.client().execute(() -> payload.handle(ctx.player())));
        }
    }
}

