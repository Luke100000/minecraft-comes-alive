package net.mca.quilt;

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
import net.mca.item.ItemsMCA;
import net.mca.network.MessagesMCA;
import net.mca.quilt.cobalt.network.NetworkHandlerImpl;
import net.mca.quilt.resources.*;
import net.mca.server.ServerInteractionManager;
import net.mca.server.command.AdminCommand;
import net.mca.server.command.Command;
import net.mca.server.world.data.VillageManager;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.PackType;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.quiltmc.qsl.command.api.CommandRegistrationCallback;
import org.quiltmc.qsl.lifecycle.api.event.ServerTickEvents;
import org.quiltmc.qsl.lifecycle.api.event.ServerWorldTickEvents;
import org.quiltmc.qsl.networking.api.ServerPlayConnectionEvents;
import org.quiltmc.qsl.resource.loader.api.ResourceLoader;

import java.util.function.Consumer;

public final class MCAQuilt implements ModInitializer {
    static {
        MCA.platformHelper = new QuiltPlatformHelper();
    }

    private static <T> void registerHelper(Registry<T> registry, Consumer<MCA.RegisterHelper<T>> consumer) {
        consumer.accept((name, value) -> Registry.register(registry, name, value));
    }

    @Override
    public void onInitialize(ModContainer container) {
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

        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new ApiIdentifiableReloadListener());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltClothingList());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltHairList());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltBodySkinList());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltLayeredHairList());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltHairStyleList());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltGiftLoader());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltDialogues());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltTasks());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltNames());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(new QuiltBuildingTypes());

        ServerWorldTickEvents.END.register((s, w) -> VillageManager.get(w).tick());
        ServerTickEvents.END.register(s -> ServerInteractionManager.getInstance().tick());
        ServerTickEvents.END.register(MCA::setServer);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerInteractionManager.getInstance().onPlayerJoin(handler.player)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, integrated, dedicated) -> {
            AdminCommand.register(dispatcher);
            Command.register(dispatcher);
        });

    }
}

