package net.conczin.mca.entity;

import net.conczin.mca.MCA;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

public final class MCAFishingBobberEntity extends Projectile {
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
    private int lureSpeed;
    private float fishAngle;

    public MCAFishingBobberEntity(EntityType<? extends MCAFishingBobberEntity> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public static MCAFishingBobberEntity cast(ServerLevel world, VillagerEntityMCA owner, BlockPos targetWater) {
        MCAFishingBobberEntity bobber = new MCAFishingBobberEntity(EntitiesMCA.FISHING_BOBBER, world);
        bobber.setOwner(owner);
        bobber.lureSpeed = Math.max(0, (int) (EnchantmentHelper.getFishingTimeReduction(
                world,
                owner.getItemInHand(owner.getDominantHand()),
                owner
        ) * 20.0F));
        bobber.launchAt(owner, targetWater);
        world.addFreshEntity(bobber);
        MCA.LOGGER.info(
                "[MCA Fishing Debug] cast bobber={} owner={} target={} lureSpeed={} pos={} motion={}",
                bobber.getId(), owner.getUUID(), targetWater, bobber.lureSpeed, bobber.position(), bobber.getDeltaMovement()
        );
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
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] discard-invalid-owner bobber={} owner={} job={} held={} distanceSqr={}",
                    getId(), owner == null ? null : owner.getUUID(),
                    owner == null ? null : owner.getVillagerBrain().getCurrentJob(),
                    owner == null ? null : owner.getItemInHand(owner.getDominantHand()),
                    owner == null ? null : distanceToSqr(owner)
            );
            discard();
            return;
        }

        super.tick();
        if (isRemoved()) {
            return;
        }

        BlockPos pos = blockPosition();
        FluidState fluid = level().getFluidState(pos);
        float waterHeight = fluid.is(FluidTags.WATER) ? fluid.getHeight(level(), pos) : 0.0F;
        boolean inWater = waterHeight > 0.0F;

        if (!bobbing) {
            if (inWater) {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 0.2, 0.3));
                bobbing = true;
                flyingTicks = 0;
                if (!level().isClientSide) {
                    MCA.LOGGER.info(
                            "[MCA Fishing Debug] bobbing-start bobber={} tick={} pos={} motion={}",
                            getId(), tickCount, position(), getDeltaMovement()
                    );
                }
                return;
            }

            hitTargetOrDeflectSelf(ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity));
            if (isRemoved()) {
                return;
            }

            if (++flyingTicks > MAX_FLYING_TICKS) {
                if (!level().isClientSide) {
                    MCA.LOGGER.info(
                            "[MCA Fishing Debug] discard-flying-timeout bobber={} tick={} pos={} motion={}",
                            getId(), tickCount, position(), getDeltaMovement()
                    );
                }
                discard();
                return;
            }
        } else {
            stabilizeBobbing(waterHeight, inWater);
            if (inWater && !level().isClientSide) {
                catchingFish();
            }
        }

        if (!inWater) {
            setDeltaMovement(getDeltaMovement().add(0.0, -0.03, 0.0));
        }

        move(MoverType.SELF, getDeltaMovement());
        updateRotation();
        if (!bobbing && (onGround() || horizontalCollision)) {
            setDeltaMovement(Vec3.ZERO);
        }
        setDeltaMovement(getDeltaMovement().scale(0.92));
        reapplyPosition();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 64.0 * 64.0;
    }

    private boolean canRemain(@Nullable VillagerEntityMCA owner) {
        return owner != null
                && owner.isAlive()
                && owner.getVillagerBrain().getCurrentJob() == Chore.FISH
                && owner.getItemInHand(owner.getDominantHand()).getItem() instanceof FishingRodItem
                && distanceToSqr(owner) <= MAX_OWNER_DISTANCE_SQR;
    }

    private void stabilizeBobbing(float waterHeight, boolean inWater) {
        BlockPos pos = blockPosition();
        Vec3 motion = getDeltaMovement();
        double surfaceOffset = getY() + motion.y - pos.getY() - waterHeight;
        if (Math.abs(surfaceOffset) < 0.01) {
            surfaceOffset += Math.signum(surfaceOffset) * 0.1;
        }

        setDeltaMovement(
                motion.x * 0.9,
                motion.y - surfaceOffset * random.nextFloat() * 0.2,
                motion.z * 0.9
        );

        if (inWater && isBiting()) {
            setDeltaMovement(getDeltaMovement().add(
                    0.0,
                    -0.1 * synchronizedRandom.nextFloat() * synchronizedRandom.nextFloat(),
                    0.0
            ));
        }
    }

    private void catchingFish() {
        ServerLevel world = (ServerLevel) level();
        int countdownStep = 1;
        if (random.nextFloat() < 0.25F && world.isRainingAt(blockPosition().above())) {
            countdownStep++;
        }

        if (nibble > 0) {
            nibble--;
            if (nibble <= 0) {
                timeUntilLured = 0;
                timeUntilHooked = 0;
                entityData.set(DATA_BITING, false);
                MCA.LOGGER.info(
                        "[MCA Fishing Debug] bite-ended bobber={} tick={} pos={} motion={}",
                        getId(), tickCount, position(), getDeltaMovement()
                );
            }
            return;
        }

        if (timeUntilHooked > 0) {
            timeUntilHooked -= countdownStep;
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
                MCA.LOGGER.info(
                        "[MCA Fishing Debug] bite-started bobber={} tick={} nibble={} pos={} motion={}",
                        getId(), tickCount, nibble, position(), getDeltaMovement()
                );
            }
            return;
        }

        if (timeUntilLured > 0) {
            timeUntilLured -= countdownStep;
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
                MCA.LOGGER.info(
                        "[MCA Fishing Debug] approach-started bobber={} tick={} approachTicks={} fishAngle={}",
                        getId(), tickCount, timeUntilHooked, fishAngle
                );
            }
            return;
        }

        timeUntilLured = Mth.nextInt(random, 100, 600);
        timeUntilLured -= lureSpeed;
        MCA.LOGGER.info(
                "[MCA Fishing Debug] lure-wait-started bobber={} tick={} waitTicks={} lureSpeed={}",
                getId(), tickCount, timeUntilLured, lureSpeed
        );
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide && !level().getFluidState(BlockPos.containing(result.getLocation())).is(FluidTags.WATER)) {
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] discard-block-hit bobber={} block={} pos={} motion={}",
                    getId(), result.getBlockPos(), position(), getDeltaMovement()
            );
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BITING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_BITING.equals(key)) {
            boolean biting = isBiting();
            if (biting) {
                setDeltaMovement(
                        getDeltaMovement().x,
                        -0.4F * Mth.nextFloat(synchronizedRandom, 0.6F, 1.0F),
                        getDeltaMovement().z
                );
            }
            MCA.LOGGER.info(
                    "[MCA Fishing Debug] bite-sync side={} bobber={} tick={} biting={} pos={} motion={}",
                    level().isClientSide ? "client" : "server", getId(), tickCount, biting, position(), getDeltaMovement()
            );
        }
        super.onSyncedDataUpdated(key);
    }
}
