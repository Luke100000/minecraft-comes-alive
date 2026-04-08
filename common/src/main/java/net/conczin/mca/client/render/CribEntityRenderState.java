package net.conczin.mca.client.render;

import net.conczin.mca.entity.CribEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;

public class CribEntityRenderState extends EntityRenderState {
    public @Nullable CribEntity crib;
    public float yRot;
    public final ItemStackRenderState babyItem = new ItemStackRenderState();
}
