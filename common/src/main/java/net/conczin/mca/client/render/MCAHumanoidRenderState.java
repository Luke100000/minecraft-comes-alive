package net.conczin.mca.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.LivingEntity;

public class MCAHumanoidRenderState extends HumanoidRenderState {
    public LivingEntity villager;
    public boolean visible;
    public boolean glowing;
    public boolean cribPassenger;
}
