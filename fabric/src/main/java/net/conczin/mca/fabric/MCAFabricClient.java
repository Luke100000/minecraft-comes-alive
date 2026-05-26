package net.conczin.mca.fabric;

import net.conczin.mca.ClientProxyAbstractImpl;
import net.conczin.mca.Config;
import net.conczin.mca.KeyBindings;
import net.conczin.mca.MCAClient;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.client.particle.InteractionParticle;
import net.conczin.mca.client.gui.MCAScreens;
import net.conczin.mca.client.resources.ColorPaletteLoader;
import net.conczin.mca.client.render.*;
import net.conczin.mca.network.Network;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ParticleTypesMCA;
import net.conczin.mca.resources.ApiReloadListener;
import net.conczin.mca.resources.Supporters;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.player.Player;

public final class MCAFabricClient extends ClientProxyAbstractImpl implements ClientModInitializer {
    private static void registerReloadListener(ResourceLoader loader, net.minecraft.resources.Identifier id, PreparableReloadListener listener) {
        loader.registerReloadListener(id, listener);
    }

    @Override
    public void onInitializeClient() {
        Network.registerClientSender(ClientPlayNetworking::send);

        if (Config.getInstance().useSquidwardModels) {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER, VillagerRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER, VillagerRenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
        } else {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER, VillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER, VillagerEntityMCARenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
        }

        EntityRenderers.register(EntitiesMCA.GRIM_REAPER, GrimReaperRenderer::new);
        EntityRenderers.register(EntitiesMCA.CRIB, CribEntityRenderer::new);

        ParticleProviderRegistry.getInstance().register(ParticleTypesMCA.NEG_INTERACTION, InteractionParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(ParticleTypesMCA.POS_INTERACTION, InteractionParticle.Factory::new);

        BlockEntityRenderers.register(BlockEntityTypesMCA.TOMBSTONE, TombstoneBlockEntityRenderer::new);

        // Register resource reload listeners
        ResourceLoader resourceLoader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        registerReloadListener(resourceLoader, MCAScreens.ID, new MCAScreens());
        registerReloadListener(resourceLoader, ColorPaletteLoader.ID, new ColorPaletteLoader());
        registerReloadListener(resourceLoader, Supporters.ID, new Supporters());
        registerReloadListener(resourceLoader, ApiReloadListener.ID, new ApiReloadListener());

        ClientPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MCAClient.onLogin()
        );

        ClientTickEvents.START_CLIENT_TICK.register(MCAClient::tickClient);

        KeyBindings.list.forEach(KeyMappingHelper::registerKeyMapping);
    }

    @Override
    public Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
