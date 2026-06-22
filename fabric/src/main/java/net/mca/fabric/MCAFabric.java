package net.mca.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.mca.MCA;
import net.mca.ParticleTypesMCA;
import net.mca.SoundsMCA;
import net.mca.TradeOffersMCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.block.BlocksMCA;
import net.mca.entity.EntitiesMCA;
import net.mca.fabric.cobalt.network.NetworkHandlerImpl;
import net.mca.fabric.resources.*;
import net.mca.item.ItemsMCA;
import net.mca.network.MessagesMCA;
import net.mca.server.ServerInteractionManager;
import net.mca.server.command.AdminCommand;
import net.mca.server.command.Command;
import net.mca.server.world.data.VillageManager;
import net.minecraft.resource.ResourceType;

public final class MCAFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        new NetworkHandlerImpl();

        BlocksMCA.bootstrap();
        ItemsMCA.bootstrap();
        SoundsMCA.bootstrap();
        ParticleTypesMCA.bootstrap();
        EntitiesMCA.bootstrap();
        MessagesMCA.bootstrap();
        CriterionMCA.bootstrap();

        TradeOffersMCA.bootstrap();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new ApiIdentifiableReloadListener());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricClothingList());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricHairList());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricGiftLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricDialogues());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricTasks());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricNames());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricBuildingTypes());

        ServerTickEvents.END_WORLD_TICK.register(w -> VillageManager.get(w).tick());
        ServerTickEvents.END_SERVER_TICK.register(s -> ServerInteractionManager.getInstance().tick());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerInteractionManager.getInstance().onPlayerJoin(handler.player)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdminCommand.register(dispatcher);
            Command.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(MCA::setServer);
    }
}

