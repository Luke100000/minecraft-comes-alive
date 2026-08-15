package net.conczin.mca.forge;

import net.conczin.mca.Config;
import net.conczin.mca.client.render.TombstoneBlockEntityRenderer;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.block.BlocksMCA;
import net.conczin.mca.client.gui.MCAScreens;
import net.conczin.mca.client.particle.InteractionParticle;
import net.conczin.mca.client.resources.ColorPaletteLoader;
import net.conczin.mca.client.resources.GeneratedEyeTextureReloadListener;
import net.conczin.mca.entity.EntitiesMCA;
import net.conczin.mca.resources.ApiReloadListener;
import net.conczin.mca.resources.FaceList;
import net.conczin.mca.resources.Supporters;
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

@Mod.EventBusSubscriber(modid = net.conczin.mca.MCA.MOD_ID, value = Dist.CLIENT, bus = Bus.MOD)
public final class MCAForgeClient {
    @SubscribeEvent
    public static void data(RegisterClientReloadListenersEvent event) {
        new ClientProxyImpl();
        event.registerReloadListener(new MCAScreens());
        event.registerReloadListener(new ColorPaletteLoader());
        event.registerReloadListener(new FaceList());
        event.registerReloadListener(new GeneratedEyeTextureReloadListener());
        event.registerReloadListener(new Supporters());
        event.registerReloadListener(new ApiReloadListener());
    }

    @SubscribeEvent
    @SuppressWarnings("removal")
    public static void setup(FMLClientSetupEvent event) {
        if (Config.getInstance().useSquidwardModels) {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER.get(), VillagerRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER.get(), VillagerRenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER.get(), ZombieVillagerRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.get(), ZombieVillagerRenderer::new);
        } else {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER.get(), net.conczin.mca.client.render.VillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER.get(), net.conczin.mca.client.render.VillagerEntityMCARenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER.get(), net.conczin.mca.client.render.ZombieVillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.get(), net.conczin.mca.client.render.ZombieVillagerEntityMCARenderer::new);
        }

        EntityRenderers.register(EntitiesMCA.GRIM_REAPER.get(), net.conczin.mca.client.render.GrimReaperRenderer::new);
        EntityRenderers.register(EntitiesMCA.CRIB.get(), net.conczin.mca.client.render.CribEntityRenderer::new);

        BlockEntityRenderers.register(BlockEntityTypesMCA.TOMBSTONE.get(), TombstoneBlockEntityRenderer::new);

        net.conczin.mca.ModelPredicatesMCA.setup(ItemProperties::register);

        ItemBlockRenderTypes.setRenderLayer(BlocksMCA.INFERNAL_FLAME.get(), RenderType.cutout());
    }

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        net.conczin.mca.KeyBindings.list.forEach(event::register);
    }

    @SubscribeEvent
    public static void onParticleFactoryRegistration(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(net.conczin.mca.ParticleTypesMCA.NEG_INTERACTION.get(), InteractionParticle.Factory::new);
        event.registerSpriteSet(net.conczin.mca.ParticleTypesMCA.POS_INTERACTION.get(), InteractionParticle.Factory::new);
    }
}
