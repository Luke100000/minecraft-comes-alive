package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.Locale;

/**
 * Confirms the building type selected by the player after an ambiguous scan.
 */
public class ConfirmBuildingPolymorphMessage implements Message {
    @Serial
    private static final long serialVersionUID = 7603852075326624913L;

    private final BlockPos source;
    private final boolean strictScan;
    private final String chosenType;

    public ConfirmBuildingPolymorphMessage(BlockPos source, boolean strictScan, String chosenType) {
        this.source = source;
        this.strictScan = strictScan;
        this.chosenType = chosenType;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        VillageManager villages = VillageManager.get(player.getServerWorld());
        Building.validationResult result = villages.processBuilding(source, true, strictScan, chosenType);
        player.sendMessage(Text.translatable("blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
    }
}
