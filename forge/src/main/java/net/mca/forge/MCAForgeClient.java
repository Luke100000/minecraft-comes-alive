package net.mca.forge;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import net.mca.*;
import net.mca.block.BlockEntityTypesMCA;
import net.mca.block.BlocksMCA;
import net.mca.client.gui.MCAScreens;
import net.mca.client.particle.InteractionParticle;
import net.mca.client.render.CribEntityRenderer;
import net.mca.client.render.GrimReaperRenderer;
import net.mca.client.render.TombstoneBlockEntityRenderer;
import net.mca.client.render.VillagerEntityMCARenderer;
import net.mca.client.render.ZombieVillagerEntityMCARenderer;
import net.mca.client.resources.ColorPaletteLoader;
import net.mca.entity.EntitiesMCA;
import net.mca.resources.ApiReloadListener;
import net.mca.resources.Supporters;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MCA.MOD_ID, value = Dist.CLIENT, bus = Bus.MOD)
public final class MCAForgeClient {
    @SubscribeEvent
    public static void data(RegisterClientReloadListenersEvent event) {
        new ClientProxyImpl();
        event.registerReloadListener(new MCAScreens());
        event.registerReloadListener(new ColorPaletteLoader());
        event.registerReloadListener(new Supporters());
        event.registerReloadListener(new ApiReloadListener());
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Config.getInstance().useSquidwardModels) {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER.get(), VillagerRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER.get(), VillagerRenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER.get(), ZombieVillagerRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.get(), ZombieVillagerRenderer::new);
        } else {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER.get(), VillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER.get(), VillagerEntityMCARenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER.get(), ZombieVillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.get(), ZombieVillagerEntityMCARenderer::new);
        }

        EntityRenderers.register(EntitiesMCA.GRIM_REAPER.get(), GrimReaperRenderer::new);
        EntityRenderers.register(EntitiesMCA.CRIB.get(), CribEntityRenderer::new);

        BlockEntityRenderers.register(BlockEntityTypesMCA.TOMBSTONE.get(), TombstoneBlockEntityRenderer::new);

        ModelPredicatesMCA.setup(ItemProperties::register);

        // TODO: Forge has made this deprecated in 1.19 and we're meant to use `render_type` in the model json
        // - remove and replace at a later date instead, since Fabric still uses the older method afaik.
        //noinspection removal
        ItemBlockRenderTypes.setRenderLayer(BlocksMCA.INFERNAL_FLAME.get(), RenderType.cutout());
    }

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        KeyBindings.list.forEach(event::register);
    }

    @SubscribeEvent
    public static void onParticleFactoryRegistration(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticleTypesMCA.NEG_INTERACTION.get(), InteractionParticle.Factory::new);
        event.registerSpriteSet(ParticleTypesMCA.POS_INTERACTION.get(), InteractionParticle.Factory::new);
    }
}
