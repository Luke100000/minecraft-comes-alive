package net.conczin.mca.client.render;

import net.conczin.mca.entity.GrimReaperEntity;
import net.conczin.mca.entity.ReaperAttackState;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import org.jspecify.annotations.Nullable;

public class GrimReaperRenderState extends UndeadRenderState {
    public @Nullable GrimReaperEntity reaper;
    public ReaperAttackState attackState = ReaperAttackState.IDLE;
}
