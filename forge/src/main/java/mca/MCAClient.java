package mca;

import mca.block.BlockEntityTypesMCA;
import mca.block.BlocksMCA;
import mca.client.gui.MCAScreens;
import mca.client.particle.InteractionParticle;
import mca.client.render.GrimReaperRenderer;
import mca.client.render.TombstoneBlockEntityRenderer;
import mca.client.render.VillagerEntityMCARenderer;
import mca.client.render.ZombieVillagerEntityMCARenderer;
import mca.client.resources.ColorPaletteLoader;
import mca.entity.EntitiesMCA;
import mca.item.BabyItem;
import mca.item.ItemsMCA;
import mca.resources.Supporters;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EntityRenderers;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.ZombieVillagerEntityRenderer;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.util.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

@Mod.EventBusSubscriber(modid = mca.MCA.MOD_ID, value = Dist.CLIENT, bus = Bus.MOD)
public final class MCAClient {
    @SubscribeEvent
    public static void data(FMLConstructModEvent event) {
        new ClientProxyImpl();
        ((ReloadableResourceManagerImpl) MinecraftClient.getInstance().getResourceManager()).registerReloader(new MCAScreens());
        ((ReloadableResourceManagerImpl) MinecraftClient.getInstance().getResourceManager()).registerReloader(new ColorPaletteLoader());
        ((ReloadableResourceManagerImpl) MinecraftClient.getInstance().getResourceManager()).registerReloader(new Supporters());
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Config.getInstance().useSquidwardModels) {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER.get(), VillagerEntityRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER.get(), VillagerEntityRenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER.get(), ZombieVillagerEntityRenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.get(), ZombieVillagerEntityRenderer::new);
        } else {
            EntityRenderers.register(EntitiesMCA.MALE_VILLAGER.get(), VillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_VILLAGER.get(), VillagerEntityMCARenderer::new);

            EntityRenderers.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER.get(), ZombieVillagerEntityMCARenderer::new);
            EntityRenderers.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.get(), ZombieVillagerEntityMCARenderer::new);
        }

        EntityRenderers.register(EntitiesMCA.GRIM_REAPER.get(), GrimReaperRenderer::new);

        BlockEntityRendererFactories.register(BlockEntityTypesMCA.TOMBSTONE.get(), TombstoneBlockEntityRenderer::new);

        ModelPredicateProviderRegistry.register(ItemsMCA.BABY_BOY.get(), new Identifier("invalidated"), (stack, world, entity, i) -> {
            return BabyItem.hasBeenInvalidated(stack) ? 1 : 0;
        });
        ModelPredicateProviderRegistry.register(ItemsMCA.BABY_GIRL.get(), new Identifier("invalidated"), (stack, world, entity, i) -> {
            return BabyItem.hasBeenInvalidated(stack) ? 1 : 0;
        });

        RenderLayers.setRenderLayer(BlocksMCA.INFERNAL_FLAME.get(), RenderLayer.getCutout());
    }

    @SubscribeEvent
    public static void onParticleFactoryRegistration(ParticleFactoryRegisterEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.particleManager.registerFactory(ParticleTypesMCA.NEG_INTERACTION.get(), InteractionParticle.Factory::new);
        mc.particleManager.registerFactory(ParticleTypesMCA.POS_INTERACTION.get(), InteractionParticle.Factory::new);
    }
}
