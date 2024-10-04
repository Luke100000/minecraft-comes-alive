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
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.control.JumpControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

import static net.mca.client.model.CommonVillagerModel.getVillager;

public class VillagerEntityMCA extends VillagerEntity implements VillagerLike<VillagerEntityMCA>, NamedScreenHandlerFactory, CompassionateEntity<BreedableRelationship>, CrossbowUser {
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

    public VillagerEntityMCA(EntityType<VillagerEntityMCA> type, World w, Gender gender) {
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

        if (!getWorld().isClient) {
            Optional<Village> village = residency.getHomeVillage();
            if (village.isPresent() && Config.getInstance().villagerRestockNotification) {
                village.get().broadCastMessage((ServerWorld) getWorld(), "events.restock", getName().getString());
            }
        }
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();

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
            SkullBlockEntity.loadProperties(gameProfile, profile -> gameProfile = profile);
        } else {
            gameProfile = null;
        }
    }

    @Override
    public CDataManager<VillagerEntityMCA> getTypeDataManager() {
        return DATA;
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new VillagerNavigation(this, world);
    }

    @Override
    protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
        return VillagerTasksMCA.initializeTasks(this, VillagerTasksMCA.createProfile().deserialize(dynamic));
    }

    @Override
    public void reinitializeBrain(ServerWorld world) {
        Brain<VillagerEntityMCA> brain = getMCABrain();
        brain.stopAllTasks(world, this);
        //copyWithoutBehaviors will copy the memories of the old brain to the new brain
        this.brain = brain.copy();
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
    protected Text getDefaultName() {
        return getProfessionText();
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);

        initialize(spawnReason);

        setAgeState(AgeState.byCurrentAge(getBreedingAge()));

        FamilyTreeNode entry = getRelationships().getFamilyEntry();
        if (!FamilyTreeNode.isValid(entry.father()) && !FamilyTreeNode.isValid(entry.mother())) {
            FamilyTree tree = FamilyTree.get(world.toServerWorld());
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
        setVillagerData(getVillagerData().withProfession(profession));
        reinitializeBrain((ServerWorld) getWorld());
    }

    @Override
    public Identifier getProfessionId() {
        return Registries.VILLAGER_PROFESSION.getId(getProfession());
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
        return !ProfessionsMCA.canNotTrade.contains(getProfession());
    }

    @Override
    public void setVillagerData(VillagerData data) {
        boolean hasChanged = !getWorld().isClient && getProfession() != data.getProfession() && data.getProfession() != ProfessionsMCA.OUTLAW.get();
        super.setVillagerData(data);
        if (hasChanged) {
            randomizeClothes();
            getRelationships().getFamilyEntry().setProfession(data.getProfession());
        }
    }

    @Override
    public void setBaby(boolean isBaby) {
        setBreedingAge(isBaby ? -AgeState.getMaxAge() : 0);
    }

    @Override
    public void setCustomName(@Nullable Text name) {
        super.setCustomName(name);

        if (name != null) {
            setName(name.getString());
        }
    }

    @Override
    public void setBreedingAge(int age) {
        super.setBreedingAge(age);

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
    public boolean tryAttack(Entity target) {
        //player just get a beating
        attackedEntity(target);

        //base damage // todo attributes?
        float damage = getProfession() == ProfessionsMCA.GUARD.get() ? 9 : 3;
        float knockback = 1;

        //traits
        if (getTraits().hasTrait(Traits.WEAK)) {
            damage *= 0.75f;
        }
        if (getTraits().hasTrait(Traits.TOUGH)) {
            damage *= 1.25f;
        }

        //enchantment
        if (target instanceof LivingEntity livingEntity) {
            damage += EnchantmentHelper.getAttackDamage(getMainHandStack(), livingEntity.getGroup());
            knockback += EnchantmentHelper.getKnockback(this);
        }

        //fire aspect
        int i = EnchantmentHelper.getFireAspect(this);
        if (i > 0) {
            target.setOnFireFor(i * 4);
        }

        boolean damageDealt = target.damage(getWorld().getDamageSources().mobAttack(this), damage);

        //knockback and post damage stuff
        if (damageDealt) {
            if (knockback > 0 && target instanceof LivingEntity livingEntity) {
                livingEntity.takeKnockback(
                        knockback / 2, MathHelper.sin(getYaw() * ((float) Math.PI / 180F)),
                        -MathHelper.cos(getYaw() * ((float) Math.PI / 180F))
                );

                setVelocity(getVelocity().multiply(0.6D, 1, 0.6));
            }

            applyDamageEffects(this, target);
            onAttacking(target);
        }

        return damageDealt;
    }

    private void attackedEntity(Entity target) {
        if (target instanceof PlayerEntity player) {
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
            getBrain().forget(MemoryModuleTypeMCA.SMALL_BOUNTY.get());
            getBrain().forget(MemoryModuleType.ATTACK_TARGET);
            getBrain().forget(MemoryModuleTypeMCA.HIT_BY_PLAYER.get());
        } else {
            getBrain().remember(MemoryModuleTypeMCA.SMALL_BOUNTY.get(), bounty - amount);
        }
    }

    private void pardonPlayers(PlayerEntity attacker) {
        pardonPlayers();
        int bounty = getSmallBounty();
        if (bounty <= getMaxWarnings(attacker)) {
            getBrain().forget(MemoryModuleType.ATTACK_TARGET);
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
    public final ActionResult interactAt(PlayerEntity player, Vec3d pos, @NotNull Hand hand) {
    	// This allows hitbox interactions to be ignored if the player is carrying a child villager.
    	if(getVehicle() != null && getVehicle().equals(player)) return ActionResult.PASS;
    	
        ItemStack stack = player.getStackInHand(hand);
        boolean isOnBlacklist = Config.getInstance().villagerInteractionItemBlacklist.contains(Registries.ITEM.getId(stack.getItem()).toString());
        if (hand.equals(Hand.MAIN_HAND) && !isOnBlacklist && !stack.isIn(TagsMCA.Items.VILLAGER_EGGS) && canInteractWithItemStackInHand(stack) && !getVillagerBrain().isPanicking()) {
            //make sure dialogueType is synced in case the client needs it
            getDialogueType(player);

            if (player.isSneaking()) {
                if (player instanceof ServerPlayerEntity e) {
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
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
    	// This allows hitbox interactions to be ignored if the player is carrying a child villager.
    	if(getVehicle() != null && getVehicle().equals(player)) return ActionResult.PASS;
    	
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isIn(TagsMCA.Items.VILLAGER_EGGS) && isAlive() && !hasCustomer() && !isSleeping() && canInteractWithItemStackInHand(stack) && !getVillagerBrain().isPanicking()) {
            if (isBaby()) {
                copiedSayNo();
            } else {
                boolean hasOffers = !getOffers().isEmpty();
                if (hand == Hand.MAIN_HAND) {
                    if (!hasOffers && !getWorld().isClient) {
                        copiedSayNo();
                    }

                    player.incrementStat(Stats.TALKED_TO_VILLAGER);
                }

                if (hasOffers && !getWorld().isClient) {
                    copiedBeginTradeWith(player);
                }
            }
            return ActionResult.success(getWorld().isClient);
        }
        return ActionResult.PASS;
    }

    private void copiedSayNo() {
        this.setHeadRollingTimeLeft(40);
        if (!this.getWorld().isClient()) {
            this.playSound(this.getNoSound(), this.getSoundVolume(), this.getSoundPitch());
        }
    }

    private void copiedBeginTradeWith(PlayerEntity customer) {
        this.copiedPrepareOffersFor(customer);
        this.setCustomer(customer);
        this.sendOffers(customer, this.getDisplayName(), this.getVillagerData().getLevel());
    }

    private void copiedPrepareOffersFor(PlayerEntity player) {
        int reputation = this.getReputation(player);
        if (reputation != 0) {
            for (TradeOffer tradeOffer : this.getOffers()) {
                tradeOffer.increaseSpecialPrice(-MathHelper.floor((float) reputation * tradeOffer.getPriceMultiplier()));
            }
        }

        if (player.hasStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE)) {
            StatusEffectInstance statusEffect = player.getStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE);
            //noinspection ConstantConditions
            int amplifier = statusEffect.getAmplifier();

            for (TradeOffer tradeOffer2 : this.getOffers()) {
                double d = 0.3 + 0.0625 * (double) amplifier;
                int k = (int) Math.floor(d * (double) tradeOffer2.getOriginalFirstBuyItem().getCount());
                tradeOffer2.increaseSpecialPrice(-Math.max(k, 1));
            }
        }
    }

    @Override
    public VillagerEntityMCA createChild(ServerWorld world, PassiveEntity partner) {
        VillagerEntityMCA child = partner instanceof VillagerEntityMCA partnerVillager
                ? relations.getPregnancy().createChild(Gender.getRandom(), partnerVillager)
                : relations.getPregnancy().createChild(Gender.getRandom());

        child.setVillagerData(child.getVillagerData().withType(getRandomType(partner)));

        child.initialize(world, world.getLocalDifficulty(child.getBlockPos()), SpawnReason.BREEDING, null, null);
        return child;
    }

    private VillagerType getRandomType(PassiveEntity partner) {
        double d = random.nextDouble();

        if (d < 0.5D) {
            return VillagerType.forBiome(getWorld().getBiome(getBlockPos()));
        }

        if (d < 0.75D) {
            return getVillagerData().getType();
        }

        return ((VillagerEntity) partner).getVillagerData().getType();
    }

    @Override
    public final boolean damage(DamageSource source, float damageAmount) {
        // no baby squishes
        if (getVehicle() instanceof PlayerEntity) {
            return super.damage(source, 0.0f);
        }

        // you can't hit babies!
        // TODO: Verify the `isUnblockable` replacement for 1.19.4, ensure same behavior
        if (!Config.getInstance().canHurtBabies && !source.isIn(DamageTypeTags.BYPASSES_SHIELD) && getAgeState() == AgeState.BABY) {
            if (source.getAttacker() instanceof PlayerEntity && requestCooldown()) {
                sendEventMessage(Text.translatable("villager.baby_hit"));
            }
            return super.damage(source, 0.0f);
        }

        // Guards take 50% less damage
        if (getProfession() == ProfessionsMCA.GUARD.get()) {
            damageAmount *= 0.5f;
        }

        if (getTraits().hasTrait(Traits.TOUGH)) {
            damageAmount *= 0.75f;
        }

        if (!getWorld().isClient) {
            //scream and loose hearts
            if (source.getAttacker() instanceof PlayerEntity player) {
                if (getWorld().getTime() - lastHit > 40) {
                    lastHit = getWorld().getTime();
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
            if (source.getSource() instanceof ZombieEntity
                    && getProfession() != ProfessionsMCA.GUARD.get()
                    && Config.getInstance().enableInfection
                    && random.nextFloat() < Config.getInstance().zombieBiteInfectionChance
                    && random.nextFloat() > (getVillagerData().getLevel() - 1) * Config.getInstance().infectionChanceDecreasePerLevel
                    && (getResidency().getHomeVillage().filter(v -> v.hasBuilding("infirmary")).isEmpty() || random.nextBoolean())) {
                setInfected(true);
                sendChatToAllAround("villager.bitten");
                MCA.LOGGER.info(getName() + " has been infected");
            }
        }

        @Nullable
        Entity attacker = source != null ? source.getAttacker() : null;

        // Notify the surrounding guards when a villager is attacked. Yoinks!
        if (attacker instanceof LivingEntity livingEntity && !isHostile() && !isFriend(attacker.getType())) {
            // remember the specific attacker
            getBrain().remember(MemoryModuleTypeMCA.HIT_BY_PLAYER.get(), Optional.of(livingEntity));
            getBrain().remember(MemoryModuleTypeMCA.SMALL_BOUNTY.get(), getSmallBounty() + 1);

            Vec3d pos = getPos();
            getWorld().getNonSpectatingEntities(VillagerEntityMCA.class, new Box(pos, pos).expand(32)).forEach(v -> {
                if (this.squaredDistanceTo(v) <= (v.getTarget() == null ? 1024 : 64)) {
                    if (attacker instanceof PlayerEntity player) {
                        int bounty = v.getSmallBounty();
                        if (v.isGuard()) {
                            int maxWarning = v.getMaxWarnings(player);
                            if (bounty > maxWarning) {
                                // ok, that was enough
                                v.getBrain().remember(MemoryModuleType.ATTACK_TARGET, livingEntity);
                            } else if (bounty == 0 || bounty == maxWarning) {
                                // just a warning
                                v.sendChatMessage(player, "villager.warning");
                            }
                            v.getBrain().remember(MemoryModuleTypeMCA.SMALL_BOUNTY.get(), bounty + 1);
                        }
                    } else if (v.isGuard()) {
                        // non players get attacked straight away
                        v.getBrain().remember(MemoryModuleType.ATTACK_TARGET, livingEntity);
                    }
                }
            });
        }

        // Iron Golem got his revenge, now chill
        if (attacker instanceof IronGolemEntity golem) {
            golem.stopAnger();

            //kill the damn tracker goals
            try {
                Field targetSelector = MobEntity.class.getDeclaredField("targetSelector");
                ((GoalSelector) targetSelector.get(attacker)).getRunningGoals().forEach(g -> {
                    if (g.getGoal() instanceof TrackTargetGoal) {
                        g.getGoal().stop();
                    }
                });
            } catch (NoSuchFieldException | IllegalAccessException e) {
                //nop
            }

            damageAmount *= 0.0f;
        }

        return super.damage(source, damageAmount);
    }

    long lastCooldown = 0L;

    private boolean requestCooldown() {
        if (getWorld().getTime() - lastCooldown > 100) {
            lastCooldown = getWorld().getTime();
            return true;
        } else {
            return false;
        }
    }

    public boolean isGuard() {
        return getProfession() == ProfessionsMCA.GUARD.get() || getProfession() == ProfessionsMCA.ARCHER.get();
    }

    public int getSmallBounty() {
        return Objects.requireNonNull(getBrain().getOptionalMemory(MemoryModuleTypeMCA.SMALL_BOUNTY.get())).orElse(0);
    }

    public boolean isHitBy(ServerPlayerEntity player) {
        return Objects.requireNonNull(getBrain().getOptionalMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER.get())).filter(v -> v == player).isPresent();
    }

    private int getMaxWarnings(PlayerEntity attacker) {
        return getVillagerBrain().getMemoriesForPlayer(attacker).getHearts() / Config.getInstance().heartsForPardonHit;
    }

    @Override
    public void tickMovement() {
        tickHandSwing();

        super.tickMovement();

        burned--;
        if (isOnFire()) {
            burned = Config.getInstance().burnedClothingTickLength;
        }
        if (burned > 0) {
            spawnBurntParticles();
        }

        if (!getWorld().isClient) {
            if (age % 200 == 0 && getHealth() < getMaxHealth()) {
                // if the villager has food they should try to eat.
                ItemStack food = getMainHandStack();

                if (food.isFood()) {
                    eatFood(getWorld(), food);
                } else {
                    //noinspection ConstantConditions
                    if (!findAndEquipToMain(i -> i.isFood()
                            && i.getItem().getFoodComponent().getHunger() > 0
                            && i.getItem().getFoodComponent().getStatusEffects().stream().noneMatch(e -> StatusEffectDangerSet.isDanger.contains(e.getFirst().getEffectType())))) {
                        heal(1); // natural regeneration
                    }
                }
            }

            tickDespawnDelay();

            residency.tick();

            relations.tick(age);

            inventory.update(this);

            if (age % Config.getInstance().pardonPlayerTicks == 0) {
                pardonPlayers();
            }

            // Brain and pregnancy depend on the above states, so we tick them last
            // Every 1 second
            mcaBrain.think();

            // pop a item from the desaturation queue
            if (age % Config.getInstance().giftDesaturationReset == 0) {
                getRelationships().getGiftSaturation().pop();
            }

            // track the position from time to time
            if (interactedWith && age % Config.getInstance().trackVillagerPositionEveryNTicks == 0) {
                VillagerTrackerManager.update(this);
            }
        }
    }

    protected boolean findAndEquipToMain(Predicate<ItemStack> predicate) {
        int slot = InventoryUtils.getFirstSlotContainingItem(getInventory(), predicate);

        if (slot > -1) {
            ItemStack replacement = getInventory().getStack(slot).split(1);

            if (!replacement.isEmpty()) {
                setStackInHand(getDominantHand(), replacement);
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
            calculateDimensions();
        }

        if (getWorld().isClient) {
            // procreate anim
            if (relations.isProcreating()) {
                headYaw += 50;
            }

            // mood particles
            Mood mood = mcaBrain.getMood();
            if (mood.getParticle() != null && this.age % mood.getParticleInterval() == 0 && getWorld().random.nextBoolean()) {
                produceParticles(mood.getParticle());
            }
        } else {
            // infection
            float infection = getInfectionProgress();
            if (infection > 0 && this.age % 20 == 0) {
                if (infection > FEVER_THRESHOLD && getWorld().random.nextInt(25) == 0) {
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
            if (this.age % 90 == 0 && mcaBrain.isPanicking()) {
                sendChatToAllAround("villager.scream");
            }

            // sirben noises
            if (this.age % 60 == 0 && random.nextInt(50) == 0 && traits.hasTrait(Traits.SIRBEN)) {
                sendChatToAllAround("sirben");
            }

            //strengthen experienced villagers
            EntityAttributeInstance instance = this.getAttributes().getCustomInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (instance != null) {
                int level = this.getVillagerData().getLevel() - 1;
                instance.removeModifier(EXTRA_HEALTH_EFFECT_ID);
                instance.addTemporaryModifier(new EntityAttributeModifier(EXTRA_HEALTH_EFFECT_ID, "level health boost", Config.getInstance().villagerHealthBonusPerLevel * level, EntityAttributeModifier.Operation.ADDITION));
            }

            //twice a day, randomize the mood a bit
            if (this.age % 12000 == 0) {
                int base = Math.round(mcaBrain.getMoodValue() / 12.0f);
                int value = random.nextInt(7) - 3;
                mcaBrain.modifyMoodValue(value - base);
            }
        }
    }

    @Override
    public void calculateDimensions() {
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
        boolean oldOnGround = this.isOnGround();
        super.calculateDimensions();
        this.setOnGround(oldOnGround);
    }

    @Override
    public ItemStack eatFood(World world, ItemStack stack) {
        if (stack.isFood()) {
            //noinspection ConstantConditions
            heal(stack.getItem().getFoodComponent().getHunger());
        }
        return super.eatFood(world, stack);
    }

    @Override
    public void tickRiding() {
        super.tickRiding();

        Entity vehicle = getVehicle();

        if (vehicle instanceof PathAwareEntity pathAwareEntity) {
            bodyYaw = pathAwareEntity.bodyYaw;
        }

        if (vehicle instanceof PlayerEntity player) {
            List<Entity> passengers = vehicle.getPassengerList();

            float yaw = -player.bodyYaw * 0.017453292F;

            boolean left = passengers.get(0) == this;
            boolean head = passengers.size() > 2 && passengers.get(2) == this;

            Vec3d offset = head ? new Vec3d(0, 0.35f, 0) : new Vec3d(left ? 0.4F : -0.4F, 0.05f, 0).rotateY(yaw);

            // todo currently only client side
            if (isClient() && MCAClient.useGeneticsRenderer(vehicle.getUuid())) {
                float height = getVillager(vehicle).getRawScaleFactor();
                offset = offset.multiply(1.0f, height, 1.0f);
                offset = offset.add(0.0f, vehicle.getMountedHeightOffset() * height - vehicle.getMountedHeightOffset(), 0.0f);
            }

            Vec3d pos = this.getPos();
            this.setPos(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());

            if (vehicle.isSneaking()) {
                stopRiding();
            }
        }
    }

    @Override
    public double getHeightOffset() {
        Entity vehicle = getVehicle();
        if (vehicle instanceof PlayerEntity) {
            return -0.2;
        }
        return -0.35;
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {

        Entity vehicle = getVehicle();
        if (vehicle instanceof PlayerEntity) {
            return SLEEPING_DIMENSIONS;
        }

        if (pose == EntityPose.SLEEPING) {
            return SLEEPING_DIMENSIONS;
        }

        float height = getScaleFactor() * 2.0F;
        float width = getHorizontalScaleFactor() * 0.6F;

        return EntityDimensions.changing(width, height);
    }

    @Override
    public void onDeath(DamageSource cause) {
        // deselect equipment as this messes with MobEntities equipment dropping
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.equipStack(slot, ItemStack.EMPTY);
        }

        //death message
        if (!getWorld().isClient) {
            getResidency().getHomeVillage().flatMap(Village::getCivilRegistry).ifPresent(r -> r.addText(getDamageTracker().getDeathMessage()));
        }

        super.onDeath(cause);

        if (getWorld().isClient) {
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
            ServerWorld servRef = (ServerWorld) getWorld();
            Map<UUID, Memories> memories = mcaBrain.getMemories();

            //iterate through all players for fate system
            if (cause.getAttacker() != null) {
                servRef.getPlayers().forEach(player -> {
                    Rank relationToVillage = Tasks.getRank(village.get(), player);
                    Identifier causeId = EntityType.getId(cause.getAttacker().getType());
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
    public JumpControl getJumpControl() {
        return jumpControl;
    }

    @Override
    public EntityNavigation getNavigation() {
        return isRidingHorse() ? navigation : super.getNavigation();
    }

    protected boolean isRidingHorse() {
        return hasVehicle() && getVehicle() instanceof AbstractHorseEntity;
    }

    @Override
    public void requestTeleport(double destX, double destY, double destZ) {
        if (hasVehicle()) {
            Entity rootVehicle = getRootVehicle();
            if (rootVehicle instanceof MobEntity) {
                rootVehicle.requestTeleport(destX, destY, destZ);
                return; // villagers can travel by teleporting, so make sure they take their mount with
            }
        }

        super.requestTeleport(destX, destY, destZ);
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
            if (mood.getSoundInterval() > 0 && age % mood.getSoundInterval() == 0) {
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
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_GREET.get() : SoundsMCA.VILLAGER_FEMALE_GREET.get(), getSoundVolume(), getSoundPitch());
        }
    }

    public final void playSurprisedSound() {
        if (Config.getInstance().useMCAVoices) {
            playSound(getSurprisedSound(), getSoundVolume(), getSoundPitch());
        }
    }

    @Override
    public SoundEvent getYesSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_YES.get() : SoundsMCA.VILLAGER_FEMALE_YES.get();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getYesSound();
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    public SoundEvent getNoSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_NO.get() : SoundsMCA.VILLAGER_FEMALE_NO.get();
        } else if (Config.getInstance().useVanillaVoices) {
            return SoundEvents.ENTITY_VILLAGER_NO;
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    @Override
    protected SoundEvent getTradingSound(boolean sold) {
        if (Config.getInstance().useMCAVoices) {
            return sold ? getYesSound() : getNoSound();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getTradingSound(sold);
        } else {
            return SoundsMCA.SILENT.get();
        }
    }

    @Override
    public void playCelebrateSound() {
        if (Config.getInstance().useMCAVoices) {
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_CELEBRATE.get() : SoundsMCA.VILLAGER_FEMALE_CELEBRATE.get(), getSoundVolume(), getSoundPitch());
        } else if (Config.getInstance().useVanillaVoices) {
            super.playCelebrateSound();
        } else {
            playSound(SoundsMCA.SILENT.get(), getSoundVolume(), getSoundPitch());
        }
    }

    @Override
    public float getSoundPitch() {
        float r = (random.nextFloat() - 0.5f) * 0.05f;
        float g = (genetics.getGene(Genetics.VOICE) - 0.5f) * 0.3f;
        float a = MathHelper.lerp(AgeState.getDelta(age), getAgeState().getPitch(), getAgeState().getNext().getPitch());
        return a + r + g;
    }

    @Override
    public final Text getDisplayName() {
        Text name = super.getDisplayName();

        if (getVillagerBrain() != null) {
            MoveState state = getVillagerBrain().getMoveState();
            if (state != MoveState.MOVE) {
                name = name.copyContentOnly().append(" (").append(state.getName()).append(")");
            }
            Chore chore = getVillagerBrain().getCurrentJob();
            if (chore != Chore.NONE) {
                name = name.copyContentOnly().append(" (").append(chore.getName()).append(")");
            }
        }

        if (isInfected()) {
            return name.copyContentOnly().formatted(Formatting.GREEN);
        } else if (getProfession() == ProfessionsMCA.OUTLAW.get()) {
            return name.copyContentOnly().formatted(Formatting.RED);
        }
        return name;
    }

    @Override
    @Nullable
    public final Text getCustomName() {
        String value = getTrackedValue(VILLAGER_NAME);
        return MCA.isBlankString(value) ? null : Text.literal(value);
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
            playSound(SoundEvents.ENTITY_ZOMBIE_AMBIENT, getSoundVolume(), getSoundPitch());
        }
    }

    // we make it public here
    @Override
    public void produceParticles(ParticleEffect parameters) {
        super.produceParticles(parameters);
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int i, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        return GenericContainerScreenHandler.createGeneric9x3(i, playerInventory, inventory);
    }

    @Override
    public VillagerDimensions getVillagerDimensions() {
        return dimensions;
    }

    @Override
    public boolean setAgeState(AgeState state) {
        if (VillagerLike.super.setAgeState(state)) {
            if (!getWorld().isClient) {
                // trigger grow up advancements
                relations.getParents()
                        .filter(ServerPlayerEntity.class::isInstance)
                        .map(ServerPlayerEntity.class::cast).forEach(
                                e -> CriterionMCA.CHILD_AGE_STATE_CHANGE.trigger(e, state.name())
                        );

                if (state == AgeState.ADULT) {
                    // Notify player parents of the age up and set correct dialogue type.
                    relations.getParents()
                            .filter(PlayerEntity.class::isInstance)
                            .map(PlayerEntity.class::cast).forEach(
                                    p -> sendEventMessage(Text.translatable("notify.child.grownup", getName()), p)
                            );
                }

                reinitializeBrain((ServerWorld) getWorld());

                // set age specific clothes
                randomizeClothes();
            }
            return true;
        }

        return false;
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
    public void onTrackedDataSet(TrackedData<?> par) {
        if (getTypeDataManager().isParam(AGE_STATE, par) || getTypeDataManager().isParam(Genetics.SIZE.getParam(), par)) {
            calculateDimensions();
        }
        if (getTypeDataManager().isParam(CUSTOM_SKIN, par)) {
            updateCustomSkin();
        }

        super.onTrackedDataSet(par);
    }

    @Override
    public SimpleInventory getInventory() {
        return inventory;
    }

    public void moveTowards(BlockPos pos, float speed, int closeEnoughDist) {
        this.brain.remember(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, closeEnoughDist));
        this.lookAt(pos);
    }

    public void moveTowards(BlockPos pos, float speed) {
        moveTowards(pos, speed, 1);
    }

    public void moveTowards(BlockPos pos) {
        moveTowards(pos, 0.5F);
    }

    public void lookAt(BlockPos pos) {
        this.brain.remember(MemoryModuleType.LOOK_TARGET, new BlockPosLookTarget(pos));
    }

    @Override
    public void handleStatus(byte id) {
        switch (id) {
            case Status.MCA_VILLAGER_NEG_INTERACTION ->
                    getWorld().addImportantParticle(ParticleTypesMCA.NEG_INTERACTION.get(), true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_POS_INTERACTION ->
                    getWorld().addImportantParticle(ParticleTypesMCA.POS_INTERACTION.get(), true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_TRAGEDY -> this.produceParticles(ParticleTypes.DAMAGE_INDICATOR);
            default -> super.handleStatus(id);
        }
    }

    public void onInvChange(Inventory inventoryFromListener) {
        //nop
    }

    public void setInventory(UpdatableInventory inventory) {
        NbtCompound nbt = new NbtCompound();
        InventoryUtils.saveToNBT(inventory, nbt);
        InventoryUtils.readFromNBT(this.inventory, nbt);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public <T extends MobEntity> T convertTo(EntityType<T> type, boolean keepInventory) {
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

        if (mob instanceof ZombieVillagerEntity zombie) {
            zombie.initialize((ServerWorld) getWorld(), getWorld().getLocalDifficulty(zombie.getBlockPos()), SpawnReason.CONVERSION, new ZombieEntity.ZombieData(false, true), null);
            zombie.setVillagerData(getVillagerData());
            zombie.setGossipData(getGossip().serialize(NbtOps.INSTANCE));
            zombie.setOfferData(getOffers().toNbt());
            zombie.setXp(getExperience());
            zombie.setUuid(getUuid());
            zombie.setPersistent();

            getWorld().syncWorldEvent(null, 1026, this.getBlockPos(), 0);
        }

        if (mob instanceof ZombieVillagerEntityMCA zombie) {
            zombie.setInventory(inventory);
        }

        return mob;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        getTypeDataManager().load(this, nbt);
        relations.readFromNbt(nbt);
        longTermMemory.readFromNbt(nbt);

        playerModel = PlayerModel.VALUES[nbt.getInt("playerModel")];

        updateSpeed();

        inventory.clear();
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
    public final void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
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
    public boolean canUseRangedWeapon(RangedWeaponItem weapon) {
        return true;
    }

    @Override
    public void shoot(LivingEntity arg, float f) {
        Hand lv = ProjectileUtil.getHandPossiblyHolding(arg, Items.CROSSBOW);
        ItemStack lv2 = arg.getStackInHand(lv);

        if (arg.isHolding(Items.CROSSBOW)) {
            CrossbowItem.shootAll(arg.getWorld(), arg, lv, lv2, f, 4);
        }

        this.postShoot();
    }

    @Override
    public void setCharging(boolean charging) {
        //nop
    }

    @Override
    public void shoot(LivingEntity target, ItemStack crossbow, ProjectileEntity projectile, float multiShotSpray) {
        this.shoot(this, target, projectile, multiShotSpray, 1.6F);
    }

    @Override
    public void postShoot() {
        //nop
    }

    @Override
    public void onStruckByLightning(ServerWorld world, LightningEntity lightning) {
        getTraits().addTrait(Traits.ELECTRIFIED);
    }

    @Override
    public void attack(LivingEntity target, float pullProgress) {
        setTarget(target);
        attackedEntity(target);

        if (isHolding(Items.CROSSBOW)) {
            this.shoot(this, 1.75F);
        } else if (isHolding(Items.BOW)) {
            ItemStack itemStack = this.getProjectileType(this.getStackInHand(ProjectileUtil.getHandPossiblyHolding(this, Items.BOW)));
            PersistentProjectileEntity persistentProjectileEntity = this.createArrowProjectile(itemStack, pullProgress);
            double x = target.getX() - this.getX();
            double y = target.getBodyY(0.3333333333333333D) - persistentProjectileEntity.getY();
            double z = target.getZ() - this.getZ();
            double vel = Math.sqrt(x * x + z * z);
            persistentProjectileEntity.setVelocity(x, y + vel * 0.20000000298023224D, z, 1.6F, 3);
            this.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
            this.getWorld().spawnEntity(persistentProjectileEntity);
        }
    }

    protected PersistentProjectileEntity createArrowProjectile(ItemStack arrow, float damageModifier) {
        return ProjectileUtil.createArrowProjectile(this, arrow, damageModifier);
    }

    @Override
    public ItemStack getProjectileType(ItemStack stack) {
        if (stack.getItem() instanceof RangedWeaponItem weapon) {
            Predicate<ItemStack> predicate = weapon.getHeldProjectiles();
            ItemStack itemStack = RangedWeaponItem.getHeldProjectile(this, predicate);
            return itemStack.isEmpty() ? new ItemStack(Items.ARROW) : itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    public static DefaultAttributeContainer.Builder createVillagerAttributes() {
        return VillagerEntity.createVillagerAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, Config.getInstance().villagerMaxHealth);
    }

    private void tickDespawnDelay() {
        if (this.despawnDelay > 0 && !this.hasCustomer() && --this.despawnDelay == 0) {
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

        inventory.addStack(new ItemStack(Items.IRON_SWORD));
        inventory.addStack(new ItemStack(Items.IRON_AXE));
        inventory.addStack(new ItemStack(Items.IRON_PICKAXE));
        inventory.addStack(new ItemStack(Items.IRON_HOE));
        inventory.addStack(new ItemStack(Items.FISHING_ROD));
        inventory.addStack(new ItemStack(Items.BREAD, 16));
    }

    public void customLevelUp() {
        this.setVillagerData(this.getVillagerData().withLevel(this.getVillagerData().getLevel() + 1));
        this.fillRecipes();
    }
}
