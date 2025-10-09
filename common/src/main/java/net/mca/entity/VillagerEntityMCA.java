package net.mca.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import net.mca.*;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.ai.*;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.brain.VillagerTasksMCA;
import net.mca.entity.ai.pathfinder.VillagerNavigation;
import net.mca.entity.ai.relationship.*;
import net.mca.entity.interaction.VillagerCommandHandler;
import net.mca.item.ItemsMCA;
import net.mca.network.c2s.InteractionVillagerMessage;
import net.mca.resources.Names;
import net.mca.resources.Rank;
import net.mca.resources.Tasks;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillagerTrackerManager;
import net.mca.util.InventoryUtils;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CDataParameter;
import net.mca.util.network.datasync.CParameter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

import static net.mca.client.model.CommonVillagerModel.getVillager;

public class VillagerEntityMCA extends Villager implements VillagerLike<VillagerEntityMCA>, MenuProvider, CompassionateEntity<BreedableRelationship>, CrossbowAttackMob {
    final UUID EXTRA_HEALTH_EFFECT_ID = UUID.fromString("87f56a96-686f-4796-b035-22e16ee9e038");

    private static final CDataParameter<Float> INFECTION_PROGRESS = CParameter.create("infectionProgress", 0.0f);
    private static final CDataParameter<Integer> GROWTH_AMOUNT = CParameter.create("growthAmount", -AgeState.getMaxAge());
    private static final CDataManager<VillagerEntityMCA> DATA = createTrackedData(VillagerEntityMCA.class).build();

