package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidArmorLayer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinHumanoidArmorLayer {
    @Unique
    private final ArmorModelSet<HumanoidModel<?>> mca$armorModels = VillagerEntityModelMCA.armorData()
            .map(MixinHumanoidArmorLayer::mca$createModel);

    @Unique
    private static HumanoidModel<?> mca$createModel(MeshDefinition mesh) {
        return new PlayerArmorExtendedModel<>(LayerDefinition.create(mesh, 64, 32).bakeRoot());
    }

    @ModifyReturnValue(
            method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;",
            at = @At("RETURN")
    )
    private HumanoidModel<?> mca$selectArmorModel(HumanoidModel<?> original, HumanoidRenderState state, EquipmentSlot slot) {
        if (!(state instanceof AvatarRenderState) || !(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
            return original;
        }

        ItemStack itemStack = mca$getEquipmentForSlot(state, slot);
        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return original;
        }

        // Only substitute MCA's shaped model for vanilla equipment assets.
        // Custom equipment should keep its own model and renderer hooks.
        if (!equippable.assetId().map(key -> key.identifier().getNamespace().equals("minecraft")).orElse(false)) {
            return original;
        }

        HumanoidModel<?> model = mca$armorModels.get(slot);
        return model == null ? original : model;
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
