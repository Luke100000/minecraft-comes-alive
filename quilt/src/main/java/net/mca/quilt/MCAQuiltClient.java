package net.mca.quilt;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.particle.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.mca.*;
import net.mca.block.BlockEntityTypesMCA;
import net.mca.block.BlocksMCA;
import net.mca.client.particle.InteractionParticle;
import net.mca.client.render.CribEntityRenderer;
import net.mca.client.render.GrimReaperRenderer;
import net.mca.client.render.TombstoneBlockEntityRenderer;
import net.mca.client.render.VillagerEntityMCARenderer;
import net.mca.client.render.ZombieVillagerEntityMCARenderer;
import net.mca.entity.EntitiesMCA;
import net.mca.quilt.client.gui.QuiltMCAScreens;
import net.mca.quilt.resources.ApiIdentifiableReloadListener;
import net.mca.quilt.resources.QuiltColorPaletteLoader;
import net.mca.quilt.resources.QuiltSupportersLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.ZombieVillagerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ResourceType;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;
import org.quiltmc.qsl.block.extensions.api.client.BlockRenderLayerMap;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientTickEvents;
import org.quiltmc.qsl.networking.api.client.ClientPlayConnectionEvents;
import org.quiltmc.qsl.resource.loader.api.ResourceLoader;

@SuppressWarnings("unused")
public final class MCAQuiltClient extends ClientProxyAbstractImpl implements ClientModInitializer {
    @Override
    public void onInitializeClient(ModContainer container) {
        if (Config.getInstance().useSquidwardModels) {
            EntityRendererRegistry.register(EntitiesMCA.MALE_VILLAGER, VillagerEntityRenderer::new);
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_VILLAGER, VillagerEntityRenderer::new);

            EntityRendererRegistry.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerEntityRenderer::new);
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerEntityRenderer::new);
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

        BlockEntityRendererFactories.register(BlockEntityTypesMCA.TOMBSTONE.get(), TombstoneBlockEntityRenderer::new);

        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(new QuiltMCAScreens());
        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(new QuiltColorPaletteLoader());
        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(new QuiltSupportersLoader());
        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(new ApiIdentifiableReloadListener());

        ModelPredicatesMCA.setup(ModelPredicateProviderRegistry::register);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MCAClient.onLogin()
        );

        BlockRenderLayerMap.put(RenderLayer.getCutout(), BlocksMCA.INFERNAL_FLAME.get());

        ClientTickEvents.START.register(MCAClient::tickClient);

        KeyBindings.list.forEach(KeyBindingHelper::registerKeyBinding);
    }

    @Override
    public PlayerEntity getClientPlayer() {
        return MinecraftClient.getInstance().player;
    }
}
