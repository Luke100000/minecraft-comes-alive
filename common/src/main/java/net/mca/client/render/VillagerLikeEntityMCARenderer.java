package net.mca.client.render;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.mca.Config;
import net.mca.client.gui.VillagerEditorScreen;
import net.mca.client.model.VillagerEntityBaseModelMCA;
import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.entity.Infectable;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class VillagerLikeEntityMCARenderer<T extends MobEntity & VillagerLike<T>> extends BipedEntityRenderer<T, VillagerEntityModelMCA<T>> {
    private static final double CARRIED_NAME_TAG_Y = 0.63;

    public VillagerLikeEntityMCARenderer(EntityRendererFactory.Context ctx, VillagerEntityModelMCA<T> model) {
        super(ctx, model, 0.5F);
        addFeature(new ArmorFeatureRenderer<>(this, createArmorModel(0.3f), createArmorModel(1.0f), ctx.getModelManager()));
    }

    private VillagerEntityBaseModelMCA<T> createArmorModel(float modelSize) {
        return new VillagerEntityBaseModelMCA<>(
                TexturedModelData.of(
                                VillagerEntityBaseModelMCA.getModelData(new Dilation(modelSize)), 64, 32)
                        .createModel()
        );
    }

    @Override
    protected void scale(T villager, MatrixStack matrices, float tickDelta) {
        float height = villager.getRawVerticalScaleFactor();
        float width = villager.getRawHorizontalScaleFactor();
        matrices.scale(width, height, width);
        if (villager.getAgeState() == AgeState.BABY && !villager.hasVehicle()) {
            matrices.translate(0, 0.6F, 0);
        }
    }

    @Nullable
    @Override
    protected RenderLayer getRenderLayer(T entity, boolean showBody, boolean translucent, boolean showOutlines) {
        if (entity.hasCustomSkin()) {
            //custom skin
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraftClient.getSkinProvider().getTextures(entity.getGameProfile());
            return map.containsKey(MinecraftProfileTexture.Type.SKIN) ?
                    RenderLayer.getEntityTranslucent(
                            minecraftClient.getSkinProvider().loadSkin(
                                    map.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN
                            )) :
                    RenderLayer.getEntityCutoutNoCull(
                            DefaultSkinHelper.getTexture(
                                    Uuids.getUuidFromProfile(entity.getGameProfile())
                            )
                    );
        }

        //setting the type to null prevents it from rendering
        //we need a skin layer anyway because of the color
        return null;
    }

    @Override
    protected boolean hasLabel(T villager) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        return villager.getCustomName() != null
                && !(MinecraftClient.getInstance().currentScreen instanceof VillagerEditorScreen)
                && player != null
                && Config.getInstance().showNameTags
                && player.squaredDistanceTo(villager) < Math.pow(Config.getInstance().nameTagDistance, 2.0f)
                && !villager.isInvisibleTo(player);
    }

    @Override
    protected void renderLabelIfPresent(T villager, Text displayName, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!(villager.getVehicle() instanceof PlayerEntity)) {
            super.renderLabelIfPresent(villager, displayName, matrices, vertexConsumers, light);
            return;
        }

        matrices.push();
        matrices.translate(0.0D, CARRIED_NAME_TAG_Y + 0.5D - villager.getNameLabelHeight(), 0.0D);
        super.renderLabelIfPresent(villager, displayName, matrices, vertexConsumers, light);
        matrices.pop();
    }

    @Override
    public Identifier getTexture(T mobEntity) {
        return DynamicSkinCache.getOrCreateStitchedSkin(mobEntity);
    }

    @Override
    protected boolean isShaking(T entity) {
        return entity.getInfectionProgress() > Infectable.FEVER_THRESHOLD;
    }
}
