package net.conczin.mca.entity;

import com.mojang.serialization.Dynamic;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.datafix.McaDataFixers;
import net.conczin.mca.entity.ai.*;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.chatAI.ChatAI;
import net.conczin.mca.entity.ai.chatAI.ChatAIContext;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.relationship.*;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.conczin.mca.registry.*;
import net.conczin.mca.resources.Names;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillagerTrackerManager;
import net.conczin.mca.util.InventoryUtils;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;


public class VillagerEntityMCA extends Villager implements VillagerLike<VillagerEntityMCA>, MenuProvider, CompassionateEntity<BreedableRelationship>, CrossbowAttackMob {
    private static final CDataParameter<Float> INFECTION_PROGRESS = CParameter.create("InfectionProgress", 0.0f);
    private static final CDataParameter<Integer> GROWTH_AMOUNT = CParameter.create("GrowthAmount", -AgeState.getMaxAge());
    private static final float VEHICLE_ATTACHMENT_Y = 0.6F;
    public static final int MAX_NICKNAME_LENGTH = 32;
    static final String CHAT_AI_PROMPT_KEY = "ChatAIPrompt";
    static final String NICKNAMES_KEY = "nicknames";
    private static final CDataManager<VillagerEntityMCA> DATA = createTrackedData(VillagerEntityMCA.class).build();
    private static final int RECALCULATE_DIMENSIONS_EVERY_N_TICKS = 100;
    public final ConversationManager conversationManager = new ConversationManager(this);
    private String chatAIPrompt = "";
    final ResourceLocation EXTRA_HEALTH_EFFECT_ID = MCA.locate("trait_health");
    private final VillagerBrain<VillagerEntityMCA> mcaBrain = new VillagerBrain<>(this);
    private final LongTermMemory longTermMemory = new LongTermMemory(this);
    private final Genetics genetics = new Genetics(this);
    private final Traits traits = new Traits(this);
    private final Residency residency = new Residency(this);
    private final BreedableRelationship relations = new BreedableRelationship(this);
    private final VillagerCommandHandler interactions = new VillagerCommandHandler(this);
    private final UpdatableInventory inventory = new UpdatableInventory(27);
    private final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.UNASSIGNED);
    private final ArcherMoveControl archerMoveControl;
    long lastCooldown = 0L;
    private PlayerModel playerModel;
    private int despawnDelay;
    private int burned;
    private long lastHit = 0;
    private int prevGrowthAmount;
    private boolean interactedWith;
    private int lastAppliedHealthLevel = Integer.MIN_VALUE;
    private double lastAppliedHealthBonus = Double.NaN;
    private boolean recoveryFoodUseActive;
    private boolean completingRecoveryFoodUse;
    private boolean recoveryFoodFromInventory;
    private int recoveryFoodUseTicks;
    private ItemStack recoveryPreviousMainHand = ItemStack.EMPTY;
    private final Map<UUID, String> nicknames = new HashMap<>();

    public VillagerEntityMCA(EntityType<VillagerEntityMCA> type, Level w, Gender gender) {
        super(type, w);
        inventory.addListener(this::onInvChange);
        this.archerMoveControl = new ArcherMoveControl(this);
        this.moveControl = this.archerMoveControl;
        genetics.setGender(gender);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(PathType.TRAPDOOR, 8.0F);
        this.setPathfindingMalus(PathType.DANGER_TRAPDOOR, 8.0F);
        this.setPathfindingMalus(PathType.WATER, 16.0F);
    }

    public ArcherMoveControl getArcherMoveControl() {
        return this.archerMoveControl;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(Class<E> type) {
        return VillagerLike.createTrackedData(type).addAll(INFECTION_PROGRESS, GROWTH_AMOUNT)
                .add(Residency::createTrackedData)
                .add(BreedableRelationship::createTrackedData);
    }

    private static boolean canEat(ItemStack i) {
        FoodProperties foodProperties = i.get(DataComponents.FOOD);
        return foodProperties != null
               && foodProperties.nutrition() > 0
               && foodProperties.effects().stream().noneMatch(e -> StatusEffectDangerSet.IS_DANGER.contains(e.effect().getEffect()));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new MCAGroundPathNavigation(this, level);
    }

    @Override
    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 input, float friction) {
        Vec3 movement = super.handleRelativeFrictionAndCalculateMovement(input, friction);
        if (getNavigation() instanceof MCAGroundPathNavigation navigation) {
            double controlledY = navigation.getControlledClimbableVelocity();
            if (!Double.isNaN(controlledY)) {
                return new Vec3(movement.x(), controlledY, movement.z());
            }
        }
        return movement;
    }

    @Override
    public void setJumping(boolean jumping) {
        boolean navigationControlsClimb = this.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isControllingClimbable();
        super.setJumping(jumping && !navigationControlsClimb);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0f)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0f)
                .add(Attributes.MAX_HEALTH, Config.getInstance().villagerMaxHealth)
                .add(Attributes.FOLLOW_RANGE, Config.getInstance().getVillagerFollowRange());
    }

    @Override
    public CDataManager<VillagerEntityMCA> getTypeDataManager() {
        return DATA;
    }

    @Override
    public PlayerModel getPlayerModel() {
        return playerModel;
    }

    @Override
    public boolean isBurned() {
        return burned > 0;
    }

    @Override
    public void restock() {
        super.restock();

        if (!level().isClientSide) {
            Optional<Village> village = residency.getHomeVillage();
            if (village.isPresent() && Config.getInstance().villagerRestockNotification) {
                village.get().broadCastMessage((ServerLevel) level(), "events.restock", getName().getString());
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        getTypeDataManager().register(builder);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return VillagerTasksMCA.initializeTasks(this, VillagerTasksMCA.createProfile().makeBrain(dynamic));
    }

    @Override
    public void refreshBrain(ServerLevel world) {
        Brain<VillagerEntityMCA> brain = getMCABrain();
        brain.stopAll(world, this);
        //copyWithoutBehaviors will copy the memories of the old brain to the new brain
        this.brain = brain.copyWithoutBehaviors();
        VillagerTasksMCA.initializeTasks(this, getMCABrain());
    }

    @SuppressWarnings("unchecked")
    public Brain<VillagerEntityMCA> getMCABrain() {
        return (Brain<VillagerEntityMCA>) brain;
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
    public HumanoidArm getMainArm() {
        return getTraits().hasTrait(Traits.LEFT_HANDED) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    @Override
    public BreedableRelationship getRelationships() {
        return relations;
    }

    @Override
    public VillagerBrain<?> getVillagerBrain() {
        return mcaBrain;
    }

    public LongTermMemory getLongTermMemory() {
        return longTermMemory;
    }

    public Residency getResidency() {
        return residency;
    }

    @Override
    public VillagerCommandHandler getInteractions() {
        return interactions;
    }

    @Override
    protected Component getTypeName() {
        return getProfessionText();
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);

        initialize(spawnType);

        setAgeState(AgeState.byCurrentAge(getAge()));

        FamilyTreeNode entry = getRelationships().getFamilyEntry();
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
        return getVillagerData().getProfession();
    }

    public final void setProfession(VillagerProfession profession) {
        setVillagerData(getVillagerData().setProfession(profession));
        refreshBrain((ServerLevel) level());
    }

    @Override
    public ResourceLocation getProfessionId() {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(getProfession());
    }

    @Override
    public boolean isProfessionImportant() {
        return ProfessionsMCA.IS_IMPORTANT.contains(getProfession());
    }

    @Override
    public boolean requiresHome() {
        return !ProfessionsMCA.NEEDS_NO_HOME.contains(getProfession()) && getDespawnDelay() <= 0;
    }

    @Override
    public boolean canTradeWithProfession() {
        return !ProfessionsMCA.CAN_NOT_TRADE.contains(getProfession()) || (offers != null && !offers.isEmpty());
    }

    @Override
    public void setVillagerData(VillagerData data) {
        boolean hasChanged = !level().isClientSide && getProfession() != data.getProfession() && data.getProfession() != ProfessionsMCA.OUTLAW;
        super.setVillagerData(data);
        if (hasChanged) {
            randomizeClothes();
            getRelationships().getFamilyEntry().setProfession(data.getProfession());
        }
    }

    @Override
    public void setBaby(boolean isBaby) {
        setAge(isBaby ? -AgeState.getMaxAge() : 0);
    }

    @Override
    public void setAge(int age) {
        super.setAge(age);

        // high quality iguana tweaks reborn LivestockSlowdownFeature fix
        if (age != -2) {
            setTrackedValue(GROWTH_AMOUNT, age);
            setAgeState(AgeState.byCurrentAge(age));

            AgeState current = getAgeState();

            AgeState next = current.getNext();
            if (current != next) {
                dimensions.interpolate(current, next, AgeState.getDelta(age));
            } else {
                dimensions.set(current);
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hurt = super.doHurtTarget(target);
        if (hurt) {
            attackedEntity(target);
        }
        return hurt;
    }

    public void onRangedAttackLanded(Entity target) {
        attackedEntity(target);
    }

    private void attackedEntity(Entity target) {
        if (target instanceof Player player) {
            pardonPlayers(player);
        }
    }

    /**
     * decrease the personal bounty counter by one
     */
    private void pardonPlayers() {
        pardonPlayers(1);
    }

    public void pardonPlayers(int amount) {
        int bounty = getSmallBounty();
        if (bounty <= amount) {
            getBrain().eraseMemory(MemoryModuleTypeMCA.SMALL_BOUNTY);
            getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            getBrain().eraseMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER);
        } else {
            getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, bounty - amount);
        }
    }

    private void pardonPlayers(Player attacker) {
        pardonPlayers();
        int bounty = getSmallBounty();
        if (bounty <= getMaxWarnings(attacker)) {
            getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    public boolean canInteractWithItemStackInHand(ItemStack stack) {
        return stack.getItem() != ItemsMCA.VILLAGER_EDITOR
               && stack.getItem() != ItemsMCA.NEEDLE_AND_THREAD
               && stack.getItem() != ItemsMCA.COMB
               && stack.getItem() != ItemsMCA.POTION_OF_FEMININITY
               && stack.getItem() != ItemsMCA.POTION_OF_MASCULINITY;
    }

    @Override
    public final InteractionResult interactAt(Player player, Vec3 pos, @NotNull InteractionHand hand) {
        // This allows hitbox interactions to be ignored if the player is carrying a child villager.
        if (getVehicle() != null && getVehicle().equals(player)) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        boolean isOnBlacklist = Config.getInstance().villagerInteractionItemBlacklist.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (hand.equals(InteractionHand.MAIN_HAND) && !isOnBlacklist && !stack.is(TagsMCA.Items.VILLAGER_EGGS) && canInteractWithItemStackInHand(stack) && !getVillagerBrain().isPanicking()) {
            //make sure dialogueType is synced in case the client needs it
            getDialogueType(player);

            if (player.isShiftKeyDown()) {
                if (!level().isClientSide && canTradeWithProfession()) {
                    getInteractions().stopInteracting();
                    startTrading(player);
                }
            } else {
                playWelcomeSound();
                interactedWith = true;
                if (player instanceof ServerPlayer serverPlayer) {
                    ChatAI.selectVillagerForConversation(serverPlayer, this);
                }
                return interactions.interactAt(player, pos, hand);
            }
        }
        return super.interactAt(player, pos, hand);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // This allows hitbox interactions to be ignored if the player is carrying a child villager.
        if (getVehicle() != null && getVehicle().equals(player)) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(TagsMCA.Items.VILLAGER_EGGS) && isAlive() && !isTrading() && !isSleeping() && canInteractWithItemStackInHand(stack) && !getVillagerBrain().isPanicking()) {
            if (isBaby()) {
                setUnhappy();
            } else if (!level().isClientSide) {
                boolean hasOffers = hasTradeOffers();
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!hasOffers && !level().isClientSide) {
                        setUnhappy();
                    }

                    player.awardStat(Stats.TALKED_TO_VILLAGER);
                }

                if (hasOffers && !level().isClientSide) {
                    startTrading(player);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    public boolean hasTradeOffers() {
        return !getOffers().isEmpty();
    }

    @Override
    public MerchantOffers getOffers() {
        MerchantOffers offers = super.getOffers();
        int previousSize = offers.size();
        offers.removeIf(offer -> offer.getResult().isEmpty());

        int removed = previousSize - offers.size();
        if (removed > 0) {
            MCA.LOGGER.warn("Removed {} invalid villager trade(s) with empty result for villager {} (profession {}, level {})",
                    removed, getUUID(), getProfessionId(), getVillagerData().getLevel());
        }
        return offers;
    }

    @Override
    public VillagerEntityMCA getBreedOffspring(ServerLevel level, AgeableMob partner) {
        VillagerEntityMCA child = partner instanceof VillagerEntityMCA partnerVillager
                ? relations.getPregnancy().createChild(Gender.getRandom(), partnerVillager)
                : relations.getPregnancy().createChild(Gender.getRandom());

        child.setVillagerData(child.getVillagerData().setType(getRandomType(partner)));

        child.finalizeSpawn(level, level.getCurrentDifficultyAt(child.blockPosition()), MobSpawnType.BREEDING, null);
        return child;
    }

    @Override
    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
        super.onOffspringSpawnedFromEgg(player, child);

        if (child instanceof VillagerEntityMCA villager) {
            villager.setCustomName(Component.literal(villager.getRelationships().getFamilyEntry().getName()));
        }
    }

    private VillagerType getRandomType(AgeableMob partner) {
        double d = random.nextDouble();

        if (d < 0.5D) {
            return VillagerType.byBiome(level().getBiome(blockPosition()));
        }

        if (d < 0.75D) {
            return getVillagerData().getType();
        }

        return ((Villager) partner).getVillagerData().getType();
    }

    @Override
    public final boolean hurt(DamageSource source, float damageAmount) {
        // no baby squishes
        if (getVehicle() instanceof Player) {
            return super.hurt(source, 0.0f);
        }

        // you can't hit babies!
        // TODO: Verify the `isUnblockable` replacement for 1.19.4, ensure same behavior
        if (!Config.getInstance().canHurtBabies && !source.is(DamageTypeTags.BYPASSES_SHIELD) && getAgeState() == AgeState.BABY) {
            if (source.getEntity() instanceof Player && requestCooldown()) {
                sendEventMessage(Component.translatable("villager.baby_hit"));
            }
            return super.hurt(source, 0.0f);
        }

        // Guards take 50% less damage
        if (getProfession() == ProfessionsMCA.GUARD) {
            damageAmount *= 0.5f;
        }

        if (getTraits().hasTrait(Traits.TOUGH)) {
            damageAmount *= 0.75f;
        }

        if (!level().isClientSide) {
            //scream and loose hearts
            if (source.getEntity() instanceof Player player) {
                if (level().getGameTime() - lastHit > 40) {
                    lastHit = level().getGameTime();
                    if (!isGuard() && requestCooldown()) {
                        if (getHealth() < getMaxHealth() / 2) {
                            sendChatMessage(player, "villager.badly_hurt");
                        } else {
                            sendChatMessage(player, "villager.hurt");
                        }
                    }
                }

                //loose hearts, the weaker the villager, the more it is scared. The first hit might be an accident.
                int trustIssues = (int) ((1.0 - getHealth() / getMaxHealth() * 0.75) * (3.0 + 2.0 * damageAmount));
                getVillagerBrain().getMemoriesForPlayer(player).modHearts(-trustIssues);
            }

            //infect the villager
            if (source.getDirectEntity() instanceof Zombie
                && getProfession() != ProfessionsMCA.GUARD
                && Config.getInstance().enableInfection
                && random.nextFloat() < Config.getInstance().zombieBiteInfectionChance
                && random.nextFloat() > (getVillagerData().getLevel() - 1) * Config.getInstance().infectionChanceDecreasePerLevel
                && (getResidency().getHomeVillage().filter(v -> v.hasBuilding("infirmary")).isEmpty() || random.nextBoolean())) {
                setInfected(true);
                sendChatToAllAround("villager.bitten");
                MCA.LOGGER.info("{} has been infected", getName());
            }
        }

        Entity attacker = source.getEntity();

        // Notify the surrounding guards when a villager is attacked. Yoinks!
        if (!level().isClientSide && attacker instanceof LivingEntity livingEntity && !isHostile() && !isFriend(attacker.getType())) {
            int victimBountyBeforeHit = getSmallBounty();

            // remember the specific attacker
            getBrain().setMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER, Optional.of(livingEntity));
            getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, victimBountyBeforeHit + 1);

            Vec3 pos = position();
            level().getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos, pos).inflate(32)).forEach(v -> {
                if (this.distanceToSqr(v) <= (v.getTarget() == null ? 1024 : 64)) {
                    if (attacker instanceof Player player) {
                        int bounty = v == this ? victimBountyBeforeHit : v.getSmallBounty();
                        if (v.isGuard()) {
                            int maxWarning = v.getMaxWarnings(player);
                            if (bounty > maxWarning) {
                                // ok, that was enough
                                v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity);
                            } else if (bounty == 0 || bounty == maxWarning) {
                                // just a warning
                                v.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                                v.sendChatMessage(player, "villager.warning");
                            }
                            v.getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, bounty + 1);
                        }
                    } else if (v.isGuard()) {
                        // non players get attacked straight away
                        v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity);
                    }
                }
            });
        }

        // Iron Golem got his revenge, now chill
        if (attacker instanceof IronGolem golem) {
            golem.stopBeingAngry();
            damageAmount *= 0.0f;
        }

        return super.hurt(source, damageAmount);
    }

    private boolean requestCooldown() {
        if (level().getGameTime() - lastCooldown > 100) {
            lastCooldown = level().getGameTime();
            return true;
        } else {
            return false;
        }
    }

    public boolean isGuard() {
        return getProfession() == ProfessionsMCA.GUARD || getProfession() == ProfessionsMCA.ARCHER;
    }

    public int getSmallBounty() {
        return Objects.requireNonNull(getBrain().getMemoryInternal(MemoryModuleTypeMCA.SMALL_BOUNTY)).orElse(0);
    }

    public boolean isHitBy(ServerPlayer player) {
        return Objects.requireNonNull(getBrain().getMemoryInternal(MemoryModuleTypeMCA.HIT_BY_PLAYER)).filter(v -> v == player).isPresent();
    }

    private int getMaxWarnings(Player attacker) {
        return getVillagerBrain().getMemoriesForPlayer(attacker).getHearts() / Math.max(1, Config.getInstance().heartsForPardonHit);
    }

    @Override
    public void aiStep() {
        int oldAge = getAge();
        updateSwingTime();

        super.aiStep();

        if (getTraits().hasTrait(Traits.NO_AGING)) {
            setAge(oldAge);
        }

        burned--;
        if (isOnFire()) {
            burned = Config.getInstance().burnedClothingTickLength;
        }
        if (burned > 0) {
            spawnBurntParticles();
        }

        if (!level().isClientSide) {
            tickRecoveryFoodUse();

            if (tickCount % 200 == 0
                && getHealth() < getMaxHealth()
                && canRecoverHealthNow()) {
                if (!startRecoveryFoodUse()) {
                    heal(1); // natural regeneration
                }
            }

            tickDespawnDelay();

            residency.tick();

            relations.tick(tickCount);

            inventory.update(this);

            if (tickCount % Config.getInstance().pardonPlayerTicks == 0) {
                pardonPlayers();
            }

            // Brain and pregnancy depend on the above states, so we tick them last
            // Every 1 second
            mcaBrain.think();

            // pop a item from the desaturation queue
            if (tickCount % Config.getInstance().giftDesaturationReset == 0) {
                getRelationships().getGiftSaturation().pop();
            }

            // track the position from time to time
            if (interactedWith && tickCount % Config.getInstance().trackVillagerPositionEveryNTicks == 0) {
                VillagerTrackerManager.update(this);
            }
        }
    }

    protected boolean findAndEquipToMain(Predicate<ItemStack> predicate) {
        int slot = InventoryUtils.getFirstSlotContainingItem(getInventory(), predicate);

        if (slot > -1) {
            ItemStack replacement = getInventory().getItem(slot).split(1);

            if (!replacement.isEmpty()) {
                setItemInHand(getDominantHand(), replacement);
                return true;
            }
        }

        return false;
    }

    public boolean isUsingRecoveryFood() {
        return recoveryFoodUseActive;
    }

    private boolean canRecoverHealthNow() {
        return !recoveryFoodUseActive && !isUsingItem() && canContinueRecoveryFoodUse();
    }

    private void tickRecoveryFoodUse() {
        if (!recoveryFoodUseActive) {
            return;
        }

        if (!canContinueRecoveryFoodUse()) {
            stopUsingItem();
            return;
        }

        recoveryFoodUseTicks++;
        ItemStack food = getItemInHand(getDominantHand());
        if (!food.isEmpty()
            && level() instanceof ServerLevel serverLevel
            && recoveryFoodUseTicks > food.getUseDuration(this) * 7 / 32
            && recoveryFoodUseTicks % 4 == 0) {
            spawnRecoveryFoodParticles(serverLevel, food, 4);
        }
    }

    private boolean canContinueRecoveryFoodUse() {
        return !isInRecoveryDanger();
    }

    private boolean isInRecoveryDanger() {
        return getVillagerBrain().isPanicking()
               || hasActiveRecoveryThreat(MemoryModuleType.ATTACK_TARGET)
               || hasActiveRecoveryThreat(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
    }

    private boolean hasActiveRecoveryThreat(MemoryModuleType<? extends LivingEntity> memoryType) {
        return getBrain().getMemoryInternal(memoryType)
                .filter(entity -> entity.isAlive() && !entity.isRemoved())
                .isPresent();
    }

    private boolean startRecoveryFoodUse() {
        ItemStack mainHandFood = getMainHandItem();
        FoodProperties mainHandFoodProperties = mainHandFood.get(DataComponents.FOOD);
        if (canEat(mainHandFood)) {
            return startRecoveryFoodUse(mainHandFoodProperties, false, ItemStack.EMPTY);
        }

        int slot = InventoryUtils.getFirstSlotContainingItem(getInventory(), VillagerEntityMCA::canEat);
        if (slot < 0) {
            return false;
        }

        ItemStack food = getInventory().getItem(slot);
        FoodProperties foodProperties = food.get(DataComponents.FOOD);
        if (!canEat(food)) {
            return false;
        }

        ItemStack previousMainHand = getMainHandItem().copy();
        ItemStack replacement = food.split(1);
        if (replacement.isEmpty()) {
            return false;
        }

        setItemInHand(getDominantHand(), replacement);
        return startRecoveryFoodUse(foodProperties, true, previousMainHand);
    }

    private boolean startRecoveryFoodUse(FoodProperties foodProperties, boolean fromInventory, ItemStack previousMainHand) {
        if (foodProperties == null) {
            return false;
        }

        recoveryFoodUseActive = true;
        recoveryFoodFromInventory = fromInventory;
        recoveryFoodUseTicks = 0;
        recoveryPreviousMainHand = previousMainHand;
        startUsingItem(getDominantHand());

        if (!isUsingItem()) {
            finishRecoveryFoodUse();
            return false;
        }

        return true;
    }

    private void finishRecoveryFoodUse() {
        if (recoveryFoodFromInventory) {
            ItemStack remainder = getMainHandItem();
            if (!remainder.isEmpty()) {
                ItemStack leftover = getInventory().addItem(remainder);
                if (!leftover.isEmpty()) {
                    spawnAtLocation(leftover, 0.0F);
                }
            }
            setItemInHand(getDominantHand(), recoveryPreviousMainHand);
        }

        recoveryFoodUseActive = false;
        completingRecoveryFoodUse = false;
        recoveryFoodFromInventory = false;
        recoveryFoodUseTicks = 0;
        recoveryPreviousMainHand = ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        super.tick();

        // update visual age
        int age = getTrackedValue(GROWTH_AMOUNT);
        if (age / RECALCULATE_DIMENSIONS_EVERY_N_TICKS != prevGrowthAmount / RECALCULATE_DIMENSIONS_EVERY_N_TICKS) {
            prevGrowthAmount = age;
            refreshDimensions();
        }

        if (level().isClientSide) {
            // procreate anim
            if (relations.isProcreating()) {
                yHeadRot += 50;
            }

            // mood particles
            Mood mood = mcaBrain.getMood();
            if (mood.getParticle() != null && this.tickCount % mood.getParticleInterval() == 0 && level().random.nextBoolean()) {
                addParticlesAroundSelf(mood.getParticle());
            }
        } else {
            // infection
            float infection = getInfectionProgress();
            if (infection > 0 && this.tickCount % 20 == 0) {
                if (infection > FEVER_THRESHOLD && level().random.nextInt(25) == 0) {
                    sendChatToAllAround("villager.sickness");
                }

                infection += 1.0f / Config.getInstance().infectionTime;
                setInfectionProgress(infection);

                if (infection > 1.0f) {
                    convertTo(EntityType.ZOMBIE_VILLAGER, false);
                    discard();
                }
            }

            // panic screams
            if (this.tickCount % 90 == 0 && mcaBrain.isPanicking()) {
                sendChatToAllAround("villager.scream");
            }

            // sirben noises
            if (this.tickCount % 60 == 0 && random.nextInt(50) == 0 && traits.hasTrait(Traits.SIRBEN)) {
                sendChatToAllAround("sirben");
            }

            updateLevelHealthBonus();

            //twice a day, randomize the mood a bit
            if (this.tickCount % 12000 == 0) {
                int base = Math.round(mcaBrain.getMoodValue() / 12.0f);
                int value = random.nextInt(7) - 3;
                mcaBrain.modifyMoodValue(value - base);
            }
        }
    }

    private void updateLevelHealthBonus() {
        int level = this.getVillagerData().getLevel() - 1;
        double bonus = Config.getInstance().villagerHealthBonusPerLevel * level;

        if (level == lastAppliedHealthLevel && bonus == lastAppliedHealthBonus) {
            return;
        }

        lastAppliedHealthLevel = level;
        lastAppliedHealthBonus = bonus;

        AttributeInstance instance = this.getAttributes().getInstance(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }

        instance.removeModifier(EXTRA_HEALTH_EFFECT_ID);

        if (bonus != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(
                    EXTRA_HEALTH_EFFECT_ID,
                    bonus,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    @Override
    public void refreshDimensions() {
        AgeState current = getAgeState();
        AgeState next = current.getNext();

        // either interpolate or set if final age is reached
        if (next != current) {
            dimensions.interpolate(current, next, AgeState.getDelta(getTrackedValue(GROWTH_AMOUNT)));
        } else {
            dimensions.set(current);
        }

        // todo calculateDimensions call move, move sets some flags, but since it's a "fake" move no collision happen
        // without collision the pathfinder skips the frame, causing children to not move
        // there are more flags affected, none of them seem to affect the game tho
        boolean oldOnGround = this.onGround();
        super.refreshDimensions();
        this.setOnGround(oldOnGround);
    }

    @Override
    protected void completeUsingItem() {
        boolean completedRecoveryFoodUse = recoveryFoodUseActive
                                           && isUsingItem()
                                           && getUsedItemHand() == getDominantHand();

        completingRecoveryFoodUse = completedRecoveryFoodUse;
        super.completeUsingItem();
        completingRecoveryFoodUse = false;

        if (completedRecoveryFoodUse) {
            finishRecoveryFoodUse();
        }
    }

    @Override
    public void stopUsingItem() {
        boolean interruptedRecoveryFoodUse = recoveryFoodUseActive && !completingRecoveryFoodUse;
        super.stopUsingItem();

        if (interruptedRecoveryFoodUse) {
            finishRecoveryFoodUse();
        }
    }

    private void spawnRecoveryFoodParticles(ServerLevel level, ItemStack food, int count) {
        RandomSource random = getRandom();
        ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, food.copy());

        for (int i = 0; i < count; i++) {
            Vec3 velocity = new Vec3(
                    (random.nextFloat() - 0.5) * 0.1,
                    random.nextFloat() * 0.1 + 0.1,
                    0.0
            );
            velocity = velocity.xRot(-getXRot() * ((float) Math.PI / 180f));
            velocity = velocity.yRot(-getYRot() * ((float) Math.PI / 180f));

            Vec3 position = new Vec3(
                    (random.nextFloat() - 0.5) * 0.3,
                    -random.nextFloat() * 0.6 - 0.3,
                    0.6
            );
            position = position.xRot(-getXRot() * ((float) Math.PI / 180f));
            position = position.yRot(-getYRot() * ((float) Math.PI / 180f));
            position = position.add(getX(), getEyeY(), getZ());

            level.sendParticles(
                    particle,
                    position.x, position.y, position.z,
                    1,
                    velocity.x, velocity.y + 0.05, velocity.z,
                    0.0
            );
        }
    }

    @Override
    public ItemStack eat(Level level, ItemStack stack, FoodProperties foodProperties) {
        heal(foodProperties.nutrition());
        return super.eat(level, stack, foodProperties);
    }

    @Override
    public void rideTick() {
        super.rideTick();

        Entity vehicle = getVehicle();

        if (vehicle instanceof PathfinderMob pathAwareEntity) {
            yBodyRot = pathAwareEntity.yBodyRot;
        }

        if (vehicle instanceof Player player) {
            List<Entity> passengers = vehicle.getPassengers();

            float yaw = -player.yBodyRot * 0.017453292F;

            boolean left = passengers.get(0) == this;
            boolean head = passengers.size() > 2 && passengers.get(2) == this;

            Vec3 offset = head ? new Vec3(0, 0.55f, 0) : new Vec3(left ? 0.4F : -0.4F, 0.05f, 0).yRot(yaw);

            // todo currently only client side
            if (isClientSide() && MCAClient.useGeneticsRenderer(vehicle.getUUID())) {
                float height = CommonVillagerModel.getVillager(vehicle).getRawVerticalScaleFactor();
                offset = offset.multiply(1.0f, height, 1.0f);
                offset = offset.add(0, (height - 1) * 1.5 - 0.7, 0);
            }

            Vec3 pos = this.position();
            this.setPosRaw(pos.x() + offset.x(), pos.y() + offset.y(), pos.z() + offset.z());

            if (vehicle.isShiftKeyDown()) {
                stopRiding();
            }
        }
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        boolean result = super.startRiding(vehicle, force);
        if (result && vehicle instanceof Player) {
            this.refreshDimensions();
        }
        return result;
    }

    @Override
    public void removeVehicle() {
        Entity vehicle = this.getVehicle();
        super.removeVehicle();
        if (vehicle instanceof Player) {
            this.refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        Entity vehicle = getVehicle();
        if (vehicle instanceof Player) {
            return SLEEPING_DIMENSIONS;
        }

        if (pose == Pose.SLEEPING) {
            return SLEEPING_DIMENSIONS;
        }

        float height = getVerticalScaleFactor() * 2.0F;
        float width = getHorizontalScaleFactor() * 0.6F;

        return EntityDimensions.scalable(width, height).withAttachments(EntityAttachments.builder()
                .attach(EntityAttachment.VEHICLE, 0.0F, getRawVerticalScaleFactor() * VEHICLE_ATTACHMENT_Y, 0.0F));
    }

    @Override
    public void die(DamageSource cause) {
        // deselect equipment as this messes with MobEntities equipment dropping
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, ItemStack.EMPTY);
        }

        //death message
        if (!level().isClientSide) {
            getResidency().getHomeVillage().flatMap(Village::getCivilRegistry).ifPresent(r -> r.addText(getCombatTracker().getDeathMessage()));
        }

        super.die(cause);

        if (level().isClientSide) {
            return;
        }

        //drop stuff
        InventoryUtils.dropAllItems(this, inventory);

        //alert family and nearby villagers
        relations.onDeath(cause);

        Optional<Village> village = residency.getHomeVillage();
        if (village.isPresent() && cause.getEntity() != null && level() instanceof ServerLevel serverLevel) {
            serverLevel.players().forEach(player -> {
                Rank relationToVillage = Tasks.getRank(village.get(), player);
                ResourceLocation causeId = EntityType.getKey(cause.getEntity().getType());
                CriterionMCA.FATE.trigger(player, causeId, relationToVillage);
            });
        }

        //move out
        residency.leaveHome();

        if (interactedWith) {
            VillagerTrackerManager.update(this);
        }
    }


    @Override
    public void teleportTo(double destX, double destY, double destZ) {
        if (isPassenger()) {
            Entity rootVehicle = getRootVehicle();
            if (rootVehicle instanceof Mob) {
                rootVehicle.teleportTo(destX, destY, destZ);
                return; // villagers can travel by teleporting, so make sure they take their mount with
            }
        }

        super.teleportTo(destX, destY, destZ);
    }

    @Override
    public SoundEvent getDeathSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SCREAM : SoundsMCA.VILLAGER_FEMALE_SCREAM;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getDeathSound();
        } else {
            return SoundsMCA.SILENT;
        }
    }

    public SoundEvent getSurprisedSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SURPRISE : SoundsMCA.VILLAGER_FEMALE_SURPRISE;
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Nullable
    @Override
    protected final SoundEvent getAmbientSound() {
        if (Config.getInstance().useMCAVoices) {
            //baby sounds
            if (getAgeState() == AgeState.BABY) {
                return SoundsMCA.VILLAGER_BABY_LAUGH;
            }

            //snoring
            if (isSleeping()) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SNORE : SoundsMCA.VILLAGER_FEMALE_SNORE;
            }

            //scream in terror and pain
            if (getVillagerBrain().isPanicking()) {
                return getDeathSound();
            }

            //coughing
            if (isInfected() && random.nextBoolean()) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_COUGH : SoundsMCA.VILLAGER_FEMALE_COUGH;
            }

            //sirben
            if (random.nextBoolean() && getTraits().hasTrait(Traits.SIRBEN)) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SIRBEN : SoundsMCA.VILLAGER_FEMALE_SIRBEN;
            }

            //generic mood sounds
            Mood mood = getVillagerBrain().getMood();
            if (mood.getSoundInterval() > 0 && tickCount % mood.getSoundInterval() == 0) {
                return getGenetics().getGender() == Gender.MALE ? mood.getSoundMale() : mood.getSoundFemale();
            }

            return SoundsMCA.SILENT;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getAmbientSound();
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Override
    protected final SoundEvent getHurtSound(DamageSource cause) {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_HURT : SoundsMCA.VILLAGER_FEMALE_HURT;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getHurtSound(cause);
        } else {
            return SoundsMCA.SILENT;
        }
    }

    public final void playWelcomeSound() {
        if (Config.getInstance().useMCAVoices && !getVillagerBrain().isPanicking() && getAgeState() != AgeState.BABY) {
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_GREET : SoundsMCA.VILLAGER_FEMALE_GREET, getSoundVolume(), getVoicePitch());
        }
    }

    public final void playSurprisedSound() {
        if (Config.getInstance().useMCAVoices) {
            playSound(getSurprisedSound(), getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_YES : SoundsMCA.VILLAGER_FEMALE_YES;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getNotifyTradeSound();
        } else {
            return SoundsMCA.SILENT;
        }
    }

    public SoundEvent getNoSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_NO : SoundsMCA.VILLAGER_FEMALE_NO;
        } else if (Config.getInstance().useVanillaVoices) {
            return SoundEvents.VILLAGER_NO;
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean sold) {
        if (Config.getInstance().useMCAVoices) {
            return sold ? getNotifyTradeSound() : getNoSound();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getTradeUpdatedSound(sold);
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Override
    public void playCelebrateSound() {
        if (Config.getInstance().useMCAVoices) {
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_CELEBRATE : SoundsMCA.VILLAGER_FEMALE_CELEBRATE, getSoundVolume(), getVoicePitch());
        } else if (Config.getInstance().useVanillaVoices) {
            super.playCelebrateSound();
        } else {
            playSound(SoundsMCA.SILENT, getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public float getVoicePitch() {
        float r = (random.nextFloat() - 0.5f) * 0.05f;
        float g = (genetics.getGene(Genetics.VOICE) - 0.5f) * 0.3f;
        float a = Mth.lerp(AgeState.getDelta(tickCount), getAgeState().getPitch(), getAgeState().getNext().getPitch());
        return a + r + g;
    }

    @Override
    public final Component getDisplayName() {
        Component name = super.getDisplayName();

        if (getVillagerBrain() != null) {
            MoveState state = getVillagerBrain().getMoveState();
            if (state != MoveState.MOVE) {
                name = name.plainCopy().append(" (").append(state.getName()).append(")");
            }
            Chore chore = getVillagerBrain().getCurrentJob();
            if (chore != Chore.NONE) {
                name = name.plainCopy().append(" (").append(chore.getName()).append(")");
            }
        }

        if (isInfected()) {
            return name.plainCopy().withStyle(ChatFormatting.GREEN);
        } else if (getProfession() == ProfessionsMCA.OUTLAW) {
            return name.plainCopy().withStyle(ChatFormatting.RED);
        }
        return name;
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        Component cleaned = VillagerLike.cleanCustomName(name);
        super.setCustomName(cleaned);

        if (cleaned != null) {
            setName(cleaned.getString());
        }
    }

    @Override
    public float getInfectionProgress() {
        return getTrackedValue(INFECTION_PROGRESS);
    }

    @Override
    public void setInfectionProgress(float progress) {
        setTrackedValue(INFECTION_PROGRESS, progress);
    }

    @Override
    public void playSpeechEffect() {
        if (isSpeechImpaired()) {
            playSound(SoundEvents.ZOMBIE_AMBIENT, getSoundVolume(), getVoicePitch());
        }
    }

    // we make it public here
    @Override
    public void addParticlesAroundSelf(ParticleOptions parameters) {
        super.addParticlesAroundSelf(parameters);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory playerInventory, Player playerEntity) {
        return ChestMenu.threeRows(i, playerInventory, inventory);
    }

    @Override
    public VillagerDimensions getVillagerDimensions() {
        return dimensions;
    }

    @Override
    public boolean setAgeState(AgeState state) {
        if (VillagerLike.super.setAgeState(state)) {
            if (!level().isClientSide) {
                // trigger grow up advancements
                relations.getParents()
                        .filter(ServerPlayer.class::isInstance)
                        .map(ServerPlayer.class::cast).forEach(
                                e -> CriterionMCA.CHILD_AGE_STATE_CHANGE.trigger(e, state.name())
                        );

                if (state == AgeState.ADULT) {
                    // Notify player parents of the age up and set correct dialogue type.
                    relations.getParents()
                            .filter(Player.class::isInstance)
                            .map(Player.class::cast).forEach(
                                    p -> sendEventMessage(Component.translatable("notify.child.grownup", getName()), p)
                            );
                }

                refreshBrain((ServerLevel) level());

                getVillagerBrain().randomize(state);

                // set age specific clothes
                randomizeClothes();
            }
            return true;
        }

        return false;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> par) {
        if (getTypeDataManager().isParam(AGE_STATE, par) || getTypeDataManager().isParam(Genetics.SIZE.getParam(), par) || getTypeDataManager().isParam(Genetics.WIDTH.getParam(), par)) {
            refreshDimensions();
        }

        super.onSyncedDataUpdated(par);
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
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
        moveTowards(pos, speed, 1);
    }

    public void moveTowards(BlockPos pos) {
        moveTowards(pos, 0.5F);
    }

    public void lookAt(BlockPos pos) {
        this.brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case Status.MCA_VILLAGER_NEG_INTERACTION ->
                    level().addAlwaysVisibleParticle(ParticleTypesMCA.NEG_INTERACTION, true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_POS_INTERACTION ->
                    level().addAlwaysVisibleParticle(ParticleTypesMCA.POS_INTERACTION, true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_TRAGEDY -> this.addParticlesAroundSelf(ParticleTypes.DAMAGE_INDICATOR);
            default -> super.handleEntityEvent(id);
        }
    }

    public void onInvChange(Container inventoryFromListener) {
        //nop
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> type, boolean keepInventory) {
        T mob;
        if (!isRemoved() && type == EntityType.ZOMBIE_VILLAGER) {
            residency.leaveHome();
            mob = (T) VillagerLike.convertPreservingUuid(this,
                    getGenetics().getGender().getZombieType(),
                    keepInventory,
                    this::getEquipmentDropChance);
        } else {
            mob = super.convertTo(type, keepInventory);
        }

        if (mob instanceof VillagerLike<?> zombie) {
            zombie.copyVillagerAttributesFrom(this);
        }

        if (mob instanceof ZombieVillager zombie) {
            zombie.setPersistenceRequired();
        }

        if (mob instanceof ZombieVillagerEntityMCA zombie) {
            zombie.setInventory(inventory);
        }

        return mob;
    }

    @Override
    public void writeAdditionalConversionData(CompoundTag output) {
        output.putString(CHAT_AI_PROMPT_KEY, getChatAIPrompt());
        writeNicknames(output);
    }

    @Override
    public void readAdditionalConversionData(CompoundTag input) {
        chatAIPrompt = input.getString(CHAT_AI_PROMPT_KEY);
        readNicknames(input);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        CompoundTag data = McaDataFixers.update(nbt);
        super.readAdditionalSaveData(data);

        getTypeDataManager().load(this, data);
        relations.readFromNbt(data);
        longTermMemory.readFromNbt(data);
        readNicknames(data);
        chatAIPrompt = data.getString(CHAT_AI_PROMPT_KEY);

        playerModel = PlayerModel.byId(data.getInt("PlayerModel"));

        updateAttributes();

        inventory.clearContent();
        InventoryUtils.readFromNBT(this.registryAccess(), inventory, data);

        if (data.contains("DespawnDelay")) {
            this.despawnDelay = data.getInt("DespawnDelay");
        }

        if (data.contains("InteractedWith")) {
            this.interactedWith = data.getBoolean("InteractedWith");
        }

        if (data.contains("Clothes")) {
            validateClothes();
        }

        if (getVillagerBrain().getPersonality() == Personality.UNASSIGNED) {
            getVillagerBrain().randomize();
        }
    }

    @Override
    public void readAdditionalSaveDataForEditor(CompoundTag nbt) {
        readAdditionalSaveData(nbt);
    }

    public String getChatAIPrompt() {
        if (chatAIPrompt.isBlank()) {
            chatAIPrompt = ChatAIContext.createVillagerPrompt();
        }
        return chatAIPrompt;
    }

    public void setChatAIPrompt(String chatAIPrompt) {
        this.chatAIPrompt = chatAIPrompt;
    }

    private boolean isCarriedByPlayer() {
        return getVehicle() instanceof Player;
    }

    /**
     * Vanilla excludes passengers from entity chunk storage. MCA children riding
     * players are not restored from player data, so they must remain save roots.
     */
    @Override
    public boolean shouldBeSaved() {
        if (!isCarriedByPlayer()) {
            return super.shouldBeSaved();
        }

        Entity.RemovalReason removalReason = getRemovalReason();
        return removalReason == null || removalReason.shouldSave();
    }

    @Override
    public boolean save(CompoundTag nbt) {
        return isCarriedByPlayer() ? saveAsPassenger(nbt) : super.save(nbt);
    }

    @Override
    public final void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);

        relations.writeToNbt(nbt);
        longTermMemory.writeToNbt(nbt);
        writeNicknames(nbt);

        getTypeDataManager().save(this, nbt);
        InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);

        nbt.putString(CHAT_AI_PROMPT_KEY, getChatAIPrompt());
        nbt.putInt("DespawnDelay", this.despawnDelay);
        nbt.putBoolean("InteractedWith", this.interactedWith);

        if (interactedWith) {
            VillagerTrackerManager.update(this);
        }
    }

    @Override
    public boolean isHostile() {
        return getProfession() == ProfessionsMCA.OUTLAW;
    }

    //friends will not get slapped in revenge
    public boolean isFriend(EntityType<?> type) {
        return type == EntityType.IRON_GOLEM || type == EntitiesMCA.FEMALE_VILLAGER || type == EntitiesMCA.MALE_VILLAGER;
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return true;
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        //nop
    }

    @Override
    public void onCrossbowAttackPerformed() {
        //nop
    }

    @Override
    public void thunderHit(ServerLevel world, LightningBolt lightning) {
        getTraits().addTrait(Traits.ELECTRIFIED);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        InteractionHand weaponHand = RangedWeaponHelper.getWeaponHoldingHand(this);
        if (weaponHand == null) {
            return;
        }

        ItemStack weaponStack = this.getItemInHand(weaponHand);
        if (weaponStack.getItem() instanceof CrossbowItem crossbow) {
            crossbow.performShooting(
                    this.level(),
                    this,
                    weaponHand,
                    weaponStack,
                    1.75F,
                    14 - this.level().getDifficulty().getId() * 4,
                    target
            );
            this.onCrossbowAttackPerformed();
            return;
        }

        if (weaponStack.getItem() instanceof BowItem) {
            ItemStack arrow = this.getProjectile(weaponStack);
            AbstractArrow persistentProjectileEntity = ProjectileUtil.getMobArrow(this, arrow, pullProgress, weaponStack);
            double x = target.getX() - this.getX();
            double y = target.getY(0.3333333333333333D) - persistentProjectileEntity.getY();
            double z = target.getZ() - this.getZ();
            double vel = Math.sqrt(x * x + z * z);
            persistentProjectileEntity.shoot(x, y + vel * 0.20000000298023224D, z, 1.6F, 3);
            this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
            this.level().addFreshEntity(persistentProjectileEntity);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getProjectile(ItemStack stack) {
        if (stack.getItem() instanceof ProjectileWeaponItem weapon) {
            Predicate<ItemStack> predicate = weapon instanceof CrossbowItem ? ProjectileWeaponItem.ARROW_OR_FIREWORK : weapon.getAllSupportedProjectiles();
            ItemStack itemStack = ProjectileWeaponItem.getHeldProjectile(this, predicate);
            return itemStack.isEmpty() ? new ItemStack(Items.ARROW) : itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    private void tickDespawnDelay() {
        if (this.despawnDelay > 0 && !this.isTrading() && --this.despawnDelay == 0) {
            if (getRelationships().getPartner().isPresent() || getVillagerBrain().getMemories().values().stream().anyMatch(m -> random.nextInt(Config.getInstance().marriageHeartsRequirement) < m.getHearts())) {
                setProfession(VillagerProfession.NONE);
                setDespawnDelay(0);
            } else {
                this.discard();
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
        setProfession(ProfessionsMCA.MERCENARY);

        inventory.addItem(new ItemStack(Items.IRON_SWORD));
        inventory.addItem(new ItemStack(Items.IRON_AXE));
        inventory.addItem(new ItemStack(Items.IRON_PICKAXE));
        inventory.addItem(new ItemStack(Items.IRON_HOE));
        inventory.addItem(new ItemStack(Items.FISHING_ROD));
        inventory.addItem(new ItemStack(Items.BREAD, 16));
    }

    public void customLevelUp() {
        this.setVillagerData(this.getVillagerData().setLevel(this.getVillagerData().getLevel() + 1));
        this.updateTrades();
    }

    public String getNickname(UUID playerUUID) {
        return nicknames.getOrDefault(playerUUID, "");
    }

    public void setNickname(UUID playerUUID, String nickname) {
        String value = nickname.strip();
        if (value.length() > MAX_NICKNAME_LENGTH) {
            return;
        }

        if (value.isEmpty()) {
            nicknames.remove(playerUUID);
        } else {
            nicknames.put(playerUUID, value);
        }
    }

    private void readNicknames(CompoundTag nbt) {
        nicknames.clear();

        CompoundTag data = nbt.getCompound(NICKNAMES_KEY);
        for (String playerUUID : data.getAllKeys()) {
            try {
                setNickname(UUID.fromString(playerUUID), data.getString(playerUUID));
            } catch (IllegalArgumentException exception) {
                MCA.LOGGER.warn("Ignoring invalid nickname player UUID '{}'", playerUUID);
            }
        }
    }

    private void writeNicknames(CompoundTag nbt) {
        CompoundTag data = new CompoundTag();
        nicknames.forEach((playerUUID, nickname) -> data.putString(playerUUID.toString(), nickname));
        nbt.put(NICKNAMES_KEY, data);
    }
}
