package net.mca.forge;

import net.mca.MCA;
import net.mca.MCAClient;
import net.mca.server.ServerInteractionManager;
import net.mca.server.command.AdminCommand;
import net.mca.server.command.Command;
import net.mca.server.world.data.VillageManager;
import net.mca.util.recipes.CribRecipeProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Events that listen on the forge event bus.
 *
 * @see {@link MinecraftForge#EVENT_BUS}
 */
@Mod.EventBusSubscriber(modid = MCA.MOD_ID)
public class ForgeBusEvents {
    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        AdminCommand.register(event.getDispatcher());
        Command.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (!event.level.isClientSide && event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            VillageManager.get((ServerLevel)event.level).tick();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            ServerInteractionManager.getInstance().tick();
        }
        MCA.setServer(event.getServer());
    }

    @SubscribeEvent
    public static void OnEntityJoinWorldEvent(EntityJoinLevelEvent event) {
        if (event.getEntity().level().isClientSide) {
            if (Minecraft.getInstance().player == null || event.getEntity().getUUID().equals(Minecraft.getInstance().player.getUUID())) {
                MCAClient.onLogin();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        ServerInteractionManager.getInstance().onPlayerJoin((ServerPlayer)event.getEntity());
    }

    @SubscribeEvent
    public static void onParticleFactoryRegistration(TickEvent.ClientTickEvent event) {
        MCAClient.tickClient(Minecraft.getInstance());
    }
}
