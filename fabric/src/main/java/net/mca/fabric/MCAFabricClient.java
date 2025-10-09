package net.mca.fabric;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.particle.ParticleProviderRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.mca.ClientProxyAbstractImpl;
import net.mca.Config;
import net.mca.KeyBindings;
import net.mca.MCAClient;
import net.mca.ModelPredicatesMCA;
import net.mca.ParticleTypesMCA;
import net.mca.block.BlockEntityTypesMCA;
import net.mca.block.BlocksMCA;
import net.mca.client.particle.InteractionParticle;
import net.mca.client.render.CribEntityRenderer;
import net.mca.client.render.GrimReaperRenderer;
import net.mca.client.render.TombstoneBlockEntityRenderer;
import net.mca.client.render.VillagerEntityMCARenderer;
import net.mca.client.render.ZombieVillagerEntityMCARenderer;
import net.mca.entity.EntitiesMCA;
import net.mca.fabric.client.gui.FabricMCAScreens;
import net.mca.fabric.resources.ApiIdentifiableReloadListener;
import net.mca.fabric.resources.FabricColorPaletteLoader;
import net.mca.fabric.resources.FabricSupportersLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;

public final class MCAFabricClient extends ClientProxyAbstractImpl implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (Config.getInstance().useSquidwardModels) {
            EntityRendererRegistry.register(EntitiesMCA.MALE_VILLAGER, VillagerRenderer::new);
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_VILLAGER, VillagerRenderer::new);

            EntityRendererRegistry.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
        } else {
            EntityRendererRegistry.register(EntitiesMCA.MALE_VILLAGER, VillagerEntityMCARenderer::new);
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_VILLAGER, VillagerEntityMCARenderer::new);

            EntityRendererRegistry.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
        }

        EntityRendererRegistry.register(EntitiesMCA.GRIM_REAPER, GrimReaperRenderer::new);
        EntityRendererRegistry.register(EntitiesMCA.CRIB, CribEntityRenderer::new);

        ParticleProviderRegistry.register(ParticleTypesMCA.NEG_INTERACTION.get(), InteractionParticle.Factory::new);
        ParticleProviderRegistry.register(ParticleTypesMCA.POS_INTERACTION.get(), InteractionParticle.Factory::new);

        BlockEntityRendererRegistry.register(BlockEntityTypesMCA.TOMBSTONE.get(), TombstoneBlockEntityRenderer::new);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricMCAScreens());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricColorPaletteLoader());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricSupportersLoader());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ApiIdentifiableReloadListener());

        ModelPredicatesMCA.setup(ItemProperties::register);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MCAClient.onLogin()
        );

        BlockRenderLayerMap.INSTANCE.putBlock(BlocksMCA.INFERNAL_FLAME.get(), RenderType.cutout());

        ClientTickEvents.START_CLIENT_TICK.register(MCAClient::tickClient);

        KeyBindings.list.forEach(KeyBindingHelper::registerKeyBinding);
    }

    @Override
    public Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
