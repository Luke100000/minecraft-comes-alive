package net.conczin.mca.client.render;

import net.conczin.mca.entity.CribEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

public class CribEntityRenderState extends EntityRenderState {
    public @Nullable CribEntity crib;
    public float yRot;
}
