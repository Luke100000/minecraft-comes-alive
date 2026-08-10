package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;
import java.util.Locale;

/**
 * Confirms the building type selected by the player after an ambiguous scan.
 */
public class ConfirmBuildingPolymorphMessage implements Message {
    @Serial
    private static final long serialVersionUID = 7603852075326624913L;

    private final long source;
    private final boolean strictScan;
    private final String chosenType;

    public ConfirmBuildingPolymorphMessage(BlockPos source, boolean strictScan, String chosenType) {
        this.source = source.asLong();
        this.strictScan = strictScan;
        this.chosenType = chosenType;
    }

    @Override
    public void receive(ServerPlayer player) {
        VillageManager villages = VillageManager.get(player.serverLevel());
        Building.validationResult result = villages.processBuilding(BlockPos.of(source), true, strictScan, chosenType);
        player.displayClientMessage(Component.translatable("blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
    }
}
