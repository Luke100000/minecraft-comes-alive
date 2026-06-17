package net.conczin.mca.ducks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;

public interface PlayerRendererMCA {
    boolean mca$renderHand(AbstractClientPlayer player, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, boolean rightArm, boolean hasSleeve);
}
