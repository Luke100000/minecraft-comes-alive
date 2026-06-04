package net.conczin.mca.fabric;

import net.conczin.mca.ClientProxyAbstractImpl;
import net.conczin.mca.Config;
import net.conczin.mca.KeyBindings;
import net.conczin.mca.MCAClient;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.client.particle.InteractionParticle;
import net.conczin.mca.client.render.*;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.GrimReaperEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.fabric.client.gui.FabricMCAScreens;
import net.conczin.mca.fabric.resources.ApiIdentifiableReloadListener;
import net.conczin.mca.fabric.resources.FabricColorPaletteLoader;
import net.conczin.mca.fabric.resources.FabricSupportersLoader;
import net.conczin.mca.network.Network;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ModelPredicatesMCA;
import net.conczin.mca.registry.ParticleTypesMCA;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;

public final class MCAFabricClient extends ClientProxyAbstractImpl implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Network.registerClientSender(new Network.ClientSender() {
            @Override
            public void sendToServer(net.conczin.mca.network.HandleablePayload payload) {
                ClientPlayNetworking.send(payload);
            }
        });

        if (Config.getInstance().useSquidwardModels) {
            EntityRendererRegistry.register(EntitiesMCA.MALE_VILLAGER, new EntityRendererProvider<VillagerEntityMCA>() {
                @Override
                @SuppressWarnings("unchecked")
                public net.minecraft.client.renderer.entity.EntityRenderer<VillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return (net.minecraft.client.renderer.entity.EntityRenderer<VillagerEntityMCA, ?>) (Object) new VillagerRenderer(ctx);
                }
            });
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_VILLAGER, new EntityRendererProvider<VillagerEntityMCA>() {
                @Override
                @SuppressWarnings("unchecked")
                public net.minecraft.client.renderer.entity.EntityRenderer<VillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return (net.minecraft.client.renderer.entity.EntityRenderer<VillagerEntityMCA, ?>) (Object) new VillagerRenderer(ctx);
                }
            });

            EntityRendererRegistry.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER, new EntityRendererProvider<ZombieVillagerEntityMCA>() {
                @Override
                @SuppressWarnings("unchecked")
                public net.minecraft.client.renderer.entity.EntityRenderer<ZombieVillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return (net.minecraft.client.renderer.entity.EntityRenderer<ZombieVillagerEntityMCA, ?>) (Object) new ZombieVillagerRenderer(ctx);
                }
            });
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, new EntityRendererProvider<ZombieVillagerEntityMCA>() {
                @Override
                @SuppressWarnings("unchecked")
                public net.minecraft.client.renderer.entity.EntityRenderer<ZombieVillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return (net.minecraft.client.renderer.entity.EntityRenderer<ZombieVillagerEntityMCA, ?>) (Object) new ZombieVillagerRenderer(ctx);
                }
            });
        } else {
            EntityRendererRegistry.register(EntitiesMCA.MALE_VILLAGER, new EntityRendererProvider<VillagerEntityMCA>() {
                @Override
                public net.minecraft.client.renderer.entity.EntityRenderer<VillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return new VillagerEntityMCARenderer(ctx);
                }
            });
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_VILLAGER, new EntityRendererProvider<VillagerEntityMCA>() {
                @Override
                public net.minecraft.client.renderer.entity.EntityRenderer<VillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return new VillagerEntityMCARenderer(ctx);
                }
            });

            EntityRendererRegistry.register(EntitiesMCA.MALE_ZOMBIE_VILLAGER, new EntityRendererProvider<ZombieVillagerEntityMCA>() {
                @Override
                public net.minecraft.client.renderer.entity.EntityRenderer<ZombieVillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return new ZombieVillagerEntityMCARenderer(ctx);
                }
            });
            EntityRendererRegistry.register(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, new EntityRendererProvider<ZombieVillagerEntityMCA>() {
                @Override
                public net.minecraft.client.renderer.entity.EntityRenderer<ZombieVillagerEntityMCA, ?> create(EntityRendererProvider.Context ctx) {
                    return new ZombieVillagerEntityMCARenderer(ctx);
                }
            });
        }

        EntityRendererRegistry.register(EntitiesMCA.GRIM_REAPER, new EntityRendererProvider<GrimReaperEntity>() {
            @Override
            public net.minecraft.client.renderer.entity.EntityRenderer<GrimReaperEntity, ?> create(EntityRendererProvider.Context ctx) {
                return new GrimReaperRenderer(ctx);
            }
        });
        EntityRendererRegistry.register(EntitiesMCA.CRIB, new EntityRendererProvider<CribEntity>() {
            @Override
            public net.minecraft.client.renderer.entity.EntityRenderer<CribEntity, ?> create(EntityRendererProvider.Context ctx) {
                return new CribEntityRenderer(ctx);
            }
        });

        ParticleFactoryRegistry.getInstance().register(ParticleTypesMCA.NEG_INTERACTION, new ParticleFactoryRegistry.PendingParticleFactory<net.minecraft.core.particles.SimpleParticleType>() {
            @Override
            public net.minecraft.client.particle.ParticleProvider<net.minecraft.core.particles.SimpleParticleType> create(net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider sprite) {
                return new InteractionParticle.Factory(sprite);
            }
        });
        ParticleFactoryRegistry.getInstance().register(ParticleTypesMCA.POS_INTERACTION, new ParticleFactoryRegistry.PendingParticleFactory<net.minecraft.core.particles.SimpleParticleType>() {
            @Override
            public net.minecraft.client.particle.ParticleProvider<net.minecraft.core.particles.SimpleParticleType> create(net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider sprite) {
                return new InteractionParticle.Factory(sprite);
            }
        });

        BlockEntityRendererRegistry.register(BlockEntityTypesMCA.TOMBSTONE, TombstoneBlockEntityRenderer::new);

        // Register resource reload listeners
        ResourceManagerHelper managerHelper = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        managerHelper.registerReloadListener(new FabricMCAScreens());
        managerHelper.registerReloadListener(new FabricColorPaletteLoader());
        managerHelper.registerReloadListener(new FabricSupportersLoader());
        managerHelper.registerReloadListener(new ApiIdentifiableReloadListener());

        ModelPredicatesMCA.setup();

        ClientPlayConnectionEvents.JOIN.register(new ClientPlayConnectionEvents.Join() {
            @Override
            public void onPlayReady(net.minecraft.client.multiplayer.ClientPacketListener handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, net.minecraft.client.Minecraft client) {
                MCAClient.onLogin();
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(new ClientTickEvents.StartTick() {
            @Override
            public void onStartTick(net.minecraft.client.Minecraft client) {
                MCAClient.tickClient(client);
            }
        });

        for (var keyBinding : KeyBindings.list) {
            KeyBindingHelper.registerKeyBinding(keyBinding);
        }
    }

    @Override
    public Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
