package net.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mca.MCA;
import net.mca.client.model.CommonVillagerModel;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class FaceLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {
    private static final int FACE_COUNT = 22;

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
        int index = (int) Math.min(FACE_COUNT - 1, Math.max(0, CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.FACE) * FACE_COUNT));
        int time = villager.tickCount / 2 + (int) (CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
        boolean blink = time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDeadOrDying();
        boolean hasHeterochromia = variant.equals("normal") && CommonVillagerModel.getVillager(villager).getTraits().hasTrait(Traits.HETEROCHROMIA);
        String gender = CommonVillagerModel.getVillager(villager).getGenetics().getGender().getDataName();
        String blinkTexture = blink ? "_blink" : (hasHeterochromia ? "_hetero" : "");

        return cached("skins/face/" + variant + "/" + gender + "/" + index + blinkTexture + ".png", MCA::locate);
    }
}
