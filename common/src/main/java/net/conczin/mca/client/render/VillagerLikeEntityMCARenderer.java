package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.Infectable;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class VillagerLikeEntityMCARenderer<T extends Mob & VillagerLike<T>>
        extends HumanoidMobRenderer<T, MCAHumanoidRenderState, VillagerEntityModelMCA> {
    private static final Identifier TEXTURE = Identifier.parse("minecraft:textures/entity/player/wide/steve.png");

    public VillagerLikeEntityMCARenderer(EntityRendererProvider.Context ctx, VillagerEntityModelMCA model) {
        super(ctx, model, 0.5F);
    }

    private VillagerEntityBaseModelMCA createArmorModel(float modelSize) {
        return new VillagerEntityBaseModelMCA(
                LayerDefinition.create(
                        VillagerEntityBaseModelMCA.getModelData(new CubeDeformation(modelSize)), 64, 32)
                        .bakeRoot());
    }

    @Override
    protected void scale(MCAHumanoidRenderState renderState, PoseStack matrices) {
        if (renderState.villager instanceof VillagerLike<?> villagerLike) {
            float height = villagerLike.getRawVerticalScaleFactor();
            float width = villagerLike.getRawHorizontalScaleFactor();
            matrices.scale(width, height, width);
            if (villagerLike.getAgeState() == AgeState.BABY && (!renderState.isPassenger || renderState.cribPassenger)) {
                matrices.translate(0.0F, 0.6F, 0.0F);
            }
        }
    }

    @Nullable
    @Override
    protected RenderType getRenderType(MCAHumanoidRenderState renderState, boolean showBody, boolean translucent,
            boolean showOutlines) {
        // We intentionally render villagers through MCA layers only.
        return null;
    }

    @Override
    protected boolean isShaking(MCAHumanoidRenderState renderState) {
        if (renderState.villager instanceof Infectable infectable) {
            return infectable.getInfectionProgress() > Infectable.FEVER_THRESHOLD;
        }
        return false;
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
    public Identifier getTextureLocation(MCAHumanoidRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public MCAHumanoidRenderState createRenderState() {
        return new MCAHumanoidRenderState();
    }

    @Override
    public void extractRenderState(T mob, MCAHumanoidRenderState state, float delta) {
        super.extractRenderState(mob, state, delta);
        state.villager = mob;
        state.visible = !mob.isInvisible();
        state.glowing = Minecraft.getInstance().shouldEntityAppearGlowing(mob);
        state.cribPassenger = mob.getVehicle() instanceof CribEntity;
    }
}
