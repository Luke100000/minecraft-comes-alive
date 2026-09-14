package net.conczin.mca.client.render;

import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class VillagerRenderState extends UndeadRenderState {
    public boolean isConverting;
    public float panicAnimationProgress;
    public @Nullable Vec3 fishingHookPosition;
}
