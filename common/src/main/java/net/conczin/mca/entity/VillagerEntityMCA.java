package net.conczin.mca.entity;

import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.ConversationManager;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.LongTermMemory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mood;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Residency;
import net.conczin.mca.entity.ai.StatusEffectDangerSet;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.conczin.mca.mixin.MixinVillagerInvoker;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.registry.ParticleTypesMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.registry.SoundsMCA;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.resources.Names;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillagerTrackerManager;
import net.conczin.mca.util.InventoryUtils;
import net.conczin.mca.util.WorldUtils;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.Zombie.ZombieGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VillagerEntityMCA
   extends Villager
   implements VillagerLike<VillagerEntityMCA>,
   MenuProvider,
   CompassionateEntity<BreedableRelationship>,
   CrossbowAttackMob {
   private static final CDataParameter<Float> INFECTION_PROGRESS = CParameter.create("InfectionProgress", 0.0F);
   private static final CDataParameter<Integer> GROWTH_AMOUNT = CParameter.create("GrowthAmount", -AgeState.getMaxAge());
   private static final CDataManager<VillagerEntityMCA> DATA = createTrackedData(VillagerEntityMCA.class).build();
   private static final int RECALCULATE_DIMENSIONS_EVERY_N_TICKS = 100;
   public final ConversationManager conversationManager = new ConversationManager(this);
   final Identifier EXTRA_HEALTH_EFFECT_ID = MCA.locate("trait_health");
   private final VillagerBrain<VillagerEntityMCA> mcaBrain = new VillagerBrain(this);
   private final LongTermMemory longTermMemory = new LongTermMemory(this);
   private final Genetics genetics = new Genetics(this);
   private final Traits traits = new Traits(this);
   private final Residency residency = new Residency(this);
   private final BreedableRelationship relations = new BreedableRelationship(this);
   private final VillagerCommandHandler interactions = new VillagerCommandHandler(this);
   private final UpdatableInventory inventory = new UpdatableInventory(27);
   private final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.UNASSIGNED);
   long lastCooldown = 0L;
   private VillagerLike.PlayerModel playerModel;
   private int despawnDelay;
   private int burned;
   private long lastHit = 0L;
   private int prevGrowthAmount;
   private boolean interactedWith;

   public VillagerEntityMCA(EntityType<VillagerEntityMCA> type, Level w, Gender gender) {
      super(type, w);
      this.inventory.addListener(this::onInvChange);
      this.genetics.setGender(gender);
   }

   public static <E extends Entity> CDataManager.Builder<E> createTrackedData(Class<E> type) {
      return VillagerLike.createTrackedData(type)
         .addAll(INFECTION_PROGRESS, GROWTH_AMOUNT)
         .add(Residency::createTrackedData)
         .add(BreedableRelationship::createTrackedData);
   }

   private static boolean canEat(ItemStack i) {
      FoodProperties foodProperties = (FoodProperties)i.get(DataComponents.FOOD);
      Consumable consumable = (Consumable)i.get(DataComponents.CONSUMABLE);
      return foodProperties != null
         && foodProperties.nutrition() > 0
         && (
            consumable == null
               || consumable.onConsumeEffects()
                  .stream()
                  .noneMatch(
                     effect -> effect instanceof ApplyStatusEffectsConsumeEffect applyStatusEffects
                        && applyStatusEffects.effects().stream().anyMatch(statusEffect -> StatusEffectDangerSet.IS_DANGER.contains(statusEffect.getEffect()))
                  )
         );
   }

   public static Builder createAttributes() {
      return Villager.createAttributes()
         .add(Attributes.ATTACK_DAMAGE, 3.0)
         .add(Attributes.ATTACK_KNOCKBACK, 1.0)
         .add(Attributes.MAX_HEALTH, Config.getInstance().villagerMaxHealth);
   }

   @Override
   public CDataManager<VillagerEntityMCA> getTypeDataManager() {
      return DATA;
   }

   @Override
   public VillagerLike.PlayerModel getPlayerModel() {
      return this.playerModel;
   }

   @Override
   public boolean isBurned() {
      return this.burned > 0;
   }

   public void restock() {
      super.restock();
      if (!this.level().isClientSide()) {
         Optional<Village> village = this.residency.getHomeVillage();
         if (village.isPresent() && Config.getInstance().villagerRestockNotification) {
            village.get().broadCastMessage((ServerLevel)this.level(), "events.restock", this.getName().getString());
         }
      }
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      this.getTypeDataManager().register(builder);
   }

   protected Brain<?> makeBrain(Dynamic<?> dynamic) {
      return VillagerTasksMCA.initializeTasks(this, VillagerTasksMCA.createProfile().makeBrain(dynamic));
   }

   public void refreshBrain(ServerLevel world) {
      Brain<VillagerEntityMCA> brain = this.getMCABrain();
      brain.stopAll(world, this);
      this.brain = brain.copyWithoutBehaviors();
      VillagerTasksMCA.initializeTasks(this, this.getMCABrain());
   }

   public Brain<VillagerEntityMCA> getMCABrain() {
      return (Brain<VillagerEntityMCA>)this.brain;
   }

   @Override
   public Genetics getGenetics() {
      return this.genetics;
   }

   @Override
   public Traits getTraits() {
      return this.traits;
   }

   public BreedableRelationship getRelationships() {
      return this.relations;
   }

   @Override
   public VillagerBrain<?> getVillagerBrain() {
      return this.mcaBrain;
   }

   public LongTermMemory getLongTermMemory() {
      return this.longTermMemory;
   }

   public Residency getResidency() {
      return this.residency;
   }

   public VillagerCommandHandler getInteractions() {
      return this.interactions;
   }

   protected Component getTypeName() {
      return this.getProfessionText();
   }

   @Nullable
   public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType, SpawnGroupData groupData) {
      SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
      this.initialize(spawnType);
      this.setAgeState(AgeState.byCurrentAge(this.getAge()));
      FamilyTreeNode entry = this.getRelationships().getFamilyEntry();
      if (!FamilyTreeNode.isValid(entry.father()) && !FamilyTreeNode.isValid(entry.mother())) {
         FamilyTree tree = FamilyTree.get(level.getLevel());
         FamilyTreeNode father = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.MALE), Gender.MALE);
         FamilyTreeNode mother = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.FEMALE), Gender.FEMALE);
         father.setDeceased(true);
         mother.setDeceased(true);
         entry.setFather(father);
         entry.setMother(mother);
      }

      return data;
   }

   public final VillagerProfession getProfession() {
      return (VillagerProfession)this.getVillagerData().profession().value();
   }

   public final void setProfession(VillagerProfession profession) {
      this.setVillagerData(this.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession)));
      this.refreshBrain((ServerLevel)this.level());
   }

   @Override
   public Identifier getProfessionId() {
      return BuiltInRegistries.VILLAGER_PROFESSION.getKey(this.getProfession());
   }

   @Override
   public boolean isProfessionImportant() {
      return ProfessionsMCA.IS_IMPORTANT.contains(this.getProfession());
   }

   @Override
   public boolean requiresHome() {
      return !ProfessionsMCA.NEEDS_NO_HOME.contains(this.getProfession()) && this.getDespawnDelay() <= 0;
   }

   @Override
   public boolean canTradeWithProfession() {
      return !ProfessionsMCA.CAN_NOT_TRADE.contains(this.getProfession()) || this.offers != null && !this.offers.isEmpty();
   }

   public void setVillagerData(VillagerData data) {
      VillagerProfession profession = (VillagerProfession)data.profession().value();
      boolean hasChanged = !this.level().isClientSide() && this.getProfession() != profession && profession != ProfessionsMCA.OUTLAW;
      super.setVillagerData(data);
      if (hasChanged) {
         this.randomizeClothes();
         this.getRelationships().getFamilyEntry().setProfession(profession);
      }
   }

   public void setBaby(boolean isBaby) {
      this.setAge(isBaby ? -AgeState.getMaxAge() : 0);
   }

   public void setAge(int age) {
      super.setAge(age);
      if (age != -2) {
         this.setTrackedValue(GROWTH_AMOUNT, age);
         this.setAgeState(AgeState.byCurrentAge(age));
         AgeState current = this.getAgeState();
         AgeState next = current.getNext();
         if (current != next) {
            this.dimensions.interpolate(current, next, AgeState.getDelta(age));
         } else {
            this.dimensions.set(current);
         }
      }
   }

   public boolean doHurtTarget(ServerLevel world, Entity target) {
      this.attackedEntity(target);
      return super.doHurtTarget(world, target);
   }

   private void attackedEntity(Entity target) {
      if (target instanceof Player player) {
         this.pardonPlayers(player);
      }
   }

   private void pardonPlayers() {
      this.pardonPlayers(1);
   }

   public void pardonPlayers(int amount) {
      int bounty = this.getSmallBounty();
      if (bounty <= amount) {
         this.getBrain().eraseMemory(MemoryModuleTypeMCA.SMALL_BOUNTY);
         this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
         this.getBrain().eraseMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER);
      } else {
         this.getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, bounty - amount);
      }
   }

   private void pardonPlayers(Player attacker) {
      this.pardonPlayers();
      int bounty = this.getSmallBounty();
      if (bounty <= this.getMaxWarnings(attacker)) {
         this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
      }
   }

   public boolean canInteractWithItemStackInHand(ItemStack stack) {
      return stack.getItem() != ItemsMCA.VILLAGER_EDITOR
         && stack.getItem() != ItemsMCA.NEEDLE_AND_THREAD
         && stack.getItem() != ItemsMCA.COMB
         && stack.getItem() != ItemsMCA.POTION_OF_FEMININITY
         && stack.getItem() != ItemsMCA.POTION_OF_MASCULINITY;
   }

   public final InteractionResult interactAt(Player player, Vec3 pos, @NotNull InteractionHand hand) {
      if (this.getVehicle() != null && this.getVehicle().equals(player)) {
         return InteractionResult.PASS;
      } else {
         ItemStack stack = player.getItemInHand(hand);
         boolean isOnBlacklist = Config.getInstance().villagerInteractionItemBlacklist.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
         if (hand.equals(InteractionHand.MAIN_HAND)
            && !isOnBlacklist
            && !stack.is(TagsMCA.Items.VILLAGER_EGGS)
            && this.canInteractWithItemStackInHand(stack)
            && !this.getVillagerBrain().isPanicking()) {
            this.getDialogueType(player);
            if (!player.isShiftKeyDown()) {
               this.playWelcomeSound();
               this.interactedWith = true;
               return this.interactions.interactAt(player, pos, hand);
            }

            if (!this.level().isClientSide() && this.canTradeWithProfession()) {
               this.getInteractions().stopInteracting();
               MixinVillagerInvoker invoker = (MixinVillagerInvoker)this;
               invoker.invokeStartTrading(player);
            }
         }

         return super.interactAt(player, pos, hand);
      }
   }

   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (this.getVehicle() != null && this.getVehicle().equals(player)) {
         return InteractionResult.PASS;
      } else {
         ItemStack stack = player.getItemInHand(hand);
         if (!stack.is(TagsMCA.Items.VILLAGER_EGGS)
            && this.isAlive()
            && !this.isTrading()
            && !this.isSleeping()
            && this.canInteractWithItemStackInHand(stack)
            && !this.getVillagerBrain().isPanicking()) {
            if (this.isBaby()) {
               this.copiedSayNo();
            } else if (!this.level().isClientSide()) {
               boolean hasOffers = this.hasTradeOffers();
               if (hand == InteractionHand.MAIN_HAND) {
                  if (!hasOffers && !this.level().isClientSide()) {
                     this.copiedSayNo();
                  }

                  player.awardStat(Stats.TALKED_TO_VILLAGER);
               }

               if (hasOffers && !this.level().isClientSide()) {
                  this.copiedBeginTradeWith(player);
               }
            }

            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
         } else {
            return InteractionResult.PASS;
         }
      }
   }

   private void copiedSayNo() {
      this.setUnhappyCounter(40);
      if (!this.level().isClientSide()) {
         this.playSound(this.getNoSound(), this.getSoundVolume(), this.getVoicePitch());
      }
   }

   public boolean hasTradeOffers() {
      return !this.getOffers().isEmpty();
   }

   public void startTrading(Player customer) {
      this.copiedBeginTradeWith(customer);
   }

   private void copiedBeginTradeWith(Player customer) {
      this.copiedPrepareOffersFor(customer);
      this.setTradingPlayer(customer);
      this.openTradingScreen(customer, this.getDisplayName(), this.getVillagerData().level());
   }

   private void copiedPrepareOffersFor(Player player) {
      int reputation = this.getPlayerReputation(player);
      if (reputation != 0) {
         for (MerchantOffer tradeOffer : this.getOffers()) {
            tradeOffer.addToSpecialPriceDiff(-Mth.floor(reputation * tradeOffer.getPriceMultiplier()));
         }
      }

      if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
         MobEffectInstance statusEffect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
         int amplifier = statusEffect.getAmplifier();

         for (MerchantOffer tradeOffer2 : this.getOffers()) {
            double d = 0.3 + 0.0625 * amplifier;
            int k = (int)Math.floor(d * tradeOffer2.getBaseCostA().getCount());
            tradeOffer2.addToSpecialPriceDiff(-Math.max(k, 1));
         }
      }
   }

   public VillagerEntityMCA getBreedOffspring(ServerLevel level, AgeableMob partner) {
      VillagerEntityMCA child = partner instanceof VillagerEntityMCA partnerVillager
         ? this.relations.getPregnancy().createChild(Gender.getRandom(), partnerVillager)
         : this.relations.getPregnancy().createChild(Gender.getRandom());
      child.setVillagerData(child.getVillagerData().withType(level.registryAccess(), this.getRandomType(partner)));
      child.finalizeSpawn(level, level.getCurrentDifficultyAt(child.blockPosition()), EntitySpawnReason.BREEDING, null);
      return child;
   }

   private ResourceKey<VillagerType> getRandomType(AgeableMob partner) {
      double d = this.random.nextDouble();
      if (d < 0.5) {
         return VillagerType.byBiome(this.level().getBiome(this.blockPosition()));
      } else {
         return d < 0.75
            ? this.getVillagerData().type().unwrapKey().orElse(VillagerType.PLAINS)
            : ((Villager)partner).getVillagerData().type().unwrapKey().orElse(VillagerType.PLAINS);
      }
   }

   public final boolean hurtServer(ServerLevel world, DamageSource source, float damageAmount) {
      if (this.getVehicle() instanceof Player) {
         return super.hurtServer(world, source, 0.0F);
      } else if (!Config.getInstance().canHurtBabies && !source.is(DamageTypeTags.BYPASSES_SHIELD) && this.getAgeState() == AgeState.BABY) {
         if (source.getEntity() instanceof Player && this.requestCooldown()) {
            this.sendEventMessage(Component.translatable("villager.baby_hit"));
         }

         return super.hurtServer(world, source, 0.0F);
      } else {
         if (this.getProfession() == ProfessionsMCA.GUARD) {
            damageAmount *= 0.5F;
         }

         if (this.getTraits().hasTrait(Traits.TOUGH)) {
            damageAmount *= 0.75F;
         }

         if (!this.level().isClientSide()) {
            if (source.getEntity() instanceof Player player) {
               if (this.level().getGameTime() - this.lastHit > 40L) {
                  this.lastHit = this.level().getGameTime();
                  if ((!this.isGuard() || this.getSmallBounty() == 0) && this.requestCooldown()) {
                     if (this.getHealth() < this.getMaxHealth() / 2.0F) {
                        this.sendChatMessage(player, "villager.badly_hurt");
                     } else {
                        this.sendChatMessage(player, "villager.hurt");
                     }
                  }
               }

               int trustIssues = (int)((1.0 - this.getHealth() / this.getMaxHealth() * 0.75) * (3.0 + 2.0 * damageAmount));
               this.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-trustIssues);
            }

            if (source.getDirectEntity() instanceof Zombie
               && this.getProfession() != ProfessionsMCA.GUARD
               && Config.getInstance().enableInfection
               && this.random.nextFloat() < Config.getInstance().zombieBiteInfectionChance
               && this.random.nextFloat() > (this.getVillagerData().level() - 1) * Config.getInstance().infectionChanceDecreasePerLevel
               && (this.getResidency().getHomeVillage().filter(v -> v.hasBuilding("infirmary")).isEmpty() || this.random.nextBoolean())) {
               this.setInfected(true);
               this.sendChatToAllAround("villager.bitten");
               MCA.LOGGER.info("{} has been infected", this.getName());
            }
         }

         Entity attacker = source.getEntity();
         if (attacker instanceof LivingEntity livingEntity && !this.isHostile() && !this.isFriend(attacker.getType())) {
            this.getBrain().setMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER, Optional.of(livingEntity));
            this.getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, this.getSmallBounty() + 1);
            Vec3 pos = this.position();
            this.level().getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos, pos).inflate(32.0)).forEach(v -> {
               if (this.distanceToSqr(v) <= (v.getTarget() == null ? 1024 : 64)) {
                  if (attacker instanceof Player player) {
                     int bounty = v.getSmallBounty();
                     if (v.isGuard()) {
                        int maxWarning = v.getMaxWarnings(player);
                        if (bounty > maxWarning) {
                           v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity);
                        } else if (bounty == 0 || bounty == maxWarning) {
                           v.sendChatMessage(player, "villager.warning");
                        }

                        v.getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, bounty + 1);
                     }
                  } else if (v.isGuard()) {
                     v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity);
                  }
               }
            });
         }

         if (attacker instanceof IronGolem golem) {
            golem.stopBeingAngry();
            damageAmount *= 0.0F;
         }

         return super.hurtServer(world, source, damageAmount);
      }
   }

   private boolean requestCooldown() {
      if (this.level().getGameTime() - this.lastCooldown > 100L) {
         this.lastCooldown = this.level().getGameTime();
         return true;
      } else {
         return false;
      }
   }

   public boolean isGuard() {
      return this.getProfession() == ProfessionsMCA.GUARD || this.getProfession() == ProfessionsMCA.ARCHER;
   }

   public int getSmallBounty() {
      return Objects.requireNonNull(this.getBrain().getMemoryInternal(MemoryModuleTypeMCA.SMALL_BOUNTY)).orElse(0);
   }

   public boolean isHitBy(ServerPlayer player) {
      return Objects.requireNonNull(this.getBrain().getMemoryInternal(MemoryModuleTypeMCA.HIT_BY_PLAYER)).filter(v -> v == player).isPresent();
   }

   private int getMaxWarnings(Player attacker) {
      return this.getVillagerBrain().getMemoriesForPlayer(attacker).getHearts() / Math.max(1, Config.getInstance().heartsForPardonHit);
   }

   public void aiStep() {
      this.updateSwingTime();
      super.aiStep();
      this.burned--;
      if (this.isOnFire()) {
         this.burned = Config.getInstance().burnedClothingTickLength;
      }

      if (this.burned > 0) {
         this.spawnBurntParticles();
      }

      if (!this.level().isClientSide()) {
         if (this.tickCount % 200 == 0 && this.getHealth() < this.getMaxHealth()) {
            InteractionHand foodHand = this.getDominantHand();
            ItemStack food = this.getItemInHand(foodHand);
            FoodProperties foodProperties = (FoodProperties)food.get(DataComponents.FOOD);
            if (foodProperties != null) {
               this.eatFood(foodHand, food, foodProperties);
            } else if (!this.findAndEquipToMain(VillagerEntityMCA::canEat)) {
               this.heal(1.0F);
            }
         }

         this.tickDespawnDelay();
         this.residency.tick();
         this.relations.tick(this.tickCount);
         this.inventory.update(this);
         if (this.tickCount % Config.getInstance().pardonPlayerTicks == 0) {
            this.pardonPlayers();
         }

         this.mcaBrain.think();
         if (this.tickCount % Config.getInstance().giftDesaturationReset == 0) {
            this.getRelationships().getGiftSaturation().pop();
         }

         if (this.interactedWith && this.tickCount % Config.getInstance().trackVillagerPositionEveryNTicks == 0) {
            VillagerTrackerManager.update(this);
         }
      }
   }

   protected boolean findAndEquipToMain(Predicate<ItemStack> predicate) {
      int slot = InventoryUtils.getFirstSlotContainingItem(this.getInventory(), predicate);
      if (slot > -1) {
         ItemStack replacement = this.getInventory().getItem(slot).split(1);
         if (!replacement.isEmpty()) {
            this.setItemInHand(this.getDominantHand(), replacement);
            return true;
         }
      }

      return false;
   }

   public void tick() {
      super.tick();
      int age = this.getTrackedValue(GROWTH_AMOUNT);
      if (age / 100 != this.prevGrowthAmount / 100) {
         this.prevGrowthAmount = age;
         this.refreshDimensions();
      }

      if (this.level().isClientSide()) {
         if (this.relations.isProcreating()) {
            this.yHeadRot += 50.0F;
         }

         Mood mood = this.mcaBrain.getMood();
         if (mood.getParticle() != null && this.tickCount % mood.getParticleInterval() == 0 && this.level().random.nextBoolean()) {
            this.addParticlesAroundSelf(mood.getParticle());
         }
      } else {
         float infection = this.getInfectionProgress();
         if (infection > 0.0F && this.tickCount % 20 == 0) {
            if (infection > 0.2F && this.level().random.nextInt(25) == 0) {
               this.sendChatToAllAround("villager.sickness");
            }

            infection += 1.0F / Config.getInstance().infectionTime;
            this.setInfectionProgress(infection);
            if (infection > 1.0F) {
               this.convertTo(EntityType.ZOMBIE_VILLAGER, false);
               this.discard();
            }
         }

         if (this.tickCount % 90 == 0 && this.mcaBrain.isPanicking()) {
            this.sendChatToAllAround("villager.scream");
         }

         if (this.tickCount % 60 == 0 && this.random.nextInt(50) == 0 && this.traits.hasTrait(Traits.SIRBEN)) {
            this.sendChatToAllAround("sirben");
         }

         AttributeInstance instance = this.getAttributes().getInstance(Attributes.MAX_HEALTH);
         if (instance != null) {
            int level = this.getVillagerData().level() - 1;
            instance.removeModifier(this.EXTRA_HEALTH_EFFECT_ID);
            instance.addTransientModifier(
               new AttributeModifier(this.EXTRA_HEALTH_EFFECT_ID, Config.getInstance().villagerHealthBonusPerLevel * level, Operation.ADD_VALUE)
            );
         }

         if (this.tickCount % 12000 == 0) {
            int base = Math.round(this.mcaBrain.getMoodValue() / 12.0F);
            int value = this.random.nextInt(7) - 3;
            this.mcaBrain.modifyMoodValue(value - base);
         }
      }
   }

   public void refreshDimensions() {
      AgeState current = this.getAgeState();
      AgeState next = current.getNext();
      if (next != current) {
         this.dimensions.interpolate(current, next, AgeState.getDelta(this.getTrackedValue(GROWTH_AMOUNT).intValue()));
      } else {
         this.dimensions.set(current);
      }

      boolean oldOnGround = this.onGround();
      super.refreshDimensions();
      this.setOnGround(oldOnGround);
   }

   private void eatFood(InteractionHand hand, ItemStack stack, FoodProperties foodProperties) {
      this.heal(foodProperties.nutrition());
      this.setItemInHand(hand, stack.finishUsingItem(this.level(), this));
   }

   public void rideTick() {
      super.rideTick();
      Entity vehicle = this.getVehicle();
      if (vehicle instanceof PathfinderMob pathAwareEntity) {
         this.yBodyRot = pathAwareEntity.yBodyRot;
      }

      if (vehicle instanceof Player player) {
         List<Entity> passengers = vehicle.getPassengers();
         float yaw = -player.yBodyRot * (float) (Math.PI / 180.0);
         boolean left = passengers.get(0) == this;
         boolean head = passengers.size() > 2 && passengers.get(2) == this;
         Vec3 offset = head ? new Vec3(0.0, 0.55F, 0.0) : new Vec3(left ? 0.4F : -0.4F, 0.05F, 0.0).yRot(yaw);
         if (this.isClientSide() && MCAClient.useGeneticsRenderer(vehicle.getUUID())) {
            float height = CommonVillagerModel.getVillager(vehicle).getRawVerticalScaleFactor();
            offset = offset.multiply(1.0, height, 1.0);
            offset = offset.add(0.0, (height - 1.0F) * 1.5 - 0.7, 0.0);
         }

         Vec3 pos = this.position();
         this.setPosRaw(pos.x() + offset.x(), pos.y() + offset.y(), pos.z() + offset.z());
         if (vehicle.isShiftKeyDown()) {
            this.stopRiding();
         }
      }
   }

   public EntityDimensions getDefaultDimensions(Pose pose) {
      Entity vehicle = this.getVehicle();
      if (vehicle instanceof Player) {
         return SLEEPING_DIMENSIONS;
      } else if (pose == Pose.SLEEPING) {
         return SLEEPING_DIMENSIONS;
      } else {
         float height = this.getVerticalScaleFactor() * 2.0F;
         float width = this.getHorizontalScaleFactor() * 0.6F;
         return EntityDimensions.scalable(width, height);
      }
   }

   public void die(DamageSource cause) {
      for (EquipmentSlot slot : EquipmentSlot.values()) {
         this.setItemSlot(slot, ItemStack.EMPTY);
      }

      if (!this.level().isClientSide()) {
         this.getResidency().getHomeVillage().flatMap(Village::getCivilRegistry).ifPresent(r -> r.addText(this.getCombatTracker().getDeathMessage()));
      }

      super.die(cause);
      if (!this.level().isClientSide()) {
         InventoryUtils.dropAllItems(this, this.inventory);
         this.relations.onDeath(cause);
         Optional<Village> village = this.residency.getHomeVillage();
         if (village.isPresent() && cause.getEntity() != null && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.players().forEach(player -> {
               Rank relationToVillage = Tasks.getRank(village.get(), player);
               Identifier causeId = EntityType.getKey(cause.getEntity().getType());
               CriterionMCA.FATE.trigger(player, causeId, relationToVillage);
            });
         }

         this.residency.leaveHome();
         if (this.interactedWith) {
            VillagerTrackerManager.update(this);
         }
      }
   }

   public MoveControl getMoveControl() {
      return this.isRidingHorse() ? this.moveControl : super.getMoveControl();
   }

   public PathNavigation getNavigation() {
      return this.isRidingHorse() ? this.navigation : super.getNavigation();
   }

   protected boolean isRidingHorse() {
      return this.isPassenger() && this.getVehicle() instanceof AbstractHorse;
   }

   public void teleportTo(double destX, double destY, double destZ) {
      if (this.isPassenger()) {
         Entity rootVehicle = this.getRootVehicle();
         if (rootVehicle instanceof Mob) {
            rootVehicle.teleportTo(destX, destY, destZ);
            return;
         }
      }

      super.teleportTo(destX, destY, destZ);
   }

   public SoundEvent getDeathSound() {
      if (Config.getInstance().useMCAVoices) {
         return this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SCREAM : SoundsMCA.VILLAGER_FEMALE_SCREAM;
      } else {
         return Config.getInstance().useVanillaVoices ? super.getDeathSound() : SoundsMCA.SILENT;
      }
   }

   public SoundEvent getSurprisedSound() {
      if (Config.getInstance().useMCAVoices) {
         return this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SURPRISE : SoundsMCA.VILLAGER_FEMALE_SURPRISE;
      } else {
         return SoundsMCA.SILENT;
      }
   }

   @Nullable
   protected final SoundEvent getAmbientSound() {
      if (Config.getInstance().useMCAVoices) {
         if (this.getAgeState() == AgeState.BABY) {
            return SoundsMCA.VILLAGER_BABY_LAUGH;
         } else if (this.isSleeping()) {
            return this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SNORE : SoundsMCA.VILLAGER_FEMALE_SNORE;
         } else if (this.getVillagerBrain().isPanicking()) {
            return this.getDeathSound();
         } else if (this.isInfected() && this.random.nextBoolean()) {
            return this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_COUGH : SoundsMCA.VILLAGER_FEMALE_COUGH;
         } else if (this.random.nextBoolean() && this.getTraits().hasTrait(Traits.SIRBEN)) {
            return SoundsMCA.SIRBEN;
         } else {
            Mood mood = this.getVillagerBrain().getMood();
            if (mood.getSoundInterval() > 0 && this.tickCount % mood.getSoundInterval() == 0) {
               return this.getGenetics().getGender() == Gender.MALE ? mood.getSoundMale() : mood.getSoundFemale();
            } else {
               return SoundsMCA.SILENT;
            }
         }
      } else {
         return Config.getInstance().useVanillaVoices ? super.getAmbientSound() : SoundsMCA.SILENT;
      }
   }

   protected final SoundEvent getHurtSound(DamageSource cause) {
      if (Config.getInstance().useMCAVoices) {
         return this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_HURT : SoundsMCA.VILLAGER_FEMALE_HURT;
      } else {
         return Config.getInstance().useVanillaVoices ? super.getHurtSound(cause) : SoundsMCA.SILENT;
      }
   }

   public final void playWelcomeSound() {
      if (Config.getInstance().useMCAVoices && !this.getVillagerBrain().isPanicking() && this.getAgeState() != AgeState.BABY) {
         this.playSound(
            this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_GREET : SoundsMCA.VILLAGER_FEMALE_GREET,
            this.getSoundVolume(),
            this.getVoicePitch()
         );
      }
   }

   public final void playSurprisedSound() {
      if (Config.getInstance().useMCAVoices) {
         this.playSound(this.getSurprisedSound(), this.getSoundVolume(), this.getVoicePitch());
      }
   }

   public SoundEvent getNotifyTradeSound() {
      if (Config.getInstance().useMCAVoices) {
         return this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_YES : SoundsMCA.VILLAGER_FEMALE_YES;
      } else {
         return Config.getInstance().useVanillaVoices ? super.getNotifyTradeSound() : SoundsMCA.SILENT;
      }
   }

   public SoundEvent getNoSound() {
      if (Config.getInstance().useMCAVoices) {
         return this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_NO : SoundsMCA.VILLAGER_FEMALE_NO;
      } else {
         return Config.getInstance().useVanillaVoices ? SoundEvents.VILLAGER_NO : SoundsMCA.SILENT;
      }
   }

   protected SoundEvent getTradeUpdatedSound(boolean sold) {
      if (Config.getInstance().useMCAVoices) {
         return sold ? this.getNotifyTradeSound() : this.getNoSound();
      } else {
         return Config.getInstance().useVanillaVoices ? super.getTradeUpdatedSound(sold) : SoundsMCA.SILENT;
      }
   }

   public void playCelebrateSound() {
      if (Config.getInstance().useMCAVoices) {
         this.playSound(
            this.getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_CELEBRATE : SoundsMCA.VILLAGER_FEMALE_CELEBRATE,
            this.getSoundVolume(),
            this.getVoicePitch()
         );
      } else if (Config.getInstance().useVanillaVoices) {
         super.playCelebrateSound();
      } else {
         this.playSound(SoundsMCA.SILENT, this.getSoundVolume(), this.getVoicePitch());
      }
   }

   public float getVoicePitch() {
      float r = (this.random.nextFloat() - 0.5F) * 0.05F;
      float g = (this.genetics.getGene(Genetics.VOICE) - 0.5F) * 0.3F;
      float a = Mth.lerp(AgeState.getDelta(this.tickCount), this.getAgeState().getPitch(), this.getAgeState().getNext().getPitch());
      return a + r + g;
   }

   public final Component getDisplayName() {
      Component name = super.getDisplayName();
      if (this.getVillagerBrain() != null) {
         MoveState state = this.getVillagerBrain().getMoveState();
         if (state != MoveState.MOVE) {
            name = name.plainCopy().append(" (").append(state.getName()).append(")");
         }

         Chore chore = this.getVillagerBrain().getCurrentJob();
         if (chore != Chore.NONE) {
            name = name.plainCopy().append(" (").append(chore.getName()).append(")");
         }
      }

      if (this.isInfected()) {
         return name.plainCopy().withStyle(ChatFormatting.GREEN);
      } else {
         return (Component)(this.getProfession() == ProfessionsMCA.OUTLAW ? name.plainCopy().withStyle(ChatFormatting.RED) : name);
      }
   }

   public void setCustomName(@Nullable Component name) {
      super.setCustomName(name);
      if (name != null) {
         this.setName(name.getString());
      }
   }

   @Override
   public float getInfectionProgress() {
      return this.getTrackedValue(INFECTION_PROGRESS);
   }

   @Override
   public void setInfectionProgress(float progress) {
      this.setTrackedValue(INFECTION_PROGRESS, progress);
   }

   @Override
   public void playSpeechEffect() {
      if (this.isSpeechImpaired()) {
         this.playSound(SoundEvents.ZOMBIE_AMBIENT, this.getSoundVolume(), this.getVoicePitch());
      }
   }

   public void addParticlesAroundSelf(ParticleOptions parameters) {
      super.addParticlesAroundSelf(parameters);
   }

   @Nullable
   public AbstractContainerMenu createMenu(int i, Inventory playerInventory, Player playerEntity) {
      return ChestMenu.threeRows(i, playerInventory, this.inventory);
   }

   @Override
   public VillagerDimensions getVillagerDimensions() {
      return this.dimensions;
   }

   @Override
   public boolean setAgeState(AgeState state) {
      AgeState previousState = this.getAgeState();
      boolean changed = VillagerLike.super.setAgeState(state);
      if (!changed && previousState != AgeState.UNASSIGNED) {
         return false;
      } else {
         if (!this.level().isClientSide()) {
            if (changed) {
               this.relations
                  .getParents()
                  .filter(ServerPlayer.class::isInstance)
                  .map(ServerPlayer.class::cast)
                  .forEach(e -> CriterionMCA.CHILD_AGE_STATE_CHANGE.trigger(e, state.name()));
               if (state == AgeState.ADULT) {
                  this.relations
                     .getParents()
                     .filter(Player.class::isInstance)
                     .map(Player.class::cast)
                     .forEach(p -> this.sendEventMessage(Component.translatable("notify.child.grownup", new Object[]{this.getName()}), p));
               }

               this.refreshBrain((ServerLevel)this.level());
            }

            this.randomizeClothes();
         }

         return changed;
      }
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> par) {
      if (this.getTypeDataManager().isParam(AGE_STATE, par)
         || this.getTypeDataManager().isParam(Genetics.SIZE.getParam(), par)
         || this.getTypeDataManager().isParam(Genetics.WIDTH.getParam(), par)) {
         this.refreshDimensions();
      }

      super.onSyncedDataUpdated(par);
   }

   public SimpleContainer getInventory() {
      return this.inventory;
   }

   public void setInventory(UpdatableInventory inventory) {
      CompoundTag nbt = new CompoundTag();
      InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);
      InventoryUtils.readFromNBT(this.registryAccess(), this.inventory, nbt);
   }

   public void moveTowards(BlockPos pos, float speed, int closeEnoughDist) {
      this.brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, closeEnoughDist));
      this.lookAt(pos);
   }

   public void moveTowards(BlockPos pos, float speed) {
      this.moveTowards(pos, speed, 1);
   }

   public void moveTowards(BlockPos pos) {
      this.moveTowards(pos, 0.5F);
   }

   public void lookAt(BlockPos pos) {
      this.brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
   }

   public void handleEntityEvent(byte id) {
      switch (id) {
         case 15:
            this.level().addAlwaysVisibleParticle(ParticleTypesMCA.NEG_INTERACTION, true, this.getX(), this.getEyeY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            break;
         case 16:
            this.level().addAlwaysVisibleParticle(ParticleTypesMCA.POS_INTERACTION, true, this.getX(), this.getEyeY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            break;
         case 17:
            this.addParticlesAroundSelf(ParticleTypes.DAMAGE_INDICATOR);
            break;
         default:
            super.handleEntityEvent(id);
      }
   }

   public void onInvChange(Container inventoryFromListener) {
   }

   @Nullable
   public <T extends Mob> T convertTo(EntityType<T> type, boolean keepInventory) {
      this.residency.leaveHome();
      EntityType<T> targetType = type;
      if (!this.isRemoved() && type == EntityType.ZOMBIE_VILLAGER) {
         targetType = (EntityType<T>)this.getGenetics().getGender().getZombieType();
      }

      T mob = (T)super.convertTo(targetType, ConversionParams.single(this, keepInventory, keepInventory), EntitySpawnReason.CONVERSION, converted -> {});
      if (mob instanceof VillagerLike<?> zombie) {
         zombie.copyVillagerAttributesFrom(this);
      }

      if (mob instanceof ZombieVillager zombie) {
         ServerLevel serverLevel = (ServerLevel)this.level();
         zombie.finalizeSpawn(
            serverLevel, serverLevel.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.CONVERSION, new ZombieGroupData(false, true)
         );
         zombie.setVillagerData(this.getVillagerData());
         zombie.setGossips(this.getGossips().copy());
         zombie.setTradeOffers(this.getOffers().copy());
         zombie.setVillagerXp(this.getVillagerXp());
         zombie.setUUID(this.getUUID());
         zombie.setPersistenceRequired();
         this.level().levelEvent(null, 1026, this.blockPosition(), 0);
      }

      if (mob instanceof ZombieVillagerEntityMCA zombie) {
         zombie.setInventory(this.inventory);
      }

      return mob;
   }

   protected void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      CompoundTag nbt = WorldUtils.getCompoundTag(input);
      this.getTypeDataManager().load(this, nbt);
      this.relations.readFromNbt(nbt);
      this.longTermMemory.readFromNbt(nbt);
      this.playerModel = VillagerLike.PlayerModel.VALUES[nbt.getInt("PlayerModel").orElse(VillagerLike.PlayerModel.VILLAGER.ordinal())];
      this.updateAttributes();
      this.inventory.clearContent();
      InventoryUtils.readFromNBT(this.registryAccess(), this.inventory, nbt);
      if (nbt.contains("DespawnDelay")) {
         this.despawnDelay = nbt.getInt("DespawnDelay").orElse(0);
      }

      if (nbt.contains("InteractedWith")) {
         this.interactedWith = nbt.getBoolean("InteractedWith").orElse(false);
      }

      if (nbt.contains("Clothes")) {
         this.validateClothes();
      }

      if (this.getVillagerBrain().getPersonality() == Personality.UNASSIGNED) {
         this.getVillagerBrain().randomize();
      }
   }

   @Override
   public void mca$readAdditionalSaveData(ValueInput input) {
      this.readAdditionalSaveData(input);
   }

   @Override
   public void mca$addAdditionalSaveData(ValueOutput output) {
      this.addAdditionalSaveData(output);
   }

   protected final void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      CompoundTag nbt = WorldUtils.getCompoundTag(output);
      this.relations.writeToNbt(nbt);
      this.longTermMemory.writeToNbt(nbt);
      this.getTypeDataManager().save(this, nbt);
      InventoryUtils.saveToNBT(this.registryAccess(), this.inventory, nbt);
      nbt.putInt("DespawnDelay", this.despawnDelay);
      nbt.putBoolean("InteractedWith", this.interactedWith);
      if (this.interactedWith) {
         VillagerTrackerManager.update(this);
      }
   }

   @Override
   public boolean isHostile() {
      return this.getProfession() == ProfessionsMCA.OUTLAW;
   }

   public boolean isFriend(EntityType<?> type) {
      return type == EntityType.IRON_GOLEM || type == EntitiesMCA.FEMALE_VILLAGER || type == EntitiesMCA.MALE_VILLAGER;
   }

   public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
      return true;
   }

   public void setChargingCrossbow(boolean charging) {
   }

   public void onCrossbowAttackPerformed() {
   }

   public void thunderHit(ServerLevel world, LightningBolt lightning) {
      this.getTraits().addTrait(Traits.ELECTRIFIED);
   }

   public void performRangedAttack(LivingEntity target, float pullProgress) {
      this.setTarget(target);
      this.attackedEntity(target);
      if (this.isHolding(Items.CROSSBOW)) {
         this.performCrossbowAttack(this, 1.75F);
      } else if (this.isHolding(Items.BOW)) {
         ItemStack bow = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
         ItemStack arrow = this.getProjectile(bow);
         AbstractArrow persistentProjectileEntity = ProjectileUtil.getMobArrow(this, arrow, pullProgress, bow);
         double x = target.getX() - this.getX();
         double y = target.getY(0.3333333333333333) - persistentProjectileEntity.getY();
         double z = target.getZ() - this.getZ();
         double vel = Math.sqrt(x * x + z * z);
         persistentProjectileEntity.shoot(x, y + vel * 0.2F, z, 1.6F, 3.0F);
         this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
         this.level().addFreshEntity(persistentProjectileEntity);
      }
   }

   public ItemStack getProjectile(ItemStack stack) {
      if (stack.getItem() instanceof ProjectileWeaponItem weapon) {
         Predicate<ItemStack> predicate = weapon.getSupportedHeldProjectiles();
         ItemStack itemStack = ProjectileWeaponItem.getHeldProjectile(this, predicate);
         return itemStack.isEmpty() ? new ItemStack(Items.ARROW) : itemStack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   private void tickDespawnDelay() {
      if (this.despawnDelay > 0 && !this.isTrading() && --this.despawnDelay == 0) {
         if (!this.getRelationships().getPartner().isPresent()
            && !this.getVillagerBrain()
               .getMemories()
               .values()
               .stream()
               .anyMatch(m -> this.random.nextInt(Config.getInstance().marriageHeartsRequirement) < m.getHearts())) {
            this.discard();
         } else {
            this.setProfession(ProfessionsMCA.value(VillagerProfession.NONE));
            this.setDespawnDelay(0);
         }
      }
   }

   public int getDespawnDelay() {
      return this.despawnDelay;
   }

   public void setDespawnDelay(int despawnDelay) {
      this.despawnDelay = despawnDelay;
   }

   public void makeMercenary() {
      this.setProfession(ProfessionsMCA.MERCENARY);
      this.inventory.addItem(new ItemStack(Items.IRON_SWORD));
      this.inventory.addItem(new ItemStack(Items.IRON_AXE));
      this.inventory.addItem(new ItemStack(Items.IRON_PICKAXE));
      this.inventory.addItem(new ItemStack(Items.IRON_HOE));
      this.inventory.addItem(new ItemStack(Items.FISHING_ROD));
      this.inventory.addItem(new ItemStack(Items.BREAD, 16));
   }

   public void customLevelUp() {
      this.setVillagerData(this.getVillagerData().withLevel(this.getVillagerData().level() + 1));
      this.updateTrades((ServerLevel)this.level());
   }
}
