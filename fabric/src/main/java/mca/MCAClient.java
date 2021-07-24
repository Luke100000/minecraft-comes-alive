package mca;

import mca.block.BlockEntityTypesMCA;
import mca.client.gui.FabricMCAScreens;
import mca.client.particle.InteractionParticle;
import mca.client.render.GrimReaperRenderer;
import mca.client.render.TombstoneBlockEntityRenderer;
import mca.client.render.VillagerEntityMCARenderer;
import mca.entity.EntitiesMCA;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;

public final class MCAClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.INSTANCE.register(EntitiesMCA.MALE_VILLAGER, (dispatcher, ctx) -> new VillagerEntityMCARenderer(dispatcher));
        EntityRendererRegistry.INSTANCE.register(EntitiesMCA.FEMALE_VILLAGER, (dispatcher, ctx) ->  new VillagerEntityMCARenderer(dispatcher));
        EntityRendererRegistry.INSTANCE.register(EntitiesMCA.GRIM_REAPER, (dispatcher, ctx) -> new GrimReaperRenderer(dispatcher));

        ParticleFactoryRegistry.getInstance().register(ParticleTypesMCA.NEG_INTERACTION, InteractionParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ParticleTypesMCA.POS_INTERACTION, InteractionParticle.Factory::new);

        BlockEntityRendererRegistry.INSTANCE.register(BlockEntityTypesMCA.TOMBSTONE, TombstoneBlockEntityRenderer::new);

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new FabricMCAScreens());
    }
}
