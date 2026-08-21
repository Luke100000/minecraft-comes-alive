package net.conczin.mca.fabric;

import net.conczin.mca.fabric.resources.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
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
import net.conczin.mca.fabric.cobalt.network.NetworkHandlerImpl;
import net.conczin.mca.fabric.resources.*;
import net.conczin.mca.item.ItemsMCA;
import net.conczin.mca.network.MessagesMCA;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.command.AdminCommand;
import net.conczin.mca.server.command.Command;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.PackType;

import java.util.function.Consumer;

public final class MCAFabric implements ModInitializer {
    static {
        MCA.platformHelper = new FabricPlatformHelper();
    }

    private static <T> void registerHelper(Registry<T> registry, Consumer<MCA.RegisterHelper<T>> consumer) {
        consumer.accept((name, value) -> Registry.register(registry, name, value));
    }

    @Override
    public void onInitialize() {
        new NetworkHandlerImpl();

        registerHelper(BuiltInRegistries.BLOCK, BlocksMCA::registerBlocks);
        registerHelper(BuiltInRegistries.BLOCK_ENTITY_TYPE, BlockEntityTypesMCA::registerBlockEntityTypes);
        registerHelper(BuiltInRegistries.ENTITY_TYPE, EntitiesMCA::registerEntities);
        registerHelper(BuiltInRegistries.SOUND_EVENT, SoundsMCA::registerSounds);
        registerHelper(BuiltInRegistries.PARTICLE_TYPE, ParticleTypesMCA::registerParticles);
        registerHelper(BuiltInRegistries.SENSOR_TYPE, ActivityMCA::registerSensors);
        registerHelper(BuiltInRegistries.ACTIVITY, ActivityMCA::registerActivities);
        registerHelper(BuiltInRegistries.MEMORY_MODULE_TYPE, MemoryModuleTypeMCA::registerTypes);
        registerHelper(BuiltInRegistries.VILLAGER_PROFESSION, ProfessionsMCA::registerProfessions);
        registerHelper(BuiltInRegistries.ITEM, ItemsMCA::registerItems);
        registerHelper(BuiltInRegistries.CREATIVE_MODE_TAB, ItemsMCA::registerCreativeModeTab);
        EntitiesMCA.registerAttributes(FabricDefaultAttributeRegistry::register);

        BlocksMCA.bootstrap();
        ItemsMCA.bootstrap();
        EntitiesMCA.bootstrap();
        MessagesMCA.bootstrap();
        CriterionMCA.bootstrap();

        TradeOffersMCA.bootstrap();

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ApiIdentifiableReloadListener());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricClothingList());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricHairList());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricBodySkinList());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricLayeredHairList());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricHairStyleList());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricGiftLoader());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricDialogues());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricTasks());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricNames());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricBuildingTypes());

        ServerTickEvents.END_WORLD_TICK.register(w -> VillageManager.get(w).tick());
        ServerTickEvents.END_SERVER_TICK.register(s -> ServerInteractionManager.getInstance().tick());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerInteractionManager.getInstance().onPlayerJoin(handler.player)
        );

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                ServerInteractionManager.getInstance().onPlayerRespawn(newPlayer)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdminCommand.register(dispatcher);
            Command.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(MCA::setServer);
    }
}