    public final ConversationManager conversationManager = new ConversationManager(this);
    private final VillagerBrain<VillagerEntityMCA> mcaBrain = new VillagerBrain<>(this);
    private final LongTermMemory longTermMemory = new LongTermMemory(this);
    private final Genetics genetics = new Genetics(this);
    private final Traits traits = new Traits(this);
    private final Residency residency = new Residency(this);
    private final BreedableRelationship relations = new BreedableRelationship(this);
    private final VillagerCommandHandler interactions = new VillagerCommandHandler(this);
    private final UpdatableInventory inventory = new UpdatableInventory(27);
    private final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.UNASSIGNED);

    private GameProfile gameProfile;
    private PlayerModel playerModel;

    private int despawnDelay;
    private int burned;
    private long lastHit = 0;
    private int prevGrowthAmount;
    private boolean interactedWith;

    private static final int RECALCULATE_DIMENSIONS_EVERY_N_TICKS = 100;

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(Class<E> type) {
        return VillagerLike.createTrackedData(type).addAll(INFECTION_PROGRESS, GROWTH_AMOUNT)
                .add(Residency::createTrackedData)
                .add(BreedableRelationship::createTrackedData);
    }

    public VillagerEntityMCA(EntityType<VillagerEntityMCA> type, Level w, Gender gender) {
        super(type, w);
        inventory.addListener(this::onInvChange);
        genetics.setGender(gender);
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
    protected void defineSynchedData() {
        super.defineSynchedData();

        getTypeDataManager().register(this);
    }

    @Override
    public GameProfile getGameProfile() {
        return gameProfile;
    }

    @Override
    public void updateCustomSkin() {
        if (!MCA.isBlankString(getTrackedValue(CUSTOM_SKIN))) {
            gameProfile = new GameProfile(null, getTrackedValue(CUSTOM_SKIN));
            SkullBlockEntity.updateGameprofile(gameProfile, profile -> gameProfile = profile);
        } else {
            gameProfile = null;
        }
    }

    @Override
    public CDataManager<VillagerEntityMCA> getTypeDataManager() {
        return DATA;
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        return new VillagerNavigation(this, world);
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
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        SpawnGroupData data = super.finalizeSpawn(world, difficulty, spawnReason, entityData, entityNbt);

        initialize(spawnReason);

        setAgeState(AgeState.byCurrentAge(getAge()));

        FamilyTreeNode entry = getRelationships().getFamilyEntry();
        if (!FamilyTreeNode.isValid(entry.father()) && !FamilyTreeNode.isValid(entry.mother())) {
            FamilyTree tree = FamilyTree.get(world.getLevel());
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
        return ProfessionsMCA.isImportant.contains(getProfession());
    }

    @Override
    public boolean requiresHome() {
        return !ProfessionsMCA.needsNoHome.contains(getProfession()) && getDespawnDelay() <= 0;
    }

    @Override
    public boolean canTradeWithProfession() {
        return !ProfessionsMCA.canNotTrade.contains(getProfession()) || (offers != null && !offers.isEmpty());
    }

    @Override
    public void setVillagerData(VillagerData data) {
        boolean hasChanged = !level().isClientSide && getProfession() != data.getProfession() && data.getProfession() != ProfessionsMCA.OUTLAW.get();
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
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);

        if (name != null) {
            setName(name.getString());
        }
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
        //player just get a beating
        attackedEntity(target);

        //base damage
        double baseDamage = getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = (float) (baseDamage * (getProfession() == ProfessionsMCA.GUARD.get() ? 3.0f : 1.0f));
        float knockback = (float) getAttributeValue(Attributes.ATTACK_KNOCKBACK);

        //traits
        if (getTraits().hasTrait(Traits.WEAK)) {
            damage *= 0.75f;
        }
        if (getTraits().hasTrait(Traits.TOUGH)) {
            damage *= 1.25f;
        }

        //enchantment
        if (target instanceof LivingEntity livingEntity) {
            damage += EnchantmentHelper.getDamageBonus(getMainHandItem(), livingEntity.getMobType());
            knockback += EnchantmentHelper.getKnockbackBonus(this);
        }

        //fire aspect
        int i = EnchantmentHelper.getFireAspect(this);
        if (i > 0) {
            target.setSecondsOnFire(i * 4);
        }

        boolean damageDealt = target.hurt(level().damageSources().mobAttack(this), damage);

        //knockback and post damage stuff
        if (damageDealt) {
            if (knockback > 0 && target instanceof LivingEntity livingEntity) {
                livingEntity.knockback(
                        knockback / 2, Mth.sin(getYRot() * ((float) Math.PI / 180F)),
                        -Mth.cos(getYRot() * ((float) Math.PI / 180F))
                );

                setDeltaMovement(getDeltaMovement().multiply(0.6D, 1, 0.6));
            }

            doEnchantDamageEffects(this, target);
            setLastHurtMob(target);
        }

        return damageDealt;
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
            getBrain().eraseMemory(MemoryModuleTypeMCA.SMALL_BOUNTY.get());
            getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            getBrain().eraseMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER.get());
        } else {
            getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY.get(), bounty - amount);
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
        return stack.getItem() != ItemsMCA.VILLAGER_EDITOR.get()
               && stack.getItem() != ItemsMCA.NEEDLE_AND_THREAD.get()
               && stack.getItem() != ItemsMCA.COMB.get()
               && stack.getItem() != ItemsMCA.POTION_OF_FEMINITY.get()
               && stack.getItem() != ItemsMCA.POTION_OF_MASCULINITY.get();
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
                if (player instanceof ServerPlayer e) {
                    NetworkHandler.sendToPlayer(new InteractionVillagerMessage("trade", uuid), e);
                }
            } else {
                playWelcomeSound();
                interactedWith = true;
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
                copiedSayNo();
            } else {
                boolean hasOffers = hasTradeOffers();
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!hasOffers && !level().isClientSide) {
                        copiedSayNo();
                    }

                    player.awardStat(Stats.TALKED_TO_VILLAGER);
                }

                if (hasOffers && !level().isClientSide) {
                    copiedBeginTradeWith(player);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    private void copiedSayNo() {
        this.setUnhappyCounter(40);
        if (!this.level().isClientSide()) {
            this.playSound(this.getNoSound(), this.getSoundVolume(), this.getVoicePitch());
        }
    }

    public boolean hasTradeOffers() {
        return !getOffers().isEmpty();
    }

    public void startTrading(Player customer) {
        copiedBeginTradeWith(customer);
    }

    private void copiedBeginTradeWith(Player customer) {
        this.copiedPrepareOffersFor(customer);
        this.setTradingPlayer(customer);
        this.openTradingScreen(customer, this.getDisplayName(), this.getVillagerData().getLevel());
    }

    private void copiedPrepareOffersFor(Player player) {
        int reputation = this.getPlayerReputation(player);
        if (reputation != 0) {
            for (MerchantOffer tradeOffer : this.getOffers()) {
                tradeOffer.addToSpecialPriceDiff(-Mth.floor((float) reputation * tradeOffer.getPriceMultiplier()));
            }
        }

        if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            MobEffectInstance statusEffect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
            //noinspection ConstantConditions
            int amplifier = statusEffect.getAmplifier();

            for (MerchantOffer tradeOffer2 : this.getOffers()) {
                double d = 0.3 + 0.0625 * (double) amplifier;
                int k = (int) Math.floor(d * (double) tradeOffer2.getBaseCostA().getCount());
                tradeOffer2.addToSpecialPriceDiff(-Math.max(k, 1));
            }
        }
    }

    @Override
    public VillagerEntityMCA getBreedOffspring(ServerLevel world, AgeableMob partner) {
        VillagerEntityMCA child = partner instanceof VillagerEntityMCA partnerVillager
                ? relations.getPregnancy().createChild(Gender.getRandom(), partnerVillager)
                : relations.getPregnancy().createChild(Gender.getRandom());

        child.setVillagerData(child.getVillagerData().setType(getRandomType(partner)));

        child.finalizeSpawn(world, world.getCurrentDifficultyAt(child.blockPosition()), MobSpawnType.BREEDING, null, null);
        return child;
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
        if (getProfession() == ProfessionsMCA.GUARD.get()) {
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
                    if ((!isGuard() || getSmallBounty() == 0) && requestCooldown()) {
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
                && getProfession() != ProfessionsMCA.GUARD.get()
                && Config.getInstance().enableInfection
                && random.nextFloat() < Config.getInstance().zombieBiteInfectionChance
                && random.nextFloat() > (getVillagerData().getLevel() - 1) * Config.getInstance().infectionChanceDecreasePerLevel
                && (getResidency().getHomeVillage().filter(v -> v.hasBuilding("infirmary")).isEmpty() || random.nextBoolean())) {
                setInfected(true);
                sendChatToAllAround("villager.bitten");
                MCA.LOGGER.info("{} has been infected", getName());
            }
        }

        @Nullable
        Entity attacker = source != null ? source.getEntity() : null;

        // Notify the surrounding guards when a villager is attacked. Yoinks!
        if (attacker instanceof LivingEntity livingEntity && !isHostile() && !isFriend(attacker.getType())) {
            // remember the specific attacker
            getBrain().setMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER.get(), Optional.of(livingEntity));
            getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY.get(), getSmallBounty() + 1);

            Vec3 pos = position();
            level().getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos, pos).inflate(32)).forEach(v -> {
                if (this.distanceToSqr(v) <= (v.getTarget() == null ? 1024 : 64)) {
                    if (attacker instanceof Player player) {
                        int bounty = v.getSmallBounty();
                        if (v.isGuard()) {
                            int maxWarning = v.getMaxWarnings(player);
                            if (bounty > maxWarning) {
                                // ok, that was enough
                                v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity);
                            } else if (bounty == 0 || bounty == maxWarning) {
                                // just a warning
                                v.sendChatMessage(player, "villager.warning");
                            }
                            v.getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY.get(), bounty + 1);
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

            //kill the damn tracker goals
            try {
                Field targetSelector = Mob.class.getDeclaredField("targetSelector");
                ((GoalSelector) targetSelector.get(attacker)).getRunningGoals().forEach(g -> {
                    if (g.getGoal() instanceof TargetGoal) {
                        g.getGoal().stop();
                    }
                });
            } catch (NoSuchFieldException | IllegalAccessException e) {
                //nop
            }

            damageAmount *= 0.0f;
        }

        return super.hurt(source, damageAmount);
    }

    long lastCooldown = 0L;

    private boolean requestCooldown() {
        if (level().getGameTime() - lastCooldown > 100) {
            lastCooldown = level().getGameTime();
            return true;
        } else {
            return false;
        }
    }

    public boolean isGuard() {
        return getProfession() == ProfessionsMCA.GUARD.get() || getProfession() == ProfessionsMCA.ARCHER.get();
    }

    public int getSmallBounty() {
        return Objects.requireNonNull(getBrain().getMemoryInternal(MemoryModuleTypeMCA.SMALL_BOUNTY.get())).orElse(0);
    }

    public boolean isHitBy(ServerPlayer player) {
        return Objects.requireNonNull(getBrain().getMemoryInternal(MemoryModuleTypeMCA.HIT_BY_PLAYER.get())).filter(v -> v == player).isPresent();
    }

    private int getMaxWarnings(Player attacker) {
        return getVillagerBrain().getMemoriesForPlayer(attacker).getHearts() / Math.max(1, Config.getInstance().heartsForPardonHit);
    }

    @Override
    public void aiStep() {
        updateSwingTime();

        super.aiStep();

        burned--;
        if (isOnFire()) {
            burned = Config.getInstance().burnedClothingTickLength;
        }
        if (burned > 0) {
            spawnBurntParticles();
        }

        if (!level().isClientSide) {
            if (tickCount % 200 == 0 && getHealth() < getMaxHealth()) {
                // if the villager has food they should try to eat.
                ItemStack food = getMainHandItem();

                if (food.isEdible()) {
                    eat(level(), food);
                } else {
                    //noinspection ConstantConditions
                    if (!findAndEquipToMain(i -> i.isEdible()
                                                 && i.getItem().getFoodProperties().getNutrition() > 0
                                                 && i.getItem().getFoodProperties().getEffects().stream().noneMatch(e -> StatusEffectDangerSet.isDanger.contains(e.getFirst().getEffect())))) {
                        heal(1); // natural regeneration
                    }
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

            //strengthen experienced villagers
            AttributeInstance instance = this.getAttributes().getInstance(Attributes.MAX_HEALTH);
            if (instance != null) {
                int level = this.getVillagerData().getLevel() - 1;
                instance.removeModifier(EXTRA_HEALTH_EFFECT_ID);
                instance.addTransientModifier(new AttributeModifier(EXTRA_HEALTH_EFFECT_ID, "level health boost", Config.getInstance().villagerHealthBonusPerLevel * level, AttributeModifier.Operation.ADDITION));
            }

            //twice a day, randomize the mood a bit
            if (this.tickCount % 12000 == 0) {
                int base = Math.round(mcaBrain.getMoodValue() / 12.0f);
                int value = random.nextInt(7) - 3;
                mcaBrain.modifyMoodValue(value - base);
            }
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
    public ItemStack eat(Level world, ItemStack stack) {
        if (stack.isEdible()) {
            //noinspection ConstantConditions
            heal(stack.getItem().getFoodProperties().getNutrition());
        }
        return super.eat(world, stack);
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

            Vec3 offset = head ? new Vec3(0, 0.35f, 0) : new Vec3(left ? 0.4F : -0.4F, 0.05f, 0).yRot(yaw);

            // todo currently only client side
            if (isClientSide() && MCAClient.useGeneticsRenderer(vehicle.getUUID())) {
                float height = getVillager(vehicle).getRawScaleFactor();
                offset = offset.multiply(1.0f, height, 1.0f);
                offset = offset.add(0.0f, vehicle.getPassengersRidingOffset() * height - vehicle.getPassengersRidingOffset(), 0.0f);
            }

            Vec3 pos = this.position();
            this.setPosRaw(pos.x() + offset.x(), pos.y() + offset.y(), pos.z() + offset.z());

            if (vehicle.isShiftKeyDown()) {
                stopRiding();
            }
        }
    }

    @Override
    public double getMyRidingOffset() {
        Entity vehicle = getVehicle();
        if (vehicle instanceof Player) {
            return -0.2;
        }
        return -0.35;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {

        Entity vehicle = getVehicle();
        if (vehicle instanceof Player) {
            return SLEEPING_DIMENSIONS;
        }

        if (pose == Pose.SLEEPING) {
            return SLEEPING_DIMENSIONS;
        }

        float height = getScale() * 2.0F;
        float width = getHorizontalScaleFactor() * 0.6F;

        return EntityDimensions.scalable(width, height);
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

        //distribute the hearts across the other villagers
        //this prevents rapid drops in village reputation as well as bounty hunters to know what you did
        Optional<Village> village = residency.getHomeVillage();
        if (village.isPresent()) {
            ServerLevel servRef = (ServerLevel) level();
            Map<UUID, Memories> memories = mcaBrain.getMemories();

            //iterate through all players for fate system
            if (cause.getEntity() != null) {
                servRef.players().forEach(player -> {
                    Rank relationToVillage = Tasks.getRank(village.get(), player);
                    ResourceLocation causeId = EntityType.getKey(cause.getEntity().getType());
                    CriterionMCA.FATE.trigger(player, causeId, relationToVillage);
                });
            }

            for (Map.Entry<UUID, Memories> entry : memories.entrySet()) {
                village.get().pushHearts(entry.getKey(), entry.getValue().getHearts());
            }
        }

        //move out
        residency.leaveHome();

        if (interactedWith) {
            VillagerTrackerManager.update(this);
        }
    }

    @Override
    public MoveControl getMoveControl() {
        return isRidingHorse() ? moveControl : super.getMoveControl();
    }

    @Override
    public PathNavigation getNavigation() {
        return isRidingHorse() ? navigation : super.getNavigation();
    }

    protected boolean isRidingHorse() {
        return isPassenger() && getVehicle() instanceof AbstractHorse;
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
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SCREAM.get() : SoundsMCA.VILLAGER_FEMALE_SCREAM.get();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getDeathSound();
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    public SoundEvent getSurprisedSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SURPRISE.get() : SoundsMCA.VILLAGER_FEMALE_SURPRISE.get();
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    @Nullable
    @Override
    protected final SoundEvent getAmbientSound() {
        if (Config.getInstance().useMCAVoices) {
            //baby sounds
            if (getAgeState() == AgeState.BABY) {
                return SoundsMCA.VILLAGER_BABY_LAUGH.get();
            }

            //snoring
            if (isSleeping()) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SNORE.get() : SoundsMCA.VILLAGER_FEMALE_SNORE.get();
            }

            //scream in terror and pain
            if (getVillagerBrain().isPanicking()) {
                return getDeathSound();
            }

            //coughing
            if (isInfected() && random.nextBoolean()) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_COUGH.get() : SoundsMCA.VILLAGER_FEMALE_COUGH.get();
            }

            //sirben
            if (random.nextBoolean() && getTraits().hasTrait(Traits.SIRBEN)) {
                return SoundsMCA.SIRBEN.get();
            }

            //generic mood sounds
            Mood mood = getVillagerBrain().getMood();
            if (mood.getSoundInterval() > 0 && tickCount % mood.getSoundInterval() == 0) {
                return getGenetics().getGender() == Gender.MALE ? mood.getSoundMale() : mood.getSoundFemale();
            }

            return SoundsMCA.SILENT.get();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getAmbientSound();
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    @Override
    protected final SoundEvent getHurtSound(DamageSource cause) {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_HURT.get() : SoundsMCA.VILLAGER_FEMALE_HURT.get();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getHurtSound(cause);
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    public final void playWelcomeSound() {
        if (Config.getInstance().useMCAVoices && !getVillagerBrain().isPanicking() && getAgeState() != AgeState.BABY) {
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_GREET.get() : SoundsMCA.VILLAGER_FEMALE_GREET.get(), getSoundVolume(), getVoicePitch());
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
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_YES.get() : SoundsMCA.VILLAGER_FEMALE_YES.get();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getNotifyTradeSound();
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    public SoundEvent getNoSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_NO.get() : SoundsMCA.VILLAGER_FEMALE_NO.get();
        } else if (Config.getInstance().useVanillaVoices) {
            return SoundEvents.VILLAGER_NO;
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean sold) {
        if (Config.getInstance().useMCAVoices) {
            return sold ? getNotifyTradeSound() : getNoSound();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getTradeUpdatedSound(sold);
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    @Override
    public void playCelebrateSound() {
        if (Config.getInstance().useMCAVoices) {
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_CELEBRATE.get() : SoundsMCA.VILLAGER_FEMALE_CELEBRATE.get(), getSoundVolume(), getVoicePitch());
        } else if (Config.getInstance().useVanillaVoices) {
            super.playCelebrateSound();
        } else {
            playSound(SoundsMCA.SILENT.get(), getSoundVolume(), getVoicePitch());
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
        } else if (getProfession() == ProfessionsMCA.OUTLAW.get()) {
            return name.plainCopy().withStyle(ChatFormatting.RED);
        }
        return name;
    }

    @Override
    @Nullable
    public final Component getCustomName() {
        String value = getTrackedValue(VILLAGER_NAME);
        return MCA.isBlankString(value) ? null : Component.literal(value);
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

                // set age specific clothes
                randomizeClothes();
            }
            return true;
        }

        return false;
    }

    @Override
    public float getScale() {
        return Math.min(0.999f, getRawScaleFactor());
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
        return getScale() * 1.75f;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> par) {
        if (getTypeDataManager().isParam(AGE_STATE, par) || getTypeDataManager().isParam(Genetics.SIZE.getParam(), par)) {
            refreshDimensions();
        }
        if (getTypeDataManager().isParam(CUSTOM_SKIN, par)) {
            updateCustomSkin();
        }

        super.onSyncedDataUpdated(par);
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
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
                    level().addAlwaysVisibleParticle(ParticleTypesMCA.NEG_INTERACTION.get(), true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_POS_INTERACTION ->
                    level().addAlwaysVisibleParticle(ParticleTypesMCA.POS_INTERACTION.get(), true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_TRAGEDY -> this.addParticlesAroundSelf(ParticleTypes.DAMAGE_INDICATOR);
            default -> super.handleEntityEvent(id);
        }
    }

    public void onInvChange(Container inventoryFromListener) {
        //nop
    }

    public void setInventory(UpdatableInventory inventory) {
        CompoundTag nbt = new CompoundTag();
        InventoryUtils.saveToNBT(inventory, nbt);
        InventoryUtils.readFromNBT(this.inventory, nbt);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> type, boolean keepInventory) {
        residency.leaveHome();

        T mob;
        if (!isRemoved() && type == EntityType.ZOMBIE_VILLAGER) {
            mob = (T) super.convertTo(getGenetics().getGender().getZombieType(), keepInventory);
        } else {
            mob = super.convertTo(type, keepInventory);
        }

        if (mob instanceof VillagerLike<?> zombie) {
            zombie.copyVillagerAttributesFrom(this);
        }

        if (mob instanceof ZombieVillager zombie) {
            zombie.finalizeSpawn((ServerLevel) level(), level().getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.CONVERSION, new Zombie.ZombieGroupData(false, true), null);
            zombie.setVillagerData(getVillagerData());
            zombie.setGossips(getGossips().store(NbtOps.INSTANCE));
            zombie.setTradeOffers(getOffers().createTag());
            zombie.setVillagerXp(getVillagerXp());
            zombie.setUUID(getUUID());
            zombie.setPersistenceRequired();

            level().levelEvent(null, 1026, this.blockPosition(), 0);
        }

        if (mob instanceof ZombieVillagerEntityMCA zombie) {
            zombie.setInventory(inventory);
        }

        return mob;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        getTypeDataManager().load(this, nbt);
        relations.readFromNbt(nbt);
        longTermMemory.readFromNbt(nbt);

        playerModel = PlayerModel.VALUES[nbt.getInt("playerModel")];

        updateSpeed();

        inventory.clearContent();
        InventoryUtils.readFromNBT(inventory, nbt);

        if (nbt.contains("DespawnDelay")) {
            this.despawnDelay = nbt.getInt("DespawnDelay");
        }

        if (nbt.contains("InteractedWith")) {
            this.interactedWith = nbt.getBoolean("InteractedWith");
        }

        if (nbt.contains("clothes")) {
            validateClothes();
        }

        if (getVillagerBrain().getPersonality() == Personality.UNASSIGNED) {
            getVillagerBrain().randomize();
        }
    }

    @Override
    public final void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        getTypeDataManager().save(this, nbt);
        relations.writeToNbt(nbt);
        longTermMemory.writeToNbt(nbt);
        nbt.putInt("DespawnDelay", this.despawnDelay);
        nbt.putBoolean("InteractedWith", this.interactedWith);
        InventoryUtils.saveToNBT(inventory, nbt);

        if (interactedWith) {
            VillagerTrackerManager.update(this);
        }
    }

    @Override
    public boolean isHostile() {
        return getProfession() == ProfessionsMCA.OUTLAW.get();
    }

    //friends will not get slapped in revenge
    public boolean isFriend(EntityType<?> type) {
        return type == EntityType.IRON_GOLEM || type == EntitiesMCA.FEMALE_VILLAGER.get() || type == EntitiesMCA.MALE_VILLAGER.get();
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return true;
    }

    @Override
    public void performCrossbowAttack(LivingEntity arg, float f) {
        InteractionHand lv = ProjectileUtil.getWeaponHoldingHand(arg, Items.CROSSBOW);
        ItemStack lv2 = arg.getItemInHand(lv);

        if (arg.isHolding(Items.CROSSBOW)) {
            CrossbowItem.performShooting(arg.level(), arg, lv, lv2, f, 4);
        }

        this.onCrossbowAttackPerformed();
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        //nop
    }

    @Override
    public void shootCrossbowProjectile(LivingEntity target, ItemStack crossbow, Projectile projectile, float multiShotSpray) {
        this.shootCrossbowProjectile(this, target, projectile, multiShotSpray, 1.6F);
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
        setTarget(target);
        attackedEntity(target);

        if (isHolding(Items.CROSSBOW)) {
            this.performCrossbowAttack(this, 1.75F);
        } else if (isHolding(Items.BOW)) {
            ItemStack itemStack = this.getProjectile(this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW)));
            AbstractArrow persistentProjectileEntity = this.createArrowProjectile(itemStack, pullProgress);
            double x = target.getX() - this.getX();
            double y = target.getY(0.3333333333333333D) - persistentProjectileEntity.getY();
            double z = target.getZ() - this.getZ();
            double vel = Math.sqrt(x * x + z * z);
            persistentProjectileEntity.shoot(x, y + vel * 0.20000000298023224D, z, 1.6F, 3);
            this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
            this.level().addFreshEntity(persistentProjectileEntity);
        }
    }

    protected AbstractArrow createArrowProjectile(ItemStack arrow, float damageModifier) {
        return ProjectileUtil.getMobArrow(this, arrow, damageModifier);
    }

    @Override
    public ItemStack getProjectile(ItemStack stack) {
        if (stack.getItem() instanceof ProjectileWeaponItem weapon) {
            Predicate<ItemStack> predicate = weapon.getSupportedHeldProjectiles();
            ItemStack itemStack = ProjectileWeaponItem.getHeldProjectile(this, predicate);
            return itemStack.isEmpty() ? new ItemStack(Items.ARROW) : itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0f)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0f)
                .add(Attributes.MAX_HEALTH, Config.getInstance().villagerMaxHealth);
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

    public void setDespawnDelay(int despawnDelay) {
        this.despawnDelay = despawnDelay;
    }

    public int getDespawnDelay() {
        return this.despawnDelay;
    }

    public void makeMercenary() {
        setProfession(ProfessionsMCA.MERCENARY.get());

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
}
