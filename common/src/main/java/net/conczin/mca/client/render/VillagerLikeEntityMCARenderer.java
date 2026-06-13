package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.Infectable;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class VillagerLikeEntityMCARenderer<T extends Mob & VillagerLike<T>>
        extends HumanoidMobRenderer<T, VillagerRenderState, VillagerEntityModelMCA> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/entity/steve.png");

    public VillagerLikeEntityMCARenderer(EntityRendererProvider.Context ctx, VillagerEntityModelMCA model) {
        super(ctx, model, 0.5F);
        addLayer(new HumanoidArmorLayer<>(
                this,
                createArmorModel(0.3F),
                createArmorModel(0.55F),
                createArmorModel(0.3F),
                createArmorModel(0.55F),
                ctx.getEquipmentRenderer()
        ));
    }

    private VillagerEntityBaseModelMCA createArmorModel(float modelSize) {
        return new VillagerEntityBaseModelMCA(
                LayerDefinition.create(VillagerEntityBaseModelMCA.getModelData(new CubeDeformation(modelSize)), 64, 32).bakeRoot()
        );
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public void extractRenderState(T entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        VillagerStateHolder holder = VillagerStateHolder.require(state);
        holder.mca$setPlayerModel(VillagerLike.PlayerModel.VILLAGER);
        holder.mca$setVisualSnapshot(VillagerVisualSnapshot.capture(entity));
        state.panicAnimationProgress = entity.getVillagerBrain().isPanicking() ? 1.0F : 0.0F;
        state.cribPassenger = entity.getVehicle() instanceof CribEntity;
        VillagerRenderStateHooks.extractScaledBounds(entity, state);
    }

    @Override
    protected void scale(VillagerRenderState state, PoseStack matrices) {
        VillagerVisualSnapshot visuals = VillagerVisualSnapshot.require(state);
        float height = visuals.rawVerticalScaleFactor();
        float width = visuals.rawHorizontalScaleFactor();
        matrices.scale(width, height, width);
        if (visuals.baby() && (!state.isPassenger || state.cribPassenger) && !visuals.sleeping()) {
            matrices.translate(0, 0.6F, 0);
        }
    }

    @Nullable
    @Override
    protected RenderType getRenderType(VillagerRenderState state, boolean showBody, boolean translucent, boolean showOutlines) {
        return null;
    }

    @Override
    protected boolean shouldShowName(T villager, double distanceToCameraSq) {
        Player player = Minecraft.getInstance().player;
        return villager.getCustomName() != null
               && !(Minecraft.getInstance().screen instanceof VillagerEditorScreen)
               && player != null
               && Config.getInstance().showNameTags
               && player.distanceToSqr(villager) < Math.pow(Config.getInstance().nameTagDistance, 2.0f)
               && !villager.isInvisibleTo(player);
    }

    @Override
    public ResourceLocation getTextureLocation(VillagerRenderState state) {
        return TEXTURE;
    }

    @Override
    protected boolean isShaking(VillagerRenderState state) {
        return VillagerVisualSnapshot.require(state).infectionProgress() > Infectable.FEVER_THRESHOLD;
    }
}
