package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class FaceLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {
    private final String variant;

    public FaceLayer(RenderLayerParent<T, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        model.setAllVisible(false);
        model.head.visible = true;

        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        FaceList list = FaceList.getInstance();
        if (list == null) {
            return cached("skins/face/" + variant + "/0.png", MCA::locate);
        }
        return list.pick(variant, CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.FACE));
    }

    @Override
    protected ResourceLocation getOverlay(T villager) {
        int time = villager.tickCount / 2 + (int) (CommonVillagerModel.getVillager(villager).getGenetics().getGene(net.conczin.mca.entity.ai.Genetics.HEMOGLOBIN) * 65536);
        boolean blink = time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDeadOrDying();
        if (blink) {
            return cached("skins/face/" + variant + "/blink.png", MCA::locate);
        }
        return null;
    }
}
