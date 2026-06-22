package net.mca.entity;

import net.mca.MCA;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.item.BabyItem;
import net.mca.item.CribItem;
import net.mca.item.ItemsMCA;
import net.mca.util.network.datasync.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Arrays;

public class CribEntity extends Entity implements CTrackedEntity<CribEntity>
{
	VillagerEntityMCA infant;
	private static final CDataParameter<ItemStack> BABY = CParameter.create("babyItem", ItemStack.EMPTY);
	private static final CEnumParameter<CribWoodType> WOOD = CParameter.create("wood", CribWoodType.OAK);
	private static final CEnumParameter<DyeColor> COLOR = CParameter.create("color", DyeColor.RED);
    private static final CDataManager<CribEntity> DATA = createTrackedData().build();

    static CDataManager.Builder<CribEntity> createTrackedData() { return new CDataManager.Builder<>(CribEntity.class).addAll(BABY, WOOD, COLOR); }
	
	public CribEntity(EntityType<? extends CribEntity> type, World world) { super(type, world); }
	
	public CribWoodType getWoodType() { return getTrackedValue(WOOD); }
	public void setWoodType(CribWoodType wood) { setTrackedValue(WOOD, wood); }
	
	public DyeColor getColor() { return getTrackedValue(COLOR); }
	public void setColor(DyeColor color) { setTrackedValue(COLOR, color); }

	public ItemStack getBabyItem() { return getTrackedValue(BABY); }
	
	private boolean isOccupied() { return !getTrackedValue(BABY).equals(ItemStack.EMPTY) || infant != null; }
	
	@Override
    public boolean isCollidable() { return true; }

	@Override
    public boolean isPushedByFluids() { return false; }
	
	@Override
    public boolean isPushable() { return false; }

