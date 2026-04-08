package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.Infectable;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"rawtypes", "unchecked"})
public class VillagerLikeEntityMCARenderer<T extends Mob & VillagerLike<T>>
    extends HumanoidMobRenderer<T, VillagerRenderState, VillagerEntityModelMCA> {
    private static final Identifier TEXTURE = Identifier.parse("textures/entity/steve.png");

    public VillagerLikeEntityMCARenderer(EntityRendererProvider.Context ctx, VillagerEntityModelMCA model) {
        super(ctx, model, 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, createArmorModelSet(0.3F), createArmorModelSet(0.55F), ctx.getEquipmentRenderer()));
    }

    private ArmorModelSet<VillagerEntityBaseModelMCA> createArmorModelSet(float modelSize) {
        return new ArmorModelSet<>(
            createArmorModel(modelSize),
            createArmorModel(modelSize),
            createArmorModel(modelSize),
            createArmorModel(modelSize)
        );
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
        state.mca$setVillager(entity);
        state.mca$setVisualSnapshot(VillagerVisualSnapshot.capture(entity));
    }

    @Override
    protected void scale(VillagerRenderState state, PoseStack matrices) {
        VillagerVisualSnapshot visuals = CommonVillagerModel.getVisuals(state);
        float height = visuals.rawVerticalScaleFactor();
        float width = visuals.rawHorizontalScaleFactor();
        matrices.scale(width, height, width);
        if (visuals.baby() && state.mca$getVillager() instanceof Entity entity && !entity.isPassenger()) {
            matrices.translate(0, 0.6F, 0);
        }
    }

    @Nullable
    @Override
    protected RenderType getRenderType(VillagerRenderState state, boolean showBody, boolean translucent, boolean showOutlines) {
        //setting the type to null prevents it from rendering
        //we need a skin layer anyway because of the color
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
    public Identifier getTextureLocation(VillagerRenderState state) {
        return TEXTURE;
    }

    @Override
    protected boolean isShaking(VillagerRenderState state) {
        return CommonVillagerModel.getVisuals(state).infectionProgress() > Infectable.FEVER_THRESHOLD;
    }
}
