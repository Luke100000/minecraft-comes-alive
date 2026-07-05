package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.Infectable;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.jetbrains.annotations.Nullable;
import java.util.Set;

@SuppressWarnings({"rawtypes", "unchecked"})
public class VillagerLikeEntityMCARenderer<T extends Mob & VillagerLike<T>>
    extends HumanoidMobRenderer<T, VillagerRenderState, VillagerEntityModelMCA> {
    private static final Identifier TEXTURE = Identifier.parse("textures/entity/steve.png");

    public VillagerLikeEntityMCARenderer(EntityRendererProvider.Context ctx, VillagerEntityModelMCA model) {
        super(ctx, model, 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, createArmorModelSet(), ctx.getEquipmentRenderer()));
    }

    private ArmorModelSet<VillagerEntityBaseModelMCA> createArmorModelSet() {
        return new ArmorModelSet<>(
            createArmorModel(0.55F, EquipmentSlot.HEAD),
            createArmorModel(0.55F, EquipmentSlot.CHEST),
            createArmorModel(0.3F, EquipmentSlot.LEGS),
            createArmorModel(0.55F, EquipmentSlot.FEET)
        );
    }

    private VillagerEntityBaseModelMCA createArmorModel(float dilation, EquipmentSlot slot) {
        MeshDefinition mesh = VillagerEntityBaseModelMCA.getModelData(new CubeDeformation(dilation));
        if (slot == EquipmentSlot.HEAD) {
            mesh.getRoot().retainPartsAndChildren(armorParts(slot));
        } else {
            mesh.getRoot().retainExactParts(armorParts(slot));
        }
        return new VillagerEntityBaseModelMCA(LayerDefinition.create(mesh, 64, 32).bakeRoot());
    }

    private Set<String> armorParts(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Set.of("head");
            case CHEST -> Set.of("body", "left_arm", "right_arm", "breasts");
            case LEGS -> Set.of("left_leg", "right_leg", "body");
            case FEET -> Set.of("left_leg", "right_leg");
            default -> throw new IllegalStateException("No armor model parts for slot: " + slot);
        };
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(T mob, HumanoidArm arm) {
        ItemStack itemStack = mob.getItemHeldByArm(arm);
        InteractionHand hand = arm == mob.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (mob.isUsingItem() && mob.getUsedItemHand() == hand) {
            ItemUseAnimation anim = itemStack.getUseAnimation();
            if (anim == ItemUseAnimation.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (anim == ItemUseAnimation.CROSSBOW) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
        }
        return super.getArmPose(mob, arm);
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public void extractRenderState(T entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        VillagerStateHolder holder = VillagerStateHolder.require(state);
        holder.mca$setVillagerRenderData(VillagerRenderData.create(VillagerLike.PlayerModel.VILLAGER, entity));
        state.panicAnimationProgress = entity.getVillagerBrain().getPanicAnimationProgress(partialTicks);
        VillagerRenderStateHooks.extractScaledBounds(entity, state);
    }

    @Override
    protected void scale(VillagerRenderState state, PoseStack matrices) {
        VillagerVisuals visuals = VillagerVisuals.require(state);
        float height = visuals.rawVerticalScaleFactor();
        float width = visuals.rawHorizontalScaleFactor();
        matrices.scale(width, height, width);
        if (visuals.baby() && !state.isPassenger && !state.hasPose(Pose.SLEEPING)) {
            matrices.translate(0.0F, 0.6F, 0.0F);
        }
    }

    @Nullable
    @Override
    protected RenderType getRenderType(VillagerRenderState state, boolean showBody, boolean translucent, boolean showOutlines) {
        // MCA draws the layered body manually, so the base body render has to stay disabled.
        return null;
    }

    @Override
    protected boolean shouldShowName(T villager, double distanceToCameraSq) {
        Player player = Minecraft.getInstance().player;
        return villager.getCustomName() != null
               && !(Minecraft.getInstance().gui.screen() instanceof VillagerEditorScreen)
               && player != null
               && Config.getInstance().showNameTags
               && player.distanceToSqr(villager) < Math.pow(Config.getInstance().nameTagDistance, 2.0f)
               && !villager.isInvisibleTo(player);
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        if (state instanceof VillagerStateHolder holder) {
            VillagerVisuals visuals = holder.mca$getVisuals();
            if (visuals != null) {
                return DynamicSkinCache.getOrCreateStitchedSkin(visuals);
            }
        }
        return TEXTURE;
    }

    @Override
    protected boolean isShaking(VillagerRenderState state) {
        return VillagerVisuals.require(state).infectionProgress() > Infectable.FEVER_THRESHOLD;
    }
}
