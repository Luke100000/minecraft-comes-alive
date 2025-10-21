package net.conczin.mca.entity;

import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.interaction.ZombieCommandHandler;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.util.InventoryUtils;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZombieVillagerEntityMCA extends ZombieVillager implements VillagerLike<ZombieVillagerEntityMCA>, CompassionateEntity<Relationship<ZombieVillagerEntityMCA>> {

    private static final CDataManager<ZombieVillagerEntityMCA> DATA = VillagerEntityMCA.createTrackedData(ZombieVillagerEntityMCA.class).build();

    private final VillagerBrain<ZombieVillagerEntityMCA> mcaBrain = new VillagerBrain<>(this);

    private final Genetics genetics = new Genetics(this);
    private final Traits traits = new Traits(this);

    private final Relationship<ZombieVillagerEntityMCA> relations = new Relationship<>(this);

    private final ZombieCommandHandler interactions = new ZombieCommandHandler(this);
    private final UpdatableInventory inventory = new UpdatableInventory(27);

    private int burned;

    public ZombieVillagerEntityMCA(EntityType<? extends ZombieVillager> type, Level world, Gender gender) {
        super(type, world);
        genetics.setGender(gender);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        getTypeDataManager().register(builder);
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
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);

        if (name != null) {
            setName(name.getString());
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {

        if (pose == Pose.SLEEPING) {
            return SLEEPING_DIMENSIONS;
        }

        float height = getVerticalScaleFactor() * 2.0F;
        float width = getHorizontalScaleFactor() * 0.6F;

        return EntityDimensions.scalable(width, height);
    }

    @Override
    public final InteractionResult interactAt(Player player, Vec3 pos, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand.equals(InteractionHand.MAIN_HAND) && !stack.is(TagsMCA.Items.ZOMBIE_EGGS) && stack.getItem() != Items.GOLDEN_APPLE) {
            if (player instanceof ServerPlayer) {
                String t = new String(new char[getRandom().nextInt(8) + 2]).replace("\0", ". ");
                sendChatMessage(Component.literal(t), player);
            }
        }
        return super.interactAt(player, pos, hand);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        SpawnGroupData data = super.finalizeSpawn(world, difficulty, spawnReason, entityData);

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
    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
        child.finalizeSpawn((ServerLevelAccessor) level(), level().getCurrentDifficultyAt(child.blockPosition()), MobSpawnType.SPAWN_EGG, null);
    }

    @Override
    public void aiStep() {
        super.aiStep();

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
    public boolean isHostile() {
        return true;
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);

        if (level().isClientSide) {
            return;
        }

        InventoryUtils.dropAllItems(this, inventory);

        relations.onDeath(cause);
    }

    public void setInventory(UpdatableInventory inventory) {
        CompoundTag nbt = new CompoundTag();
        InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);
        InventoryUtils.readFromNBT(this.registryAccess(), this.inventory, nbt);
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    @Override
    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> type, boolean keepInventory) {
        T mob;
        if (!isRemoved() && type == EntityType.VILLAGER) {
            mob = (T) super.convertTo(getGenetics().getGender().getVillagerType(), keepInventory);
        } else {
            mob = super.convertTo(type, keepInventory);
        }

        if (mob instanceof VillagerLike<?> villager) {
            villager.copyVillagerAttributesFrom(this);
            villager.setInfected(false);
        }

        if (mob instanceof VillagerEntityMCA villager) {
            villager.setUUID(getUUID());
            villager.setInventory(inventory);
            villager.setAge(getAgeState().toAge());
        }

        return mob;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        getTypeDataManager().load(this, nbt);
        relations.readFromNbt(nbt);

        updateAttributes();

        inventory.clearContent();
        InventoryUtils.readFromNBT(this.registryAccess(), inventory, nbt);

        validateClothes();
    }

    @Override
    public final void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        getTypeDataManager().save(this, nbt);
        relations.writeToNbt(nbt);
        InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> par) {
        if (getTypeDataManager().isParam(AGE_STATE, par) || getTypeDataManager().isParam(Genetics.SIZE.getParam(), par)) {
            refreshDimensions();
        }

        super.onSyncedDataUpdated(par);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return !isPersistenceRequired();
    }
}
