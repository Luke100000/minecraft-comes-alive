package net.mca.quilt;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.mca.MCA;
import net.mca.ParticleTypesMCA;
import net.mca.SoundsMCA;
import net.mca.TradeOffersMCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.block.BlocksMCA;
import net.mca.entity.EntitiesMCA;
import net.mca.item.ItemsMCA;
import net.mca.network.MessagesMCA;
import net.mca.quilt.cobalt.network.NetworkHandlerImpl;
import net.mca.quilt.resources.*;
import net.mca.server.ServerInteractionManager;
import net.mca.server.command.AdminCommand;
import net.mca.server.command.Command;
import net.mca.server.world.data.VillageManager;
import net.minecraft.resource.ResourceType;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.quiltmc.qsl.command.api.CommandRegistrationCallback;
import org.quiltmc.qsl.lifecycle.api.event.ServerTickEvents;
import org.quiltmc.qsl.lifecycle.api.event.ServerWorldTickEvents;
import org.quiltmc.qsl.networking.api.ServerPlayConnectionEvents;
import org.quiltmc.qsl.resource.loader.api.ResourceLoader;

public final class MCAQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer container) {
        new NetworkHandlerImpl();

        BlocksMCA.bootstrap();
        ItemsMCA.bootstrap();
        SoundsMCA.bootstrap();
        ParticleTypesMCA.bootstrap();
        EntitiesMCA.bootstrap();
        MessagesMCA.bootstrap();
        CriterionMCA.bootstrap();

        TradeOffersMCA.bootstrap();

        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new ApiIdentifiableReloadListener());
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new QuiltClothingList());
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new QuiltHairList());
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new QuiltGiftLoader());
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new QuiltDialogues());
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new QuiltTasks());
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new QuiltNames());
        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(new QuiltBuildingTypes());

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

