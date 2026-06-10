package net.conczin.mca.client.render;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBodyOverlayPhysics {
    private static final float BOUNCINESS = 0.27F;
    private static final float DAMPING = 0.375F;
    private static final float SIZE_BOUNCE_SCALE = 0.25F;
    private static final float Y_OFFSET_SCALE = 0.75F;
    private static final float Z_OFFSET_SCALE = -0.25F;
    private static final double EXPIRY_TICKS = 200.0D;
    private static final ConcurrentHashMap<UUID, PhysicsState> PLAYERS = new ConcurrentHashMap<>();

    private PlayerBodyOverlayPhysics() {
    }

    public static void tick(Minecraft client) {
        if (client.level == null || !Config.getInstance().enableBoobs || !MCAClient.isPlayerRendererAllowed()) {
            PLAYERS.clear();
            return;
        }

        Iterator<Map.Entry<UUID, PhysicsState>> iterator = PLAYERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PhysicsState> entry = iterator.next();
            Player player = client.level.getPlayerByUUID(entry.getKey());
            if (player == null || !shouldTrack(player)) {
                iterator.remove();
                continue;
            }

            PhysicsState state = entry.getValue();
            state.update(player);
            if (state.expired()) {
                iterator.remove();
            }
        }
    }

    public static void remove(UUID uuid) {
        PLAYERS.remove(uuid);
    }

    public static void clear() {
        PLAYERS.clear();
    }

    public static void applyTo(CommonVillagerModel<?> model, UUID uuid, boolean crouching, float partialTick) {
        float scaledBreastSize = model.getBreastSize() * model.getDimensions().getBreasts();
        boolean renderBreasts = model.getBreastPart().visible && model.getBodyPart().visible && scaledBreastSize > 0.0F;
        if (!renderBreasts || !Config.getInstance().enableBoobs) {
            remove(uuid);
            return;
        }

        PhysicsState state = PLAYERS.computeIfAbsent(uuid, ignored -> new PhysicsState());
        double renderTime = renderTime();
        state.markRendered(renderTime);
        Vec2 offset = state.active() ? state.interpolate(renderTime, partialTick) : Vec2.ZERO;
        float animatedBreastSize = Mth.clamp(scaledBreastSize + offset.x * SIZE_BOUNCE_SCALE, 0.0F, 1.25F);
        float breastScaleX = animatedBreastSize * 0.2F + 1.05F;
        float breastScaleY = animatedBreastSize * 0.75F + 0.75F;
        float breastScaleZ = animatedBreastSize * 0.75F + 0.75F;
        float crouchY = crouching ? 3.0F : 0.0F;
        float crouchZ = crouching ? 1.5F : 0.0F;
        float y = 5.0F - (float) Math.sqrt(model.getBreastSize()) * 2.5F + crouchY + offset.y * Y_OFFSET_SCALE;
        float z = -1.5F + model.getBreastSize() * 0.25F + crouchZ + offset.x * Z_OFFSET_SCALE;

        for (ModelPart part : model.getBreastParts()) {
            if (!part.visible) {
                continue;
            }

            part.xScale = breastScaleX;
            part.yScale = breastScaleY;
            part.zScale = breastScaleZ;
            part.xRot = (float) Math.PI * 0.3F + model.getBodyPart().xRot;
            part.setPos(0.25F, y, z);
        }
    }

    private static boolean shouldTrack(Player player) {
        VillagerLike<?> villager = MCAClient.playerData.get(player.getUUID());
        return villager != null
               && villager.getPlayerModel() != VillagerLike.PlayerModel.VANILLA
               && villager.getGenetics().getBreastSize() * villager.getVillagerDimensions().getBreasts() > 0.0F;
    }

    private static double renderTime() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? 0.0D : client.level.getGameTime();
    }

    private static final class PhysicsState {
        private boolean previousCrouching;
        private Vec3 previousPosition;
        private Vec2 previousVelocity;
        private Vec2 previousAcceleration = Vec2.ZERO;
        private Vec2 breastPosition = Vec2.ZERO;
        private Vec2 breastVelocity = Vec2.ZERO;
        private Vec2 breastAcceleration = Vec2.ZERO;
        private Vec2 interpolatedPosition;
        private Vec2 interpolatedVelocity;
        private Double lastVisibleTime;
        private Double lastRenderTime;
        private double lastPartialTick;
        private boolean active;
        private boolean expired;

        void update(Player player) {
            if (lastVisibleTime != null && player.level().getGameTime() - lastVisibleTime > EXPIRY_TICKS) {
                expired = true;
                return;
            }

            if (previousPosition == null) {
                previousCrouching = player.isCrouching();
                previousPosition = player.position();
                return;
            }

            Vec3 delta = player.position().subtract(previousPosition);
            float yawRadians = player.yBodyRot * ((float) Math.PI / 180.0F);
            Vec2 velocity = new Vec2(
                    Mth.sqrt((float) (Math.abs(delta.x * Mth.sin(yawRadians)) + Math.abs(delta.z * Mth.cos(yawRadians)))),
                    (float) delta.y
            );

            if (player.isCrouching() != previousCrouching) {
                float direction = previousCrouching ? -1.0F : 1.0F;
                velocity = velocity.add(new Vec2(direction * 0.3F, -direction * 0.2F));
            }

            previousCrouching = player.isCrouching();
            if (previousVelocity == null) {
                previousVelocity = velocity;
                previousPosition = player.position();
                return;
            }

            float stiffness = 1.0F / (BOUNCINESS * 10.0F);
            Vec2 acceleration = velocity.add(previousVelocity.negated());
            previousVelocity = velocity;
            previousPosition = player.position();

            breastAcceleration = breastPosition.scale(-stiffness).add(breastVelocity.scale(DAMPING).negated());
            if (breastAcceleration.length() < 0.002F) {
                breastPosition = Vec2.ZERO;
                breastVelocity = Vec2.ZERO;
            } else {
                breastVelocity = breastVelocity.add(breastAcceleration);
                breastPosition = breastPosition.add(breastVelocity);
            }

            breastPosition = breastPosition.add(acceleration.add(previousAcceleration.negated()).scale(1.0F / stiffness).negated());
            breastPosition = new Vec2(Mth.clamp(breastPosition.x, -1.0F, 1.0F), Mth.clamp(breastPosition.y, -1.0F, 1.0F));
            previousAcceleration = acceleration;
            active = true;
        }

        Vec2 interpolate(double renderTime, float partialTick) {
            if (lastRenderTime == null) {
                lastRenderTime = renderTime;
                lastPartialTick = partialTick;
            }

            if (interpolatedPosition == null || lastRenderTime.doubleValue() != renderTime) {
                interpolatedPosition = breastPosition;
                interpolatedVelocity = breastVelocity;
                lastPartialTick = partialTick;
            }

            lastRenderTime = renderTime;
            float delta = (float) (partialTick - lastPartialTick);
            interpolatedPosition = interpolatedPosition.add(interpolatedVelocity.scale(delta));
            interpolatedVelocity = interpolatedVelocity.add(breastAcceleration.scale(delta));
            lastPartialTick = partialTick;
            return interpolatedPosition;
        }

        void markRendered(double renderTime) {
            lastVisibleTime = renderTime;
        }

        boolean active() {
            return active;
        }

        boolean expired() {
            return expired;
        }
    }
}
