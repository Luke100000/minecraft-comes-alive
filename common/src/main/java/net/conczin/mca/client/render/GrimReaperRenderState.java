package net.conczin.mca.client.render;

import net.conczin.mca.entity.ReaperAttackState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class GrimReaperRenderState extends HumanoidRenderState {
    public ReaperAttackState attackState = ReaperAttackState.IDLE;
}
