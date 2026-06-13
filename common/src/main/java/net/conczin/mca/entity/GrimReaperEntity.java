package net.conczin.mca.entity;


import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.goal.GrimReaperIdleGoal;
import net.conczin.mca.entity.ai.goal.GrimReaperMeleeGoal;
import net.conczin.mca.entity.ai.goal.GrimReaperRestGoal;
import net.conczin.mca.entity.ai.goal.GrimReaperTargetGoal;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.registry.SoundsMCA;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CEnumParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.conczin.mca.util.network.datasync.CTrackedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GrimReaperEntity extends PathfinderMob implements CTrackedEntity<GrimReaperEntity> {
    public static final CEnumParameter<ReaperAttackState> ATTACK_STAGE = CParameter.create("AttackStage", ReaperAttackState.IDLE);

    public static final CDataManager<GrimReaperEntity> DATA = new CDataManager.Builder<>(GrimReaperEntity.class).addAll(ATTACK_STAGE).build();

    private final ServerBossEvent bossInfo = (ServerBossEvent) new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS).setDarkenScreen(true);

    public GrimReaperEntity(EntityType<? extends GrimReaperEntity> type, Level world) {
        super(type, world);

        xpReward = 100;

        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MAX_HEALTH, 300.0F)
                .add(Attributes.MOVEMENT_SPEED, 0.30F)
                .add(Attributes.FLYING_SPEED, 0.30F)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    public CDataManager<GrimReaperEntity> getTypeDataManager() {
        return DATA;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        getTypeDataManager().register(builder);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public EntityType<?> getType() {
        return super.getType();
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new GrimReaperTargetGoal(this));

        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 24, 1.0f));
        this.goalSelector.addGoal(1, new GrimReaperRestGoal(this));
        this.goalSelector.addGoal(2, new GrimReaperMeleeGoal(this));
        this.goalSelector.addGoal(3, new GrimReaperIdleGoal(this, 1));
    }

    @Override
    public MoveControl getMoveControl() {
        return moveControl;
    }

    @Override
    public void checkDespawn() {
        if (level().getDifficulty() == Difficulty.PEACEFUL && shouldDespawnInPeaceful()) {
            discard();
        }
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        FlyingPathNavigation navigator = new FlyingPathNavigation(this, world) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return true;
            }

        };
        navigator.setCanOpenDoors(false);
        navigator.setCanFloat(false);
        return navigator;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        ItemEntity itemEntity = spawnAtLocation(level, ItemsMCA.SCYTHE);
        if (itemEntity != null) {
            itemEntity.setExtendedLifetime();
        }
    }

    public ReaperAttackState getAttackState() {
        return getTrackedValue(ATTACK_STAGE);
    }

    public void setAttackState(ReaperAttackState state) {
        // Only update if needed so that sounds only play once.
        if (getAttackState() == state) {
            return;
        }

        setTrackedValue(ATTACK_STAGE, state);

        switch (state) {
            case PRE -> playSound(SoundsMCA.REAPER_SCYTHE_OUT, 1, 1);
            case POST -> playSound(SoundsMCA.REAPER_SCYTHE_SWING, 1, 1);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // Ignore wall damage, fire and explosion damage
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.IN_FIRE)) {
            // Teleport out of any walls we may end up in
            if (source.is(DamageTypes.IN_WALL)) {
                teleportTo(this.getX(), this.getY() + 3, this.getZ());
            }
            return false;
        }

        Entity entity = source.getDirectEntity();
        Entity attacker = source.getEntity();

        // Ignore damage when blocking, and randomly teleport around
        if (this.getAttackState() == ReaperAttackState.BLOCK && attacker != null) {
            playSound(SoundsMCA.REAPER_BLOCK, 1.0F, 1.0F);
            return false;
        }

        // Teleport next to the player who fired an arrow
        if (entity instanceof Projectile && getAttackState() != ReaperAttackState.REST && attacker != null && random.nextBoolean()) {
            double newX = attacker.getX() + (random.nextFloat() >= 0.50F ? 4 : -4);
            double newZ = attacker.getZ() + (random.nextFloat() >= 0.50F ? 4 : -4);

            teleportTo(newX, attacker.getY(), newZ);
            return false;
        }

        // Randomly portal behind the player who just attacked.
        if (!level().isClientSide && random.nextFloat() >= 0.30F && attacker != null) {
            double deltaX = this.getX() - attacker.getX();
            double deltaZ = this.getZ() - attacker.getZ();
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            double length = Math.max(5.0, distance) / distance * 0.95;

            teleportTo(attacker.getX() - deltaX * length, attacker.getY() + 1.5, attacker.getZ() - deltaZ * length);
        }

        // 25% damage when healing
        if (this.getAttackState() == ReaperAttackState.REST) {
            damage *= 0.25f;
        }

        return super.hurtServer(level, source, damage);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundsMCA.REAPER_IDLE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundsMCA.REAPER_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    public void tick() {
        super.tick();

        // Update boss info
        bossInfo.setProgress(this.getHealth() / this.getMaxHealth());

        if (!Config.getInstance().allowGrimReaper) {
            discard();
        }

        // Prevent flying off into oblivion on death...
        if (this.getHealth() <= 0.0F) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        LivingEntity entityToAttack = this.getTarget();

        // See if our entity to attack has died at any point.
        if (entityToAttack != null && entityToAttack.isDeadOrDying()) {
            this.setTarget(null);
            setAttackState(ReaperAttackState.IDLE);
        }

        // Logic for flying.
        fallDistance = 0;
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        if (level() instanceof ServerLevel) {
            playSound(SoundEvents.ENDERMAN_TELEPORT, 1, 1);
            super.teleportTo(x, y, z);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 1, 1);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossInfo.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossInfo.removePlayer(player);
    }
}
