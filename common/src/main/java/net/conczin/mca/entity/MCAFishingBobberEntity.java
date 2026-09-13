package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class MCAFishingBobberEntity extends ThrowableProjectile {
    private static final int MAX_FLYING_TICKS = 40;
    private static final double MAX_OWNER_DISTANCE_SQR = 32.0 * 32.0;

    private boolean bobbing;
    private int flyingTicks;

    public MCAFishingBobberEntity(EntityType<? extends MCAFishingBobberEntity> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public static MCAFishingBobberEntity cast(ServerLevel world, VillagerEntityMCA owner, BlockPos targetWater) {
        MCAFishingBobberEntity bobber = new MCAFishingBobberEntity(EntitiesMCA.FISHING_BOBBER, world);
        bobber.setOwner(owner);
        bobber.launchAt(owner, targetWater);
        world.addFreshEntity(bobber);
        return bobber;
    }

    @Nullable
    public VillagerEntityMCA getVillagerOwner() {
        return getOwner() instanceof VillagerEntityMCA villager ? villager : null;
    }

    public boolean isBobbing() {
        return bobbing && !isRemoved();
    }

    private void launchAt(VillagerEntityMCA owner, BlockPos targetWater) {
        int handSide = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float bodyYaw = owner.yBodyRot * Mth.DEG_TO_RAD;
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);

        Vec3 origin = owner.getEyePosition().add(
                -cos * handSide * 0.20 - sin * 0.20,
                -0.35,
                -sin * handSide * 0.20 + cos * 0.20
        );
        FluidState fluid = level().getFluidState(targetWater);
        double surfaceY = targetWater.getY() + fluid.getHeight(level(), targetWater);
        Vec3 destination = new Vec3(targetWater.getX() + 0.5, surfaceY, targetWater.getZ() + 0.5);
        Vec3 direction = destination.subtract(origin);
        double horizontalDistance = direction.horizontalDistance();

        setPos(origin.x, origin.y, origin.z);
        shoot(direction.x, direction.y + horizontalDistance * 0.25, direction.z, 0.6F, 0.5F);
    }

    @Override
    public void tick() {
        VillagerEntityMCA owner = getVillagerOwner();
        if (!level().isClientSide && !canRemain(owner)) {
            discard();
            return;
        }

        super.tick();
        if (isRemoved()) {
            return;
        }

        FluidState fluid = level().getFluidState(blockPosition());
        if (fluid.is(FluidTags.WATER)) {
            bobbing = true;
            flyingTicks = 0;
            stabilizeOnWater(fluid);
        } else if (!bobbing && ++flyingTicks > MAX_FLYING_TICKS) {
            discard();
        }
    }

    private boolean canRemain(@Nullable VillagerEntityMCA owner) {
        return owner != null
                && owner.isAlive()
                && owner.getVillagerBrain().getCurrentJob() == Chore.FISH
                && owner.getItemInHand(owner.getDominantHand()).getItem() instanceof FishingRodItem
                && distanceToSqr(owner) <= MAX_OWNER_DISTANCE_SQR;
    }

    private void stabilizeOnWater(FluidState fluid) {
        BlockPos pos = blockPosition();
        double surfaceY = pos.getY() + fluid.getHeight(level(), pos);
        Vec3 motion = getDeltaMovement();
        double offset = getY() - surfaceY;
        setDeltaMovement(motion.x * 0.9, motion.y - offset * 0.2, motion.z * 0.9);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide && !level().getFluidState(BlockPos.containing(result.getLocation())).is(FluidTags.WATER)) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }
}
