package net.mca.entity;

import net.mca.Config;
import net.mca.MCA;
import net.mca.TagsMCA;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Relationship;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.Gender;
import net.mca.entity.interaction.ZombieCommandHandler;
import net.mca.util.InventoryUtils;
import net.mca.util.network.datasync.CDataManager;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZombieVillagerEntityMCA extends ZombieVillagerEntity implements VillagerLike<ZombieVillagerEntityMCA>, CompassionateEntity<Relationship<ZombieVillagerEntityMCA>> {

    private static final CDataManager<ZombieVillagerEntityMCA> DATA = VillagerEntityMCA.createTrackedData(ZombieVillagerEntityMCA.class).build();

    private final VillagerBrain<ZombieVillagerEntityMCA> mcaBrain = new VillagerBrain<>(this);

    private final Genetics genetics = new Genetics(this);
    private final Traits traits = new Traits(this);

    private final Relationship<ZombieVillagerEntityMCA> relations = new Relationship<>(this);

    private final ZombieCommandHandler interactions = new ZombieCommandHandler(this);
    private final UpdatableInventory inventory = new UpdatableInventory(27);

    private int burned;

    public ZombieVillagerEntityMCA(EntityType<? extends ZombieVillagerEntity> type, World world, Gender gender) {
        super(type, world);
        genetics.setGender(gender);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();

        getTypeDataManager().register(this);
    }

    @Override
    public CDataManager<ZombieVillagerEntityMCA> getTypeDataManager() {
        return DATA;
    }

    @Override
    public Genetics getGenetics() {
        return genetics;
    }

    @Override
    public Traits getTraits() {
        return traits;
    }

    @Override
    public VillagerBrain<?> getVillagerBrain() {
        return mcaBrain;
    }

    @Override
    public ZombieCommandHandler getInteractions() {
        return interactions;
    }

    @Override
    public boolean isBurned() {
        return burned > 0;
    }

    @Override
    public Relationship<ZombieVillagerEntityMCA> getRelationships() {
        return relations;
    }

    @Override
    public float getInfectionProgress() {
        return 1.0f;
    }

    @Override
    public void setInfectionProgress(float progress) {
        // noop
    }

    @Override
    @Nullable
    public final Text getCustomName() {
        String value = getTrackedValue(VILLAGER_NAME);
        return MCA.isBlankString(value) ? null : Text.literal(value).formatted(Formatting.RED);
    }

    @Override
    public double getHeightOffset() {
        return -0.35 + 0.6 * (1.0 - getRawVerticalScaleFactor());
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {

        if (pose == EntityPose.SLEEPING) {
            return SLEEPING_DIMENSIONS;
        }

        boolean useRawDimensions = getAgeState() == AgeState.TEEN || getAgeState() == AgeState.ADULT;
        float height = (useRawDimensions ? getRawVerticalScaleFactor() : getVerticalScaleFactor()) * 2.0F;
        float width = getHorizontalScaleFactor() * 0.6F;

        return EntityDimensions.changing(width, height);
    }

    @Override
    public float getScaleFactor() {
        return Math.min(0.999f, getRawScaleFactor());
    }

    @Override
    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions size) {
        return getScaleFactor() * 1.75f;
    }

    @Override
    public final ActionResult interactAt(PlayerEntity player, Vec3d pos, @NotNull Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (hand.equals(Hand.MAIN_HAND) && !stack.isIn(TagsMCA.Items.ZOMBIE_EGGS) && stack.getItem() != Items.GOLDEN_APPLE) {
            if (player instanceof ServerPlayerEntity) {
                String t = new String(new char[getRandom().nextInt(8) + 2]).replace("\0", ". ");
                sendChatMessage(Text.literal(t), player);
            }
        }
        return super.interactAt(player, pos, hand);
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);

        if (getAgeState() == AgeState.UNASSIGNED) {
            if (random.nextFloat() < Config.getInstance().babyZombieChance) {
                setAgeState(isBaby() ? AgeState.BABY : AgeState.random());
            } else {
                setAgeState(AgeState.ADULT);
            }
        }

        if (getAgeState() == AgeState.BABY) {
            // baby zombie villager just cause weird bugs, so we skip that stage
            setAgeState(AgeState.TODDLER);
        }

        initialize(spawnReason);

        return data;
    }

    @Override
    protected void onPlayerSpawnedChild(PlayerEntity player, MobEntity child) {
        child.initialize((ServerWorldAccess) getWorld(), getWorld().getLocalDifficulty(child.getBlockPos()), SpawnReason.SPAWN_EGG, null, null);
    }

    @Override
    public void tickMovement() {
        super.tickMovement();

        burned--;
        if (isOnFire()) {
            burned = Config.getInstance().burnedClothingTickLength;
        }
        if (burned > 0) {
            spawnBurntParticles();
        }
    }

    @Override
    public void setBaby(boolean isBaby) {
        super.setBaby(isBaby);
        setAgeState(isBaby ? AgeState.BABY : AgeState.ADULT);
    }

    @Override
    public void setCustomName(@Nullable Text name) {
        super.setCustomName(name);

        if (name != null) {
            setName(name.getString());
        }
    }

    @Override
    public boolean isHostile() {
        return true;
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);

        if (getWorld().isClient) {
            return;
        }

        InventoryUtils.dropAllItems(this, inventory);

        relations.onDeath(cause);
    }

    public void setInventory(UpdatableInventory inventory) {
        NbtCompound nbt = new NbtCompound();
        InventoryUtils.saveToNBT(inventory, nbt);
        InventoryUtils.readFromNBT(this.inventory, nbt);
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    @Override
    @Nullable
    public <T extends MobEntity> T convertTo(EntityType<T> type, boolean keepInventory) {
        T mob;
        if (!isRemoved() && type == EntityType.VILLAGER) {
            mob = (T)super.convertTo(getGenetics().getGender().getVillagerType(), keepInventory);
        } else {
            mob = super.convertTo(type, keepInventory);
        }

        if (mob instanceof VillagerLike<?> villager) {
            villager.copyVillagerAttributesFrom(this);
            villager.setInfected(false);
        }

        if (mob instanceof VillagerEntityMCA villager) {
            villager.setUuid(getUuid());
            villager.setInventory(inventory);
            villager.setBreedingAge(getAgeState().toAge());
        }

        return mob;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        getTypeDataManager().load(this, nbt);
        relations.readFromNbt(nbt);

        updateAttributes();

        inventory.clear();
        InventoryUtils.readFromNBT(inventory, nbt);

        validateClothes();
    }

    @Override
    public void readAdditionalSaveDataForEditor(NbtCompound nbt) {
        NbtCompound merged = nbt.copy();
        if (merged.contains(VillagerEntityMCA.MCA_DATA_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound mcaData = merged.getCompound(VillagerEntityMCA.MCA_DATA_KEY);
            for (String key : mcaData.getKeys()) {
                NbtElement value = mcaData.get(key);
                if (value != null) {
                    merged.put(key, value.copy());
                }
            }
        }
        readCustomDataFromNbt(merged);
    }

    @Override
    public final void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        getTypeDataManager().save(this, nbt);
        relations.writeToNbt(nbt);
        InventoryUtils.saveToNBT(inventory, nbt);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> par) {
        if (getTypeDataManager().isParam(AGE_STATE, par) || getTypeDataManager().isParam(Genetics.SIZE.getParam(), par)) {
            calculateDimensions();
        }

        super.onTrackedDataSet(par);
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return !isPersistent();
    }
}
