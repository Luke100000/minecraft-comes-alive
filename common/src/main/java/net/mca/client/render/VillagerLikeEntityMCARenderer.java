package net.mca.client.render;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mca.Config;
import net.mca.client.gui.VillagerEditorScreen;
import net.mca.client.model.VillagerEntityBaseModelMCA;
import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.entity.Infectable;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class VillagerLikeEntityMCARenderer<T extends Mob & VillagerLike<T>> extends HumanoidMobRenderer<T, VillagerEntityModelMCA<T>> {
    public VillagerLikeEntityMCARenderer(EntityRendererProvider.Context ctx, VillagerEntityModelMCA<T> model) {
        super(ctx, model, 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, createArmorModel(0.3f), createArmorModel(0.55f), ctx.getModelManager()));
    }

    private VillagerEntityBaseModelMCA<T> createArmorModel(float modelSize) {
        return new VillagerEntityBaseModelMCA<>(
                LayerDefinition.create(
                                VillagerEntityBaseModelMCA.getModelData(new CubeDeformation(modelSize)), 64, 32)
                        .bakeRoot()
        );
    }

    @Override
    protected void scale(T villager, PoseStack matrices, float tickDelta) {
        float height = villager.getRawScaleFactor();
        float width = villager.getHorizontalScaleFactor();
        matrices.scale(width, height, width);
        if (villager.getAgeState() == AgeState.BABY && !villager.isPassenger()) {
            matrices.translate(0, 0.6F, 0);
        }
    }

    @Nullable
    @Override
    protected RenderType getRenderType(T entity, boolean showBody, boolean translucent, boolean showOutlines) {
        if (entity.hasCustomSkin()) {
            //custom skin
            Minecraft minecraftClient = Minecraft.getInstance();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraftClient.getSkinManager().getInsecureSkinInformation(entity.getGameProfile());
            return map.containsKey(MinecraftProfileTexture.Type.SKIN) ?
                    RenderType.entityTranslucent(
                            minecraftClient.getSkinManager().registerTexture(
                                    map.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN
                            )) :
                    RenderType.entityCutoutNoCull(
                            DefaultPlayerSkin.getDefaultSkin(
                                    UUIDUtil.getOrCreatePlayerUUID(entity.getGameProfile())
                            )
                    );
        }

        //setting the type to null prevents it from rendering
        //we need a skin layer anyway because of the color
        return null;
    }

    @Override
    protected boolean shouldShowName(T villager) {
        Player player = Minecraft.getInstance().player;
        return villager.getCustomName() != null
                && !(Minecraft.getInstance().screen instanceof VillagerEditorScreen)
                && player != null
                && Config.getInstance().showNameTags
                && player.distanceToSqr(villager) < Math.pow(Config.getInstance().nameTagDistance, 2.0f)
                && !villager.isInvisibleTo(player);
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/steve.png");

    @Override
    public ResourceLocation getTextureLocation(T mobEntity) {
        return TEXTURE;
    }

    @Override
    protected boolean isShaking(T entity) {
        return entity.getInfectionProgress() > Infectable.FEVER_THRESHOLD;
    }
}
