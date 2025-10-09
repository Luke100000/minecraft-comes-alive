package net.mca.quilt;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.particle.ParticleProviderRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;
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

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(new QuiltMCAScreens());
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(new QuiltColorPaletteLoader());
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(new QuiltSupportersLoader());
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(new ApiIdentifiableReloadListener());

        ModelPredicatesMCA.setup(ItemProperties::register);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MCAClient.onLogin()
        );

        BlockRenderLayerMap.put(RenderType.cutout(), BlocksMCA.INFERNAL_FLAME.get());

        ClientTickEvents.START.register(MCAClient::tickClient);

        KeyBindings.list.forEach(KeyBindingHelper::registerKeyBinding);
    }

    @Override
    public Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
