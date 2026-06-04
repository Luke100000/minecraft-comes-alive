package net.conczin.mca.mixin.client;

import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinHumanoidArmorLayer {

    @Unique
    private final ArmorModelSet<PlayerArmorExtendedModel> mca$armorModels = PlayerArmorExtendedModel.createArmorModels();

    @Unique
    private static Optional<UUID> mca$getPlayerUuid(HumanoidRenderState renderState) {
        if (renderState instanceof AvatarRenderState avatarRenderState) {
            if (Minecraft.getInstance().level == null) {
                return Optional.empty();
            }

            Entity entity = Minecraft.getInstance().level.getEntity(avatarRenderState.id);
            return entity instanceof Avatar avatar ? Optional.of(avatar.getUUID()) : Optional.empty();
        }

        return Optional.empty();
    }

    @Inject(method = "getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;", at = @At("HEAD"), cancellable = true)
    private void mca$injectGetArmorModel(HumanoidRenderState renderState, EquipmentSlot slot, CallbackInfoReturnable<HumanoidModel<?>> cir) {
        mca$getPlayerUuid(renderState)
                .filter(MCAClient::useGeneticsRenderer)
                .ifPresent(uuid -> cir.setReturnValue(this.mca$armorModels.get(slot)));
    }
}
