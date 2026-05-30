package net.conczin.mca.neoforge;

import com.google.common.reflect.TypeToken;
import net.conczin.mca.ClientProxyAbstractImpl;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.client.gui.MCAScreens;
import net.conczin.mca.client.particle.InteractionParticle;
import net.conczin.mca.client.render.*;
import net.conczin.mca.client.resources.ColorPaletteLoader;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ParticleTypesMCA;
import net.conczin.mca.resources.ApiReloadListener;
import net.conczin.mca.resources.Supporters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

@EventBusSubscriber(modid = MCA.MOD_ID, value = Dist.CLIENT)
public final class ClientNeoForge extends ClientProxyAbstractImpl {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Block entity renderers
        event.registerBlockEntityRenderer(BlockEntityTypesMCA.TOMBSTONE, TombstoneBlockEntityRenderer::new);

        // Entity renderers
        if (Config.getInstance().useSquidwardModels) {
            event.registerEntityRenderer(EntitiesMCA.MALE_VILLAGER, VillagerRenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_VILLAGER, VillagerRenderer::new);
            event.registerEntityRenderer(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
        } else {
            event.registerEntityRenderer(EntitiesMCA.MALE_VILLAGER, VillagerEntityMCARenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_VILLAGER, VillagerEntityMCARenderer::new);
            event.registerEntityRenderer(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
        }
        event.registerEntityRenderer(EntitiesMCA.GRIM_REAPER, GrimReaperRenderer::new);
        event.registerEntityRenderer(EntitiesMCA.CRIB, CribEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticleTypesMCA.NEG_INTERACTION, InteractionParticle.Factory::new);
        event.registerSpriteSet(ParticleTypesMCA.POS_INTERACTION, InteractionParticle.Factory::new);
    }

    @SubscribeEvent
    public static void data(AddClientReloadListenersEvent event) {
        new ClientNeoForge();

        event.addListener(MCA.locate("screens"), new MCAScreens());
        event.addListener(MCA.locate("color_palettes"), new ColorPaletteLoader());
        event.addListener(MCA.locate("supporters"), new Supporters());
        event.addListener(MCA.locate("api"), new ApiReloadListener());
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        net.conczin.mca.KeyBindings.list.forEach(event::register);
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
                },
                (entity, state) -> {
                    VillagerRenderStateHooks.extract(entity, state);
                    VillagerRenderStateHooks.extractScaledBounds(entity, state);
                });
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
        });
    }

    @SubscribeEvent
    public static void onClientConnected(ClientPlayerNetworkEvent.LoggingIn event) {
        MCAClient.onLogin();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        MCAClient.tickClient(Minecraft.getInstance());
    }
}
