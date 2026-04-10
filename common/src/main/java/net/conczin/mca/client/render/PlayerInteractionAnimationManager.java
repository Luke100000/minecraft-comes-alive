package net.conczin.mca.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerInteractionAnimationManager {
    private static final Map<UUID, ActiveAnimation> ACTIVE_ANIMATIONS = new HashMap<>();

    private PlayerInteractionAnimationManager() {
    }

    public static void start(UUID source, UUID target, String action, int durationTicks, float strength) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        ActiveAnimation animation = new ActiveAnimation(action, client.level.getGameTime() + Math.max(1, durationTicks), Math.max(1, durationTicks), Mth.clamp(strength, 0.85F, 2.25F));
        ACTIVE_ANIMATIONS.put(source, animation);
        ACTIVE_ANIMATIONS.put(target, animation);
    }

    public static void applyToHumanoidModel(UUID playerUuid, HumanoidModel<?> model, float ageInTicks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        ActiveAnimation animation = ACTIVE_ANIMATIONS.get(playerUuid);
        if (animation == null) {
            return;
        }

        long now = client.level.getGameTime();
        if (animation.endTick <= now) {
            ACTIVE_ANIMATIONS.remove(playerUuid);
            return;
        }

        float remaining = animation.endTick - now;
        float progress = 1.0F - Mth.clamp(remaining / animation.durationTicks, 0.0F, 1.0F);
        float intensity = 0.35F + 0.65F * progress;
        float strength = animation.strength;

        if ("hug".equals(animation.action)) {
            float sway = Mth.sin(ageInTicks * 0.45F) * 0.08F;
            model.body.xRot += 0.18F * intensity;
            model.head.xRot = Mth.lerp(intensity, model.head.xRot, -0.08F);
            model.rightArm.xRot = Mth.lerp(intensity, model.rightArm.xRot, -1.20F + sway);
            model.leftArm.xRot = Mth.lerp(intensity, model.leftArm.xRot, -1.20F - sway);
            model.rightArm.yRot = Mth.lerp(intensity, model.rightArm.yRot, -0.55F);
            model.leftArm.yRot = Mth.lerp(intensity, model.leftArm.yRot, 0.55F);
        } else if ("kiss".equals(animation.action)) {
            float sway = Mth.sin(ageInTicks * (0.35F + strength * 0.08F)) * 0.10F * intensity * strength;
            float bounce = Mth.abs(Mth.sin(progress * Mth.PI * (2.25F + strength))) * (0.85F + 0.65F * strength) * intensity;
            model.body.xRot += (0.24F + 0.08F * strength) * intensity + bounce * 0.04F;
            model.body.y -= bounce;
            model.head.xRot = Mth.lerp(intensity, model.head.xRot, -0.14F - 0.05F * strength);
            model.head.yRot += sway;
            model.head.y -= bounce * 1.10F;
            model.rightArm.xRot = Mth.lerp(intensity, model.rightArm.xRot, -0.45F - 0.10F * strength);
            model.leftArm.xRot = Mth.lerp(intensity, model.leftArm.xRot, -0.45F - 0.10F * strength);
            model.rightArm.yRot = Mth.lerp(intensity, model.rightArm.yRot, -0.15F - 0.08F * strength);
            model.leftArm.yRot = Mth.lerp(intensity, model.leftArm.yRot, 0.15F + 0.08F * strength);
            model.rightArm.y -= bounce * 0.40F;
            model.leftArm.y -= bounce * 0.40F;
        }

    }

    private record ActiveAnimation(String action, long endTick, int durationTicks, float strength) {
    }
}
