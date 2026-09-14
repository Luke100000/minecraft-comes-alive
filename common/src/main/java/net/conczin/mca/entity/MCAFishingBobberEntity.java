package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

public final class MCAFishingBobberEntity extends ThrowableProjectile {
    private static final int MAX_FLYING_TICKS = 40;
    private static final double MAX_OWNER_DISTANCE_SQR = 32.0 * 32.0;
    private static final EntityDataAccessor<Boolean> DATA_BITING =
            SynchedEntityData.defineId(MCAFishingBobberEntity.class, EntityDataSerializers.BOOLEAN);

    private final RandomSource synchronizedRandom = RandomSource.create();
    private boolean bobbing;
    private int flyingTicks;
    private int nibble;
    private int timeUntilLured;
    private int timeUntilHooked;
    private float fishAngle;

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

    public boolean isBiting() {
        return entityData.get(DATA_BITING);
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
        shoot(direction.x, direction.y + horizontalDistance * 0.10, direction.z, 0.6F, 0.1F);
    }

    @Override
    public void tick() {
        synchronizedRandom.setSeed(getUUID().getLeastSignificantBits() ^ level().getGameTime());
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
            if (!level().isClientSide) {
                catchingFish();
            }
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
        double surfaceHeight = fluid.getHeight(level(), pos);
        Vec3 motion = getDeltaMovement();
        double surfaceOffset = getY() + motion.y - pos.getY() - surfaceHeight;
        if (Math.abs(surfaceOffset) < 0.01) {
            surfaceOffset += Math.signum(surfaceOffset) * 0.1;
        }

        setDeltaMovement(
                motion.x * 0.9,
                motion.y - surfaceOffset * random.nextFloat() * 0.2,
                motion.z * 0.9
        );

        if (isBiting()) {
            setDeltaMovement(getDeltaMovement().add(
                    0.0,
                    -0.1 * synchronizedRandom.nextFloat() * synchronizedRandom.nextFloat(),
                    0.0
            ));
        }
    }

    private void catchingFish() {
        ServerLevel world = (ServerLevel) level();

        if (nibble > 0) {
            nibble--;
            if (nibble <= 0) {
                timeUntilLured = 0;
                timeUntilHooked = 0;
                entityData.set(DATA_BITING, false);
            }
            return;
        }

        if (timeUntilHooked > 0) {
            timeUntilHooked--;
            if (timeUntilHooked > 0) {
                fishAngle += (float) random.triangle(0.0, 9.188);
                float angle = fishAngle * Mth.DEG_TO_RAD;
                float sin = Mth.sin(angle);
                float cos = Mth.cos(angle);
                double x = getX() + sin * timeUntilHooked * 0.1F;
                double y = Mth.floor(getY()) + 1.0F;
                double z = getZ() + cos * timeUntilHooked * 0.1F;

                if (world.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
                    if (random.nextFloat() < 0.15F) {
                        world.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1F, z, 1, sin, 0.1, cos, 0.0);
                    }
                    float wakeX = sin * 0.04F;
                    float wakeZ = cos * 0.04F;
                    world.sendParticles(ParticleTypes.FISHING, x, y, z, 0, wakeZ, 0.01, -wakeX, 1.0);
                    world.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -wakeZ, 0.01, wakeX, 1.0);
                }
            } else {
                playSound(
                        SoundEvents.FISHING_BOBBER_SPLASH,
                        0.25F,
                        1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F
                );
                double y = getY() + 0.5;
                int count = (int) (1.0F + getBbWidth() * 20.0F);
                world.sendParticles(ParticleTypes.BUBBLE, getX(), y, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2F);
                world.sendParticles(ParticleTypes.FISHING, getX(), y, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2F);
                nibble = Mth.nextInt(random, 20, 40);
                entityData.set(DATA_BITING, true);
            }
            return;
        }

        if (timeUntilLured > 0) {
            timeUntilLured--;
            float splashChance = 0.15F;
            if (timeUntilLured < 20) {
                splashChance += (20 - timeUntilLured) * 0.05F;
            } else if (timeUntilLured < 40) {
                splashChance += (40 - timeUntilLured) * 0.02F;
            } else if (timeUntilLured < 60) {
                splashChance += (60 - timeUntilLured) * 0.01F;
            }

            if (random.nextFloat() < splashChance) {
                float angle = Mth.nextFloat(random, 0.0F, 360.0F) * Mth.DEG_TO_RAD;
                float distance = Mth.nextFloat(random, 25.0F, 60.0F);
                double x = getX() + Mth.sin(angle) * distance * 0.1;
                double y = Mth.floor(getY()) + 1.0F;
                double z = getZ() + Mth.cos(angle) * distance * 0.1F;
                if (world.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
                    world.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + random.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
                }
            }

            if (timeUntilLured <= 0) {
                fishAngle = Mth.nextFloat(random, 0.0F, 360.0F);
                timeUntilHooked = Mth.nextInt(random, 20, 80);
            }
            return;
        }

        timeUntilLured = Mth.nextInt(random, 100, 600);
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
        builder.define(DATA_BITING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_BITING.equals(key) && isBiting()) {
            setDeltaMovement(
                    getDeltaMovement().x,
                    -0.4F * Mth.nextFloat(synchronizedRandom, 0.6F, 1.0F),
                    getDeltaMovement().z
            );
        }
        super.onSyncedDataUpdated(key);
    }
}