	@Override
	protected void initDataTracker() { getTypeDataManager().register(this); }

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt)
	{
		NbtCompound compound = nbt.getCompound(MCA.MOD_ID);
		if(compound.contains("Baby"))
		{
			setTrackedValue(BABY, ItemStack.fromNbt(compound.getCompound("Baby")));
			if(getTrackedValue(BABY).equals(ItemStack.EMPTY)) MCA.LOGGER.warn("Issue deseriaslizing baby item from crib NBT!");
		}
		if(compound.contains("Wood")) { setTrackedValue(WOOD, CribWoodType.values()[compound.getInt("Wood")]); }
		if(compound.contains("Color")) { setTrackedValue(COLOR, DyeColor.values()[compound.getInt("Color")]); }
	}
	
	@Override
    public double getMountedHeightOffset() { return 0.42; }

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt)
	{
		NbtCompound mcaCompound = new NbtCompound();
		
		if(!getTrackedValue(BABY).equals(ItemStack.EMPTY))
		{
			NbtCompound babyCompound = new NbtCompound();
			getTrackedValue(BABY).writeNbt(babyCompound);
			mcaCompound.put("Baby", babyCompound);
		}
		
		mcaCompound.putInt("Wood", Arrays.asList(CribWoodType.values()).indexOf(getTrackedValue(WOOD)));
		mcaCompound.putInt("Color", Arrays.asList(DyeColor.values()).indexOf(getTrackedValue(COLOR)));
		
		nbt.put(MCA.MOD_ID, mcaCompound);
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
	
	private void setEntityOccupant(VillagerEntityMCA occupant)
	{
		infant = occupant;
		infant.setInvulnerable(true);
	}
	
	private void unsetEntityOccupant()
	{
		if(infant != null)
		{
			infant.setInvulnerable(false);
			infant = null;
		}
	}
	
	@Override
    public ActionResult interact(PlayerEntity player, Hand hand)
	{
		// Refresh riding data
		if(hasPassengers() && getFirstPassenger() instanceof VillagerEntityMCA && infant == null) setEntityOccupant((VillagerEntityMCA) getFirstPassenger());
		
		// Removing occupant is first priority over adding occupant, so that multiple occupants dont exist.
		if(infant != null && infant.getVehicle() == this)
		{
			infant.startRiding(player, true);
			unsetEntityOccupant();
		}
		else if(!getTrackedValue(BABY).equals(ItemStack.EMPTY))
		{
			player.getInventory().insertStack(getTrackedValue(BABY));
			setTrackedValue(BABY, ItemStack.EMPTY);
		}
		else if(player.getInventory().getMainHandStack() != ItemStack.EMPTY && player.getInventory().getMainHandStack().getItem() instanceof BabyItem)
		{
			setTrackedValue(BABY, player.getInventory().getMainHandStack());
			player.getInventory().removeOne(getTrackedValue(BABY));
		}
		else if(player.getFirstPassenger() != null && player.getFirstPassenger() instanceof VillagerEntityMCA)
		{
			VillagerEntityMCA rider = (VillagerEntityMCA) player.getFirstPassenger();
			if(rider.getAgeState() == AgeState.BABY)
			{
				setEntityOccupant(rider);
				infant.startRiding(this, true);
			}
		}
		else return ActionResult.PASS;
		
        return ActionResult.SUCCESS;
    }
	
	@Override
    public boolean handleAttack(Entity attacker) { return attacker instanceof PlayerEntity && !this.getWorld().canPlayerModifyAt((PlayerEntity)attacker, this.getBlockPos()); }

    @Override
    public boolean canHit() { return true; }
    
    @Override
    public void tick()
    {
    	super.tick();
    	
    	if(this.isOnGround()) this.setVelocity(Vec3d.ZERO);
    	else if(!this.hasNoGravity()) { this.setVelocity(this.getVelocity().add(0.0, -0.04, 0.0)); }
    	this.move(MovementType.SELF, this.getVelocity());
        
    	if(getTrackedValue(BABY) != ItemStack.EMPTY && getTrackedValue(BABY).getItem() instanceof BabyItem) getTrackedValue(BABY).getItem().inventoryTick(getTrackedValue(BABY), getWorld(), this, 0, false);
    }

    @Override
    public boolean damage(DamageSource source, float amount)
    {
        if (this.getWorld().isClient || this.isRemoved()) { return false; }
        
        if(isOccupied()) return false;
        
        if (this.isInvulnerableTo(source)) { return false; }
        
        if (source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.IS_FIRE))
        {
            this.kill();
            return false;
        }
        
        boolean bl = source.getSource() instanceof PersistentProjectileEntity;
        boolean bl2 = bl && ((PersistentProjectileEntity)source.getSource()).getPierceLevel() > 0;
        boolean bl3 = "player".equals(source.getName());
        
        if (!bl3 && !bl) { return false; }
        
        if (source.getAttacker() instanceof PlayerEntity && !((PlayerEntity)source.getAttacker()).getAbilities().allowModifyWorld) { return false; }
        
        if (source.isSourceCreativePlayer())
        {
            this.playBreakSound();
            this.spawnBreakParticles();
            this.kill();
            return bl2;
        }
        else
        {
        	CribItem matchingType = (CribItem) ItemsMCA.CRIBS.stream().filter(c -> ((CribItem) c.get()).getColor() == getTrackedValue(COLOR) && ((CribItem) c.get()).getWood() == getTrackedValue(WOOD)).findFirst().get().get();
            Block.dropStack(this.getWorld(), this.getBlockPos(), new ItemStack(matchingType));
            this.spawnBreakParticles();
            this.kill();
        }
        
        return true;
    }

    private void spawnBreakParticles() {
        if (this.getWorld() instanceof ServerWorld) {
            ((ServerWorld)this.getWorld()).spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getDefaultState()),
    		this.getX(), this.getBodyY(0.6666666666666666), this.getZ(), 10, this.getWidth() / 4.0f, this.getHeight() / 4.0f, this.getWidth() / 4.0f, 0.05);
        }
    }

    private void playBreakSound() { this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ARMOR_STAND_BREAK, this.getSoundCategory(), 1.0f, 1.0f); }

	@Override
	public CDataManager<CribEntity> getTypeDataManager() { return DATA; }
}
