package net.conczin.mca.client.render;

import net.conczin.mca.entity.ReaperAttackState;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;

public class GrimReaperRenderState extends UndeadRenderState {
    public ReaperAttackState attackState = ReaperAttackState.IDLE;
}
