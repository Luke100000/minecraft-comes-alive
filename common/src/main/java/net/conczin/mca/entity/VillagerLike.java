package net.conczin.mca.entity;

import com.mojang.serialization.JsonOps;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.DialogueType;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Messenger;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairList;
import net.conczin.mca.resources.Names;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.util.network.datasync.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.Optional;
import java.util.Set;

public interface VillagerLike<E extends Entity & VillagerLike<E>> extends CTrackedEntity<E>, VillagerDataHolder, Infectable, Messenger {
    CDataParameter<String> CLOTHES = CParameter.create("Clothes", "");
    CDataParameter<String> HAIR = CParameter.create("Hair", "");
    CDataParameter<Float> HAIR_COLOR_RED = CParameter.create("HairColorRed", 0.0f);
    CDataParameter<Float> HAIR_COLOR_GREEN = CParameter.create("HairColorGreen", 0.0f);
    CDataParameter<Float> HAIR_COLOR_BLUE = CParameter.create("HairColorBlue", 0.0f);
    CEnumParameter<AgeState> AGE_STATE = CParameter.create("AgeState", AgeState.UNASSIGNED);

    Identifier SPEED_ID = MCA.locate("trait_speed");
    Identifier DAMAGE_ID = MCA.locate("trait_damage");

