package net.conczin.mca.mixin.client;

import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinHumanoidArmorLayer {
    @Unique
    protected final HumanoidModel<?> mca$leggingsModel = mca$createModel(0.5F);
    @Unique
    protected final HumanoidModel<?> mca$bodyModel = mca$createModel(1.0F);

    @Shadow
    protected abstract boolean usesInnerModel(EquipmentSlot slot);

    @Unique
    private HumanoidModel<?> mca$createModel(float dilation) {
        return new PlayerArmorExtendedModel<>(LayerDefinition.create(VillagerEntityModelMCA.armorData(new CubeDeformation(dilation)), 64, 64).bakeRoot());
    }

    @Inject(method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;", at = @At("HEAD"), cancellable = true)
    private void mca$injectGetArmorModel(HumanoidRenderState state, EquipmentSlot slot, CallbackInfoReturnable<HumanoidModel> cir) {
        if (!(state instanceof AvatarRenderState) || !(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
            return;
        }

        // Read the item directly from the render state for this slot — no mutable capture field needed.
        // HumanoidArmorLayer.renderArmorPiece() already guards that equippable != null and
        // assetId().isPresent() before calling getArmorModel(), so both checks below are always safe.
        ItemStack itemStack = mca$getEquipmentForSlot(state, slot);
        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return; // should never happen given vanilla's guard, but defensive

        // Only substitute MCA's shaped model for vanilla minecraft equipment assets.
        // Modded assets use IClientItemExtensions.getGenericArmorModel() which can return null
        // for custom model paths — substituting there would cause a null model crash.
        if (!equippable.assetId().map(key -> key.identifier().getNamespace().equals("minecraft")).orElse(false)) {
            return;
        }

        HumanoidModel<?> model = this.usesInnerModel(slot) ? mca$leggingsModel : mca$bodyModel;
        cir.setReturnValue((HumanoidModel) model);
    }

    @Unique
    private static ItemStack mca$getEquipmentForSlot(HumanoidRenderState state, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> state.headEquipment;
            case CHEST -> state.chestEquipment;
            case LEGS -> state.legsEquipment;
            case FEET -> state.feetEquipment;
            default -> ItemStack.EMPTY;
        };
    }
}
