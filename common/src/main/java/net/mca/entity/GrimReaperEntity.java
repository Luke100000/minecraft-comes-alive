package net.mca.entity;


import net.mca.Config;
import net.mca.SoundsMCA;
import net.mca.entity.ai.goal.GrimReaperIdleGoal;
import net.mca.entity.ai.goal.GrimReaperMeleeGoal;
import net.mca.entity.ai.goal.GrimReaperRestGoal;
import net.mca.entity.ai.goal.GrimReaperTargetGoal;
import net.mca.item.ItemsMCA;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CEnumParameter;
import net.mca.util.network.datasync.CParameter;
import net.mca.util.network.datasync.CTrackedEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class GrimReaperEntity extends PathAwareEntity implements CTrackedEntity<GrimReaperEntity> {
    public static final CEnumParameter<ReaperAttackState> ATTACK_STAGE = CParameter.create("attackStage", ReaperAttackState.IDLE);

    public static final CDataManager<GrimReaperEntity> DATA = new CDataManager.Builder<>(GrimReaperEntity.class).addAll(ATTACK_STAGE).build();

    private final ServerBossBar bossInfo = (ServerBossBar) new ServerBossBar(getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.PROGRESS).setDarkenSky(true);

    public GrimReaperEntity(EntityType<? extends GrimReaperEntity> type, World world) {
        super(type, world);

        experiencePoints = 100;

        this.moveControl = new FlightMoveControl(this, 10, false);

        getTypeDataManager().register(this);
    }

    @Override
    public CDataManager<GrimReaperEntity> getTypeDataManager() {
        return DATA;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0D)
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 300.0F)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30F)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.30F)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0D);
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public SoundCategory getSoundCategory() {
        return SoundCategory.HOSTILE;
    }

    @Override
    public EntityGroup getGroup() {
        return EntityGroup.UNDEAD;
    }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new GrimReaperTargetGoal(this));

        this.goalSelector.add(0, new LookAtEntityGoal(this, PlayerEntity.class, 24, 1.0f));
        this.goalSelector.add(1, new GrimReaperRestGoal(this));
        this.goalSelector.add(2, new GrimReaperMeleeGoal(this));
        this.goalSelector.add(3, new GrimReaperIdleGoal(this, 1));
    }

    @Override
    public MoveControl getMoveControl() {
        return moveControl;
    }

    @Override
    public void checkDespawn() {
        if (getWorld().getDifficulty() == Difficulty.PEACEFUL && isDisallowedInPeaceful()) {
            discard();
        }
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return true;
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation navigator = new BirdNavigation(this, world) {
            @Override
            public boolean isValidPosition(BlockPos pos) {
                return true;
            }

        };
        navigator.setCanPathThroughDoors(false);
        navigator.setCanSwim(false);
        navigator.setCanEnterOpenDoors(true);
        return navigator;
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingLvl, boolean hitByPlayer) {
        super.dropEquipment(source, lootingLvl, hitByPlayer);
        ItemEntity itemEntity = dropItem(ItemsMCA.SCYTHE.get());
        if (itemEntity != null) {
            itemEntity.setCovetedItem();
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
            case PRE -> playSound(SoundsMCA.REAPER_SCYTHE_OUT.get(), 1, 1);
            case POST -> playSound(SoundsMCA.REAPER_SCYTHE_SWING.get(), 1, 1);
        }
    }

    @Override
    public boolean damage(DamageSource source, float damage) {
        // Ignore wall damage, fire and explosion damage
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.IN_FIRE)) {
            // Teleport out of any walls we may end up in
            if (source.isOf(DamageTypes.IN_WALL)) {
                requestTeleport(this.getX(), this.getY() + 3, this.getZ());
            }
            return false;
        }

        Entity entity = source.getSource();
        Entity attacker = source.getAttacker();

        // Ignore damage when blocking, and randomly teleport around
        if (this.getAttackState() == ReaperAttackState.BLOCK && attacker != null) {
            playSound(SoundsMCA.REAPER_BLOCK.get(), 1.0F, 1.0F);
            return false;
        }

        // Teleport next to the player who fired an arrow
        if (entity instanceof ProjectileEntity && getAttackState() != ReaperAttackState.REST && attacker != null && random.nextBoolean()) {
            double newX = attacker.getX() + (random.nextFloat() >= 0.50F ? 4 : -4);
            double newZ = attacker.getZ() + (random.nextFloat() >= 0.50F ? 4 : -4);

            requestTeleport(newX, attacker.getY(), newZ);
            return false;
        }

        // Randomly portal behind the player who just attacked.
        if (!getWorld().isClient && random.nextFloat() >= 0.30F && attacker != null) {
            double deltaX = this.getX() - attacker.getX();
            double deltaZ = this.getZ() - attacker.getZ();
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            double length = Math.max(5.0, distance) / distance * 0.95;

            requestTeleport(attacker.getX() - deltaX * length, attacker.getY() + 1.5, attacker.getZ() - deltaZ * length);
        }

        // 25% damage when healing
        if (this.getAttackState() == ReaperAttackState.REST) {
            damage *= 0.25f;
        }

        return super.damage(source, damage);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundsMCA.REAPER_IDLE.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundsMCA.REAPER_DEATH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WITHER_HURT;
    }

    @Override
    public void tick() {
        super.tick();

        //update bossinfo
        bossInfo.setPercent(this.getHealth() / this.getMaxHealth());

        if (!Config.getInstance().allowGrimReaper) {
            discard();
        }

        // Prevent flying off into oblivion on death...
        if (this.getHealth() <= 0.0F) {
            setVelocity(Vec3d.ZERO);
            return;
        }

        LivingEntity entityToAttack = this.getTarget();

        // See if our entity to attack has died at any point.
        if (entityToAttack != null && entityToAttack.isDead()) {
            this.setTarget(null);
            setAttackState(ReaperAttackState.IDLE);
        }

        // Logic for flying.
        fallDistance = 0;
    }

    @Override
    public void requestTeleport(double x, double y, double z) {
        if (getWorld() instanceof ServerWorld) {
            playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1, 1);
            super.requestTeleport(x, y, z);
            playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1, 1);
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossInfo.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossInfo.removePlayer(player);
    }
}