    static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder
                .addAll(CLOTHES, HAIR, HAIR_COLOR_RED, HAIR_COLOR_GREEN, HAIR_COLOR_BLUE, AGE_STATE)
                .add(Genetics::createTrackedData)
                .add(Traits::createTrackedData)
                .add(VillagerBrain::createTrackedData);
    }

    static VillagerLike<?> toVillager(PlayerSaveData player) {
        CompoundTag villagerData = player.getEntityData();
        return (VillagerLike<?>) EntitiesMCA.MALE_VILLAGER.create(
                TagValueInput.create(ProblemReporter.DISCARDING, player.getWorld().registryAccess(), villagerData),
                player.getWorld(),
                EntitySpawnReason.LOAD
        ).orElse(null);
    }

    static VillagerLike<?> toVillager(Entity entity) {
        if (entity instanceof VillagerLike<?>) {
            return (VillagerLike<?>) entity;
        } else if (entity instanceof ServerPlayer playerEntity) {
            return toVillager(PlayerSaveData.get(playerEntity));
        } else {
            return null;
        }
    }

    Genetics getGenetics();

    Traits getTraits();

    VillagerBrain<?> getVillagerBrain();

    EntityCommandHandler<?> getInteractions();

    default void initialize(EntitySpawnReason spawnReason) {
        if (spawnReason != EntitySpawnReason.CONVERSION) {
            if (spawnReason != EntitySpawnReason.BREEDING) {
                getGenetics().randomize();
                getTraits().randomize();
            }

            initializeSkin(false);
            getVillagerBrain().randomize();
        }

        if (getGenetics().getGender() == Gender.UNASSIGNED) {
            getGenetics().setGender(Gender.getRandom());
        }

        if (asEntity().getCustomName() == null) {
            asEntity().setCustomName(Component.literal(Names.pickCitizenName(getGenetics().getGender(), asEntity())));
        }

        validateClothes();

        asEntity().refreshDimensions();
    }

    @Override
    default boolean isSpeechImpaired() {
        return getInfectionProgress() > BABBLING_THRESHOLD;
    }

    @Override
    default boolean isToYoungToSpeak() {
        return getAgeState() == AgeState.BABY;
    }

    default void setName(String name) {
        if (!asEntity().level().isClientSide()) {
            EntityRelationship.of(asEntity()).ifPresent(relationship -> relationship.getFamilyEntry().setName(name));
        }
    }

    /**
     * @param villager the villager to check
     * @return the set of "valid" genders
     */
    default Set<Gender> getAttractedGenderSet(VillagerLike<?> villager) {
        if (villager.getTraits().hasTrait(Traits.BISEXUAL)) {
            return Set.of(Gender.MALE, Gender.FEMALE, Gender.NEUTRAL);
        } else if (villager.getTraits().hasTrait(Traits.HOMOSEXUAL)) {
            return Set.of(villager.getGenetics().getGender(), Gender.NEUTRAL);
        } else if (villager.getTraits().hasTrait(Traits.ASEXUAL)) {
            return Set.of(Gender.NEUTRAL);
        } else {
            return Set.of(villager.getGenetics().getGender().opposite(), Gender.NEUTRAL);
        }
    }

    default boolean canBeAttractedTo(VillagerLike<?> other) {
        return getAttractedGenderSet(this).contains(other.getGenetics().getGender()) && getAttractedGenderSet(other).contains(getGenetics().getGender());
    }

    default boolean canBeAttractedTo(PlayerSaveData other) {
        return !Config.getInstance().enableGenderCheckForPlayers || canBeAttractedTo(toVillager(other));
    }

    default InteractionHand getDominantHand() {
        return getTraits().hasTrait(Traits.LEFT_HANDED) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    default InteractionHand getOpposingHand() {
        return getDominantHand() == InteractionHand.OFF_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    default EquipmentSlot getDominantSlot() {
        return getDominantHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    default EquipmentSlot getOpposingSlot() {
        return getOpposingHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    default Identifier getProfessionId() {
        return MCA.locate("none");
    }

    default String getProfessionName() {
        String professionName = (
                getProfessionId().getNamespace().equalsIgnoreCase("minecraft") ?
                        (getProfessionId().getPath().equals("none") ? "mca.none" : getProfessionId().getPath()) :
                        getProfessionId().toString()
        ).replace(":", ".");

        return MCA.isBlankString(professionName) ? "mca.none" : professionName;
    }

    default MutableComponent getProfessionText() {
        return Component.translatable("entity.minecraft.villager." + getProfessionName());
    }

    default boolean isProfessionImportant() {
        return false;
    }

    default boolean requiresHome() {
        return false;
    }

    default boolean canTradeWithProfession() {
        return false;
    }

    default String getClothes() {
        return getTrackedValue(CLOTHES);
    }

    default void setClothes(Identifier clothes) {
        setClothes(clothes.toString());
    }

    default void setClothes(String clothes) {
        setTrackedValue(CLOTHES, clothes);
    }

    default String getHair() {
        return getTrackedValue(HAIR);
    }

    default void setHair(Identifier hair) {
        setHair(hair.toString());
    }

    default void setHair(String hair) {
        setTrackedValue(HAIR, hair);
    }

    default void setHairDye(float r, float g, float b) {
        setTrackedValue(HAIR_COLOR_RED, r);
        setTrackedValue(HAIR_COLOR_GREEN, g);
        setTrackedValue(HAIR_COLOR_BLUE, b);
    }

    default void clearHairDye() {
        setHairDye(0.0f, 0.0f, 0.0f);
    }

    default int getHairDye() {
        return ARGB.colorFromFloat(
                1.0f,
                getTrackedValue(HAIR_COLOR_RED),
                getTrackedValue(HAIR_COLOR_GREEN),
                getTrackedValue(HAIR_COLOR_BLUE)
        );
    }

    default void setHairDye(DyeColor color) {
        int components = color.getTextureDiffuseColor();
        int dye = getHairDye();
        if (dye > 0) {
            components = ARGB.srgbLerp(0.5f, components, dye);
        }

        setTrackedValue(HAIR_COLOR_RED, ARGB.red(components) / 255.0f);
        setTrackedValue(HAIR_COLOR_GREEN, ARGB.green(components) / 255.0f);
        setTrackedValue(HAIR_COLOR_BLUE, ARGB.blue(components) / 255.0f);
    }

    default AgeState getAgeState() {
        return getTrackedValue(AGE_STATE);
    }

    default VillagerDimensions getVillagerDimensions() {
        return getAgeState();
    }

    default void updateAttributes() {
        //set speed
        float speed = 1.0f;
        if (getTraits().hasTrait(Traits.ATHLETIC)) {
            speed *= 1.1f;
        }

        speed /= (0.9f + getGenetics().getGene(Genetics.WIDTH) * 0.2f);
        speed *= (0.9f + getGenetics().getGene(Genetics.SIZE) * 0.2f);

        speed *= getAgeState().getSpeed();

        AttributeInstance entityAttributeInstance = asEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        if (entityAttributeInstance != null) {
            entityAttributeInstance.removeModifier(SPEED_ID);
            AttributeModifier speedModifier = new AttributeModifier(SPEED_ID, speed - 1.0f, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            entityAttributeInstance.addTransientModifier(speedModifier);
        }

        // damage
        float damageMultiplier = 1.0f;
        if (getTraits().hasTrait(Traits.WEAK)) {
            damageMultiplier *= 0.75f;
        }
        if (getTraits().hasTrait(Traits.TOUGH)) {
            damageMultiplier *= 1.5f;
        }
        AttributeInstance attackAttributeInstance = asEntity().getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttributeInstance != null) {
            attackAttributeInstance.removeModifier(DAMAGE_ID);
            AttributeModifier damageModifier = new AttributeModifier(DAMAGE_ID, damageMultiplier - 1.0f, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            attackAttributeInstance.addTransientModifier(damageModifier);
        }
    }

    default boolean setAgeState(AgeState state) {
        AgeState old = getAgeState();
        if (state == old) {
            return false;
        }

        setTrackedValue(AGE_STATE, state);
        asEntity().refreshDimensions();
        updateAttributes();

        return old != AgeState.UNASSIGNED;
    }

    default float getHorizontalScaleFactor() {
        if (getGenetics() == null || Config.getInstance().useSquidwardModels) {
            return asEntity().isBaby() ? 0.5f : 1.0f;
        } else {
            return Math.min(0.999f, getRawHorizontalScaleFactor());
        }
    }

    default float getRawHorizontalScaleFactor() {
        return getGenetics().getHorizontalScaleFactor()
               * getTraits().getHorizontalScaleFactor()
               * getVillagerDimensions().getWidth()
               * getGenetics().getGender().getHorizontalScaleFactor();
    }

    default float getVerticalScaleFactor() {
        return Math.min(0.999f, getRawVerticalScaleFactor());
    }

    default float getRawVerticalScaleFactor() {
        if (getGenetics() == null || Config.getInstance().useSquidwardModels) {
            return asEntity().isBaby() ? 0.5f : 1.0f;
        } else {
            return getGenetics().getVerticalScaleFactor()
                   * getTraits().getVerticalScaleFactor()
                   * getVillagerDimensions().getHeight()
                   * getGenetics().getGender().getScaleFactor();
        }
    }

    @Override
    default DialogueType getDialogueType(Player receiver) {
        if (!receiver.level().isClientSide()) {
            // age specific
            DialogueType type = DialogueType.fromAge(getAgeState());

            // relationship specific
            if (!receiver.level().isClientSide()) {
                Optional<EntityRelationship> r = EntityRelationship.of(asEntity());
                if (r.isPresent()) {
                    FamilyTreeNode relationship = r.get().getFamilyEntry();
                    if (r.get().isMarriedTo(receiver.getUUID())) {
                        return DialogueType.SPOUSE;
                    } else if (r.get().isEngagedWith(receiver.getUUID())) {
                        return DialogueType.ENGAGED;
                    } else if (relationship.isParent(receiver.getUUID())) {
                        return type.toChild();
                    }
                }
            }

            // also sync with client
            getVillagerBrain().getMemoriesForPlayer(receiver).setDialogueType(type);
        }

        return getVillagerBrain().getMemoriesForPlayer(receiver).getDialogueType();
    }

    default void initializeSkin(boolean isPlayer) {
        randomizeClothes();
        randomizeHair();

        //colored hair
        if (!isPlayer) {
            Mob entity = asEntity();
            if (entity.getRandom().nextFloat() < Config.getInstance().coloredHairChance) {
                int n = entity.getRandom().nextInt(25);
                int o = DyeColor.values().length;
                int p = n % o;
                int q = (n + 1) % o;
                float r = entity.getRandom().nextFloat();
                int fs = DyeColor.byId(p).getTextureDiffuseColor();
                int gs = DyeColor.byId(q).getTextureDiffuseColor();
                int color = ARGB.srgbLerp(r, fs, gs);
                setTrackedValue(HAIR_COLOR_RED, ARGB.red(color) / 255.0f);
                setTrackedValue(HAIR_COLOR_GREEN, ARGB.green(color) / 255.0f);
                setTrackedValue(HAIR_COLOR_BLUE, ARGB.blue(color) / 255.0f);
            }
        }
    }

    default void randomizeClothes() {
        setClothes(ClothingList.getInstance().getPool(this).pickOne());
    }

    default void randomizeHair() {
        setHair(HairList.getInstance().getPool(getGenetics().getGender()).pickOne());
    }

    default void validateClothes() {
        if (!asEntity().level().isClientSide()) {
            if (!getClothes().startsWith("immersive_library") && !ClothingList.getInstance().clothing.containsKey(getClothes())) {
                MCA.LOGGER.info("Villagers clothing {} does not exist!", getClothes());
                randomizeClothes();
            }

            if (!getHair().startsWith("immersive_library") && !HairList.getInstance().hair.containsKey(getHair())) {
                MCA.LOGGER.info("Villagers hair {} does not exist!", getHair());
                randomizeHair();
            }
        }
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    default CompoundTag toNbtForConversion() {
        CompoundTag output = new CompoundTag();
        this.getTypeDataManager().save((E) asEntity(), output);
        return output;
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    default void readNbtForConversion(CompoundTag input) {
        this.getTypeDataManager().load((E) asEntity(), input);
    }

    void readAdditionalSaveDataForEditor(CompoundTag nbt);

    default void syncFromEditor(CompoundTag nbt) {
        Mob entity = asEntity();
        readAdditionalSaveDataForEditor(nbt);
        parseCustomName(entity.registryAccess(), nbt).ifPresent(entity::setCustomName);
    }

    default void copyVillagerAttributesFrom(VillagerLike<?> other) {
        readNbtForConversion(other.toNbtForConversion());
        this.setVillagerData(other.getVillagerData());
    }

    default boolean isHostile() {
        return false;
    }

    default PlayerModel getPlayerModel() {
        return PlayerModel.VILLAGER;
    }

    boolean isBurned();

    default void spawnBurntParticles() {
        RandomSource random = asEntity().getRandom();
        if (random.nextInt(4) == 0) {
            double d = random.nextGaussian() * 0.02;
            double e = random.nextGaussian() * 0.02;
            double f = random.nextGaussian() * 0.02;
            asEntity().level().addParticle(ParticleTypes.SMOKE, asEntity().getRandomX(1.0), asEntity().getRandomY() + 1.0, asEntity().getRandomZ(1.0), d, e, f);
        }
    }

    enum PlayerModel {
        VILLAGER,
        PLAYER,
        VANILLA;

        private static final PlayerModel[] VALUES = values();

        public static PlayerModel byId(int id) {
            return VALUES[Math.max(0, Math.min(VALUES.length - 1, id))];
        }
    }

    static Optional<Component> parseCustomName(net.minecraft.core.HolderLookup.Provider provider, CompoundTag nbt) {
        Tag customName = nbt.get("CustomName");
        if (customName == null) {
            return Optional.empty();
        }

        Optional<Component> fromNbt = ComponentSerialization.CODEC
                .parse(provider.createSerializationContext(NbtOps.INSTANCE), customName)
                .result();
        if (fromNbt.isPresent()) {
            return fromNbt;
        }

        Optional<String> serialized = nbt.getString("CustomName");
        if (serialized.isEmpty() || MCA.isBlankString(serialized.get())) {
            return Optional.empty();
        }

        String name = serialized.get();
        try {
            return ComponentSerialization.CODEC
                    .parse(provider.createSerializationContext(JsonOps.INSTANCE), GsonHelper.parse(name))
                    .result()
                    .or(() -> Optional.of(Component.literal(name)));
        } catch (Exception exception) {
            MCA.LOGGER.warn("Failed to parse entity custom name {}", name, exception);
            return Optional.of(Component.literal(name));
        }
    }
}
