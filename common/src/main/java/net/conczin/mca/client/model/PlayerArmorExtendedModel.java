package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.render.PlayerBodyOverlayPhysics;
import net.conczin.mca.client.render.PlayerInteractionAnimationManager;
import net.conczin.mca.client.render.SkinLayers3dCompat;
import net.conczin.mca.client.render.PlayerRenderContext;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;

public class PlayerArmorExtendedModel extends PlayerModel implements CommonVillagerModel<LivingEntity> {
    private static final CubeDeformation INNER_ARMOR_DEFORMATION = new CubeDeformation(0.5F);
    private static final CubeDeformation OUTER_ARMOR_DEFORMATION = new CubeDeformation(1.0F);

    public final ModelPart breasts;
    private final boolean supportsBreastOverlay;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;

    public PlayerArmorExtendedModel(ModelPart root, boolean supportsBreastOverlay) {
        super(root, false);
        this.supportsBreastOverlay = supportsBreastOverlay;
        this.breasts = root.getChild(BREASTS);
        this.breasts.visible = false;
        SkinLayers3dCompat.setIgnored(this, true);
        this.jacket.visible = false;
        this.leftSleeve.visible = false;
        this.rightSleeve.visible = false;
        this.leftPants.visible = false;
        this.rightPants.visible = false;
    }

    public static ArmorModelSet<PlayerArmorExtendedModel> createArmorModels() {
        ArmorModelSet<MeshDefinition> armorMeshSet = PlayerModel.createArmorMeshSet(INNER_ARMOR_DEFORMATION, OUTER_ARMOR_DEFORMATION);
        return new ArmorModelSet<>(
                createArmorModel(armorMeshSet.head(), OUTER_ARMOR_DEFORMATION, false),
                createArmorModel(armorMeshSet.chest(), OUTER_ARMOR_DEFORMATION, true),
                createArmorModel(armorMeshSet.legs(), INNER_ARMOR_DEFORMATION, false),
                createArmorModel(armorMeshSet.feet(), OUTER_ARMOR_DEFORMATION, false)
        );
    }

    private static PlayerArmorExtendedModel createArmorModel(MeshDefinition mesh, CubeDeformation breastDilation, boolean supportsBreastOverlay) {
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(BREASTS, supportsBreastOverlay ? VillagerEntityModelMCA.newBreasts(breastDilation, 0) : CubeListBuilder.create(), PartPose.ZERO);
        return new PlayerArmorExtendedModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), supportsBreastOverlay);
    }

    @Override
    public ModelPart getBreastPart() {
        return breasts;
    }

    @Override
    public ModelPart getBodyPart() {
        return body;
    }

    @Override
    public Iterable<ModelPart> getCommonHeadParts() {
        return ImmutableList.of(head);
    }

    @Override
    public Iterable<ModelPart> getCommonBodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts);
    }

    @Override
    public VillagerDimensions.Mutable getDimensions() {
        return dimensions;
    }

    @Override
    public float getBreastSize() {
        return breastSize;
    }

    @Override
    public void setBreastSize(float breastSize) {
        this.breastSize = breastSize;
    }

    @Override
    public void setupAnim(AvatarRenderState renderState) {
        super.setupAnim(renderState);
        this.jacket.visible = false;
        this.leftSleeve.visible = false;
        this.rightSleeve.visible = false;
        this.leftPants.visible = false;
        this.rightPants.visible = false;

        PlayerRenderContext.currentPlayerUuid().ifPresent(uuid -> {
            MCAClient.getPlayerData(uuid).ifPresent(villager -> {
                applyVillagerDimensions(villager, renderState.isCrouching);
                if (supportsBreastOverlay) {
                    PlayerBodyOverlayPhysics.applyTo(this, uuid, renderState.isCrouching, partialTick(renderState));
                } else {
                    breasts.visible = false;
                }
            });
            PlayerInteractionAnimationManager.applyToHumanoidModel(uuid, this, renderState.ageInTicks);
        });

        SkinLayers3dCompat.clearInjectedMeshes(this);
        if (!supportsBreastOverlay) {
            this.breasts.visible = false;
        }
    }

    private static float partialTick(AvatarRenderState renderState) {
        return renderState.ageInTicks - (float) Math.floor(renderState.ageInTicks);
    }
}
