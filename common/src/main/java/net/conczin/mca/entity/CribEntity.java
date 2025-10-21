package net.conczin.mca.entity;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.item.CribItem;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.util.network.datasync.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public class CribEntity extends Entity implements CTrackedEntity<CribEntity> {
    private static final CDataParameter<ItemStack> BABY = CParameter.create("BabyItem", ItemStack.EMPTY);
    private static final CEnumParameter<CribWoodType> WOOD = CParameter.create("Wood", CribWoodType.OAK);
    private static final CEnumParameter<DyeColor> COLOR = CParameter.create("Color", DyeColor.RED);

    private static final CDataManager<CribEntity> DATA = createTrackedData().build();

    VillagerEntityMCA infant;

    public CribEntity(EntityType<? extends CribEntity> type, Level world) {
        super(type, world);
    }

    static CDataManager.Builder<CribEntity> createTrackedData() {
        return new CDataManager.Builder<>(CribEntity.class).addAll(BABY, WOOD, COLOR);
    }

    public CribWoodType getWoodType() {
        return getTrackedValue(WOOD);
    }

    public void setWoodType(CribWoodType wood) {
        setTrackedValue(WOOD, wood);
    }

    public DyeColor getColor() {
        return getTrackedValue(COLOR);
    }

    public void setColor(DyeColor color) {
        setTrackedValue(COLOR, color);
    }

    public ItemStack getBabyItem() {
        return getTrackedValue(BABY);
    }

    private boolean isOccupied() {
        return !getTrackedValue(BABY).equals(ItemStack.EMPTY) || infant != null;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        getTypeDataManager().register(builder);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("Baby")) {
            setTrackedValue(BABY, ItemStack.parseOptional(level().registryAccess(), nbt.getCompound("Baby")));
            if (getTrackedValue(BABY).equals(ItemStack.EMPTY)) {
                MCA.LOGGER.warn("Issue deserializing baby item from crib NBT!");
            }
        }
        if (nbt.contains("Wood")) {
            setTrackedValue(WOOD, CribWoodType.values()[nbt.getInt("Wood")]);
        }
        if (nbt.contains("Color")) {
            setTrackedValue(COLOR, DyeColor.values()[nbt.getInt("Color")]);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (!getTrackedValue(BABY).equals(ItemStack.EMPTY)) {
            Tag babyCompound = getTrackedValue(BABY).save(level().registryAccess(), new CompoundTag());
            nbt.put("Baby", babyCompound);
        }

        nbt.putInt("Wood", Arrays.asList(CribWoodType.values()).indexOf(getTrackedValue(WOOD)));
        nbt.putInt("Color", Arrays.asList(DyeColor.values()).indexOf(getTrackedValue(COLOR)));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0.0, 0.1, 0.0);
    }

    private void setEntityOccupant(VillagerEntityMCA occupant) {
        infant = occupant;
        infant.setInvulnerable(true);
    }

    private void unsetEntityOccupant() {
        if (infant != null) {
            infant.setInvulnerable(false);
            infant = null;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // Refresh riding data
        if (isVehicle() && getFirstPassenger() instanceof VillagerEntityMCA && infant == null)
            setEntityOccupant((VillagerEntityMCA) getFirstPassenger());

        // Removing occupant is first priority over adding occupant, so that multiple occupants dont exist.
        if (infant != null && infant.getVehicle() == this) {
            infant.startRiding(player, true);
            unsetEntityOccupant();
        } else if (!getTrackedValue(BABY).equals(ItemStack.EMPTY)) {
            player.getInventory().add(getTrackedValue(BABY));
            setTrackedValue(BABY, ItemStack.EMPTY);
        } else if (player.getInventory().getSelected() != ItemStack.EMPTY && player.getInventory().getSelected().getItem() instanceof BabyItem) {
            setTrackedValue(BABY, player.getInventory().getSelected());
            player.getInventory().removeItem(getTrackedValue(BABY));
        } else if (player.getFirstPassenger() != null && player.getFirstPassenger() instanceof VillagerEntityMCA rider) {
            if (rider.getAgeState() == AgeState.BABY) {
                setEntityOccupant(rider);
                infant.startRiding(this, true);
            }
        } else return InteractionResult.PASS;

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return attacker instanceof Player && !this.level().mayInteract((Player) attacker, this.blockPosition());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.onGround()) {
            this.setDeltaMovement(Vec3.ZERO);
        } else if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());

        if (getTrackedValue(BABY) != ItemStack.EMPTY && getTrackedValue(BABY).getItem() instanceof BabyItem) {
            getTrackedValue(BABY).getItem().inventoryTick(getTrackedValue(BABY), level(), this, 0, false);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }

        if (isOccupied()) return false;

        if (this.isInvulnerableTo(source)) {
            return false;
        }

        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)) {
            this.kill();
            return false;
        }

        boolean bl = source.getDirectEntity() instanceof AbstractArrow;
        boolean bl2 = bl && ((AbstractArrow) source.getDirectEntity()).getPierceLevel() > 0;
        boolean bl3 = "player".equals(source.getMsgId());

        if (!bl3 && !bl) {
            return false;
        }

        if (source.getEntity() instanceof Player && !((Player) source.getEntity()).getAbilities().mayBuild) {
            return false;
        }

        if (source.isCreativePlayer()) {
            this.playBreakSound();
            this.spawnBreakParticles();
            this.kill();
            return bl2;
        } else {
            CribItem matchingType = ItemsMCA.CRIBS.stream().filter(c -> c.getColor() == getTrackedValue(COLOR) && c.getWood() == getTrackedValue(WOOD)).findFirst().get();
            Block.popResource(this.level(), this.blockPosition(), new ItemStack(matchingType));
            this.spawnBreakParticles();
            this.kill();
        }

        return true;
    }

    private void spawnBreakParticles() {
        if (this.level() instanceof ServerLevel) {
            ((ServerLevel) this.level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                    this.getX(), this.getY(0.6666666666666666), this.getZ(), 10, this.getBbWidth() / 4.0f, this.getBbHeight() / 4.0f, this.getBbWidth() / 4.0f, 0.05);
        }
    }

    private void playBreakSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0f, 1.0f);
    }

    @Override
    public CDataManager<CribEntity> getTypeDataManager() {
        return DATA;
    }
}
