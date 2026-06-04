package net.conczin.mca.entity;

import java.util.Arrays;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.item.CribItem;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CEnumParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.conczin.mca.util.network.datasync.CTrackedEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

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
      return this.getTrackedValue(WOOD);
   }

   public void setWoodType(CribWoodType wood) {
      this.setTrackedValue(WOOD, wood);
   }

   public DyeColor getColor() {
      return this.getTrackedValue(COLOR);
   }

   public void setColor(DyeColor color) {
      this.setTrackedValue(COLOR, color);
   }

   public ItemStack getBabyItem() {
      return this.getTrackedValue(BABY).copy();
   }

   private boolean isOccupied() {
      return !this.getTrackedValue(BABY).isEmpty() || this.infant != null;
   }

   public boolean canBeCollidedWith() {
      return true;
   }

   public boolean isPushedByFluid() {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   protected void defineSynchedData(Builder builder) {
      this.getTypeDataManager().register(builder);
   }

   protected void readAdditionalSaveData(ValueInput input) {
      input.read("Baby", ItemStack.OPTIONAL_CODEC).ifPresent(stack -> {
         this.setTrackedValue(BABY, stack);
         if (stack.isEmpty()) {
            MCA.LOGGER.warn("Issue deserializing baby item from crib NBT!");
         }
      });
      this.setTrackedValue(WOOD, CribWoodType.values()[input.getIntOr("Wood", 0)]);
      this.setTrackedValue(COLOR, DyeColor.values()[input.getIntOr("Color", DyeColor.RED.ordinal())]);
   }

   protected void addAdditionalSaveData(ValueOutput output) {
      if (!this.getTrackedValue(BABY).equals(ItemStack.EMPTY)) {
         output.store("Baby", ItemStack.OPTIONAL_CODEC, this.getTrackedValue(BABY));
      }

      output.putInt("Wood", Arrays.asList(CribWoodType.values()).indexOf(this.getTrackedValue(WOOD)));
      output.putInt("Color", Arrays.asList(DyeColor.values()).indexOf(this.getTrackedValue(COLOR)));
   }

   protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
      return new Vec3(0.0, 0.1, 0.0);
   }

   private void setEntityOccupant(VillagerEntityMCA occupant) {
      this.infant = occupant;
      this.infant.setInvulnerable(true);
   }

   private void unsetEntityOccupant() {
      if (this.infant != null) {
         this.infant.setInvulnerable(false);
         this.infant = null;
      }
   }

   public InteractionResult interact(Player player, InteractionHand hand) {
      if (this.isVehicle() && this.getFirstPassenger() instanceof VillagerEntityMCA && this.infant == null) {
         this.setEntityOccupant((VillagerEntityMCA)this.getFirstPassenger());
      }

      if (this.infant != null && this.infant.getVehicle() == this) {
         this.infant.startRiding(player, true, true);
         this.unsetEntityOccupant();
      } else if (!this.getTrackedValue(BABY).isEmpty()) {
         ItemStack babyStack = this.getTrackedValue(BABY).copy();
         this.setTrackedValue(BABY, ItemStack.EMPTY);
         player.getInventory().add(babyStack);
      } else if (!player.getInventory().getSelectedItem().isEmpty() && player.getInventory().getSelectedItem().getItem() instanceof BabyItem) {
         ItemStack babyStack = player.getInventory().getSelectedItem();
         this.setTrackedValue(BABY, babyStack.copy());
         babyStack.shrink(1);
      } else {
         if (player.getFirstPassenger() == null || !(player.getFirstPassenger() instanceof VillagerEntityMCA rider)) {
            return InteractionResult.PASS;
         }

         if (rider.getAgeState() == AgeState.BABY) {
            this.setEntityOccupant(rider);
            this.infant.startRiding(this, true, true);
         }
      }

      return InteractionResult.SUCCESS;
   }

   public boolean skipAttackInteraction(Entity attacker) {
      return attacker instanceof Player && !this.level().mayInteract((Player)attacker, this.blockPosition());
   }

   public boolean isPickable() {
      return true;
   }

   public void tick() {
      super.tick();
      if (this.onGround()) {
         this.setDeltaMovement(Vec3.ZERO);
      } else if (!this.isNoGravity()) {
         this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
      }

      this.move(MoverType.SELF, this.getDeltaMovement());
      if (!this.getTrackedValue(BABY).isEmpty() && this.getTrackedValue(BABY).getItem() instanceof BabyItem) {
         this.getTrackedValue(BABY).inventoryTick(this.level(), this, EquipmentSlot.MAINHAND);
      }
   }

   public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
      if (this.isRemoved()) {
         return false;
      } else if (this.isOccupied()) {
         return false;
      } else if (this.isInvulnerableToBase(source)) {
         return false;
      } else if (!source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FIRE)) {
         boolean bl = source.getDirectEntity() instanceof AbstractArrow;
         boolean bl2 = bl && ((AbstractArrow)source.getDirectEntity()).getPierceLevel() > 0;
         boolean bl3 = "player".equals(source.getMsgId());
         if (!bl3 && !bl) {
            return false;
         } else if (source.getEntity() instanceof Player && !((Player)source.getEntity()).getAbilities().mayBuild) {
            return false;
         } else if (source.isCreativePlayer()) {
            this.playBreakSound();
            this.spawnBreakParticles();
            this.kill(world);
            return bl2;
         } else {
            CribItem matchingType = ItemsMCA.CRIBS
               .stream()
               .filter(c -> c.getColor() == this.getTrackedValue(COLOR) && c.getWood() == this.getTrackedValue(WOOD))
               .findFirst()
               .get();
            Block.popResource(this.level(), this.blockPosition(), new ItemStack(matchingType));
            this.spawnBreakParticles();
            this.kill(world);
            return true;
         }
      } else {
         this.kill(world);
         return false;
      }
   }

   private void spawnBreakParticles() {
      if (this.level() instanceof ServerLevel) {
         ((ServerLevel)this.level())
            .sendParticles(
               new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
               this.getX(),
               this.getY(0.6666666666666666),
               this.getZ(),
               10,
               this.getBbWidth() / 4.0F,
               this.getBbHeight() / 4.0F,
               this.getBbWidth() / 4.0F,
               0.05
            );
      }
   }

   private void playBreakSound() {
      this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0F, 1.0F);
   }

   @Override
   public CDataManager<CribEntity> getTypeDataManager() {
      return DATA;
   }
}
