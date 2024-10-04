package net.mca.entity;

import com.google.common.base.Strings;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.ai.DialogueType;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Messenger;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.relationship.*;
import net.mca.entity.interaction.EntityCommandHandler;
import net.mca.resources.ClothingList;
import net.mca.resources.HairList;
import net.mca.resources.Names;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.util.network.datasync.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerDataContainer;

import java.util.*;

public interface VillagerLike<E extends Entity & VillagerLike<E>> extends CTrackedEntity<E>, VillagerDataContainer, Infectable, Messenger {
    CDataParameter<String> VILLAGER_NAME = CParameter.create("villagerName", "");
    CDataParameter<String> CUSTOM_SKIN = CParameter.create("custom_skin", "");
    CDataParameter<String> CLOTHES = CParameter.create("clothes", "");
    CDataParameter<String> HAIR = CParameter.create("hair", "");
    CDataParameter<Float> HAIR_COLOR_RED = CParameter.create("hair_color_red", 0.0f);
    CDataParameter<Float> HAIR_COLOR_GREEN = CParameter.create("hair_color_green", 0.0f);
    CDataParameter<Float> HAIR_COLOR_BLUE = CParameter.create("hair_color_blue", 0.0f);
    CEnumParameter<AgeState> AGE_STATE = CParameter.create("ageState", AgeState.UNASSIGNED);

    UUID SPEED_ID = UUID.fromString("1eaf83ff-7207-5596-c37a-d7a07b3ec4ce");

    static <E extends Entity> CDataManager.Builder<E> createTrackedData(Class<E> type) {
        return new CDataManager.Builder<>(type)
                .addAll(VILLAGER_NAME, CUSTOM_SKIN, CLOTHES, HAIR, HAIR_COLOR_RED, HAIR_COLOR_GREEN, HAIR_COLOR_BLUE, AGE_STATE)
                .add(Genetics::createTrackedData)
                .add(Traits::createTrackedData)
                .add(VillagerBrain::createTrackedData);
    }

    Genetics getGenetics();

    Traits getTraits();

    VillagerBrain<?> getVillagerBrain();

    EntityCommandHandler<?> getInteractions();

    default void initialize(SpawnReason spawnReason) {
        if (spawnReason != SpawnReason.CONVERSION) {
            if (spawnReason != SpawnReason.BREEDING) {
                getGenetics().randomize();
                getTraits().randomize();
            }

            initializeSkin(false);
            getVillagerBrain().randomize();
        }

        if (getGenetics().getGender() == Gender.UNASSIGNED) {
            getGenetics().setGender(Gender.getRandom());
        }

        if (Strings.isNullOrEmpty(getTrackedValue(VILLAGER_NAME))) {
            setName(Names.pickCitizenName(getGenetics().getGender(), asEntity()));
        }

        validateClothes();

        asEntity().calculateDimensions();
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
        setTrackedValue(VILLAGER_NAME, name);
        if (!asEntity().getWorld().isClient) {
            EntityRelationship.of(asEntity()).ifPresent(relationship -> relationship.getFamilyEntry().setName(name));
        }
    }

    default void setCustomSkin(String name) {
        setTrackedValue(CUSTOM_SKIN, name);
    }

    default void updateCustomSkin() {

    }

    default GameProfile getGameProfile() {
        return null;
    }

    default boolean hasCustomSkin() {
        if (!MCA.isBlankString(getTrackedValue(CUSTOM_SKIN)) && getGameProfile() != null) {
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraftClient.getSkinProvider().getTextures(getGameProfile());
            return map.containsKey(MinecraftProfileTexture.Type.SKIN);
        } else {
            return false;
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

    default Hand getDominantHand() {
        return getTraits().hasTrait(Traits.LEFT_HANDED) ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    default Hand getOpposingHand() {
        return getDominantHand() == Hand.OFF_HAND ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    default EquipmentSlot getSlotForHand(Hand hand) {
        return hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }

    default EquipmentSlot getDominantSlot() {
        return getSlotForHand(getDominantHand());
    }

    default EquipmentSlot getOpposingSlot() {
        return getSlotForHand(getOpposingHand());
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

    default MutableText getProfessionText() {
        return Text.translatable("entity.minecraft.villager." + getProfessionName());
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

    default void setHairDye(DyeColor color) {
        float[] components = color.getColorComponents().clone();

        float[] dye = getHairDye();
        if (dye[0] > 0.0f) {
            components[0] = components[0] * 0.5f + dye[0] * 0.5f;
            components[1] = components[1] * 0.5f + dye[1] * 0.5f;
            components[2] = components[2] * 0.5f + dye[2] * 0.5f;
        }

        setTrackedValue(HAIR_COLOR_RED, components[0]);
        setTrackedValue(HAIR_COLOR_GREEN, components[1]);
        setTrackedValue(HAIR_COLOR_BLUE, components[2]);
    }

    default void setHairDye(float r, float g, float b) {
        setTrackedValue(HAIR_COLOR_RED, r);
        setTrackedValue(HAIR_COLOR_GREEN, g);
        setTrackedValue(HAIR_COLOR_BLUE, b);
    }

    default void clearHairDye() {
        setHairDye(0.0f, 0.0f, 0.0f);
    }

    default float[] getHairDye() {
        return new float[]{
                getTrackedValue(HAIR_COLOR_RED),
                getTrackedValue(HAIR_COLOR_GREEN),
                getTrackedValue(HAIR_COLOR_BLUE)
        };
    }

    default AgeState getAgeState() {
        return getTrackedValue(AGE_STATE);
    }

    default VillagerDimensions getVillagerDimensions() {
        return getAgeState();
    }

    default void updateSpeed() {
        //set speed
        float speed = getVillagerBrain().getPersonality().getSpeedModifier();

        speed /= (0.9f + getGenetics().getGene(Genetics.WIDTH) * 0.2f);
        speed *= (0.9f + getGenetics().getGene(Genetics.SIZE) * 0.2f);

        speed *= getAgeState().getSpeed();

        EntityAttributeInstance entityAttributeInstance = asEntity().getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (entityAttributeInstance != null) {
            if (entityAttributeInstance.getModifier(SPEED_ID) != null) {
                entityAttributeInstance.removeModifier(SPEED_ID);
            }
            EntityAttributeModifier speedModifier = new EntityAttributeModifier(SPEED_ID, "Speed", speed - 1.0f, EntityAttributeModifier.Operation.MULTIPLY_BASE);
            entityAttributeInstance.addTemporaryModifier(speedModifier);
        }
    }

    default boolean setAgeState(AgeState state) {
        AgeState old = getAgeState();
        if (state == old) {
            return false;
        }

        setTrackedValue(AGE_STATE, state);
        asEntity().calculateDimensions();
        updateSpeed();

        return old != AgeState.UNASSIGNED;
    }

    default float getHorizontalScaleFactor() {
        if (getGenetics() == null || Config.getInstance().useSquidwardModels) {
            return asEntity().isBaby() ? 0.5f : 1.0f;
        } else {
            return Math.min(0.999f, getGenetics().getHorizontalScaleFactor() * getTraits().getHorizontalScaleFactor() * getVillagerDimensions().getWidth() * getGenetics().getGender().getHorizontalScaleFactor());
        }
    }

    default float getRawScaleFactor() {
        if (getGenetics() == null || Config.getInstance().useSquidwardModels) {
            return asEntity().isBaby() ? 0.5f : 1.0f;
        } else {
            return getGenetics().getVerticalScaleFactor() * getTraits().getVerticalScaleFactor() * getVillagerDimensions().getHeight() * getGenetics().getGender().getScaleFactor();
        }
    }

    @Override
    default DialogueType getDialogueType(PlayerEntity receiver) {
        if (!receiver.getWorld().isClient) {
            // age specific
            DialogueType type = DialogueType.fromAge(getAgeState());

            // relationship specific
            if (!receiver.getWorld().isClient) {
                Optional<EntityRelationship> r = EntityRelationship.of(asEntity());
                if (r.isPresent()) {
                    FamilyTreeNode relationship = r.get().getFamilyEntry();
                    if (r.get().isMarriedTo(receiver.getUuid())) {
                        return DialogueType.SPOUSE;
                    } else if (r.get().isEngagedWith(receiver.getUuid())) {
                        return DialogueType.ENGAGED;
                    } else if (relationship.isParent(receiver.getUuid())) {
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
            MobEntity entity = asEntity();
            if (entity.getRandom().nextFloat() < Config.getInstance().coloredHairChance) {
                int n = entity.getRandom().nextInt(25);
                int o = DyeColor.values().length;
                int p = n % o;
                int q = (n + 1) % o;
                float r = entity.getRandom().nextFloat();
                float[] fs = SheepEntity.getRgbColor(DyeColor.byId(p));
                float[] gs = SheepEntity.getRgbColor(DyeColor.byId(q));
                setTrackedValue(HAIR_COLOR_RED, fs[0] * (1.0f - r) + gs[0] * r);
                setTrackedValue(HAIR_COLOR_GREEN, fs[1] * (1.0f - r) + gs[1] * r);
                setTrackedValue(HAIR_COLOR_BLUE, fs[2] * (1.0f - r) + gs[2] * r);
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
        if (!asEntity().getWorld().isClient()) {
            if (!getClothes().startsWith("immersive_library") && !ClothingList.getInstance().clothing.containsKey(getClothes())) {
                //try to port from old versions
                if (getClothes() != null) {
                    Identifier identifier = new Identifier(getClothes());
                    String id = identifier.getNamespace() + ":skins/clothing/normal/" + identifier.getPath();
                    if (ClothingList.getInstance().clothing.containsKey(id)) {
                        setClothes(id);
                    } else {
                        MCA.LOGGER.info(String.format(Locale.ROOT, "Villagers clothing %s does not exist!", getClothes()));
                        randomizeClothes();
                    }
                } else {
                    MCA.LOGGER.info(String.format(Locale.ROOT, "Villagers clothing %s does not exist!", getClothes()));
                    randomizeClothes();
                }
            }

            if (!getHair().startsWith("immersive_library") && !HairList.getInstance().hair.containsKey(getHair())) {
                MCA.LOGGER.info(String.format(Locale.ROOT, "Villagers hair %s does not exist!", getHair()));
                randomizeHair();
            }
        }
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    default NbtCompound toNbtForConversion(EntityType<?> convertingTo) {
        NbtCompound output = new NbtCompound();
        this.getTypeDataManager().save((E) asEntity(), output);
        return output;
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    default void readNbtForConversion(EntityType<?> convertingFrom, NbtCompound input) {
        this.getTypeDataManager().load((E) asEntity(), input);
    }

    default void copyVillagerAttributesFrom(VillagerLike<?> other) {
        readNbtForConversion(other.asEntity().getType(), other.toNbtForConversion(asEntity().getType()));
    }

    static VillagerLike<?> toVillager(PlayerSaveData player) {
        NbtCompound villagerData = player.getEntityData();
        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.get().create(player.getWorld());
        assert villager != null;
        villager.readCustomDataFromNbt(villagerData);
        return villager;
    }

    static VillagerLike<?> toVillager(Entity entity) {
        if (entity instanceof VillagerLike<?>) {
            return (VillagerLike<?>) entity;
        } else if (entity instanceof ServerPlayerEntity playerEntity) {
            return toVillager(PlayerSaveData.get(playerEntity));
        } else {
            return null;
        }
    }

    default boolean isHostile() {
        return false;
    }

    default PlayerModel getPlayerModel() {
        return PlayerModel.VILLAGER;
    }

    boolean isBurned();

    default void spawnBurntParticles() {
        Random random = asEntity().getRandom();
        if (random.nextInt(4) == 0) {
            double d = random.nextGaussian() * 0.02;
            double e = random.nextGaussian() * 0.02;
            double f = random.nextGaussian() * 0.02;
            asEntity().getWorld().addParticle(ParticleTypes.SMOKE, asEntity().getParticleX(1.0), asEntity().getRandomBodyY() + 1.0, asEntity().getParticleZ(1.0), d, e, f);
        }
    }

    enum PlayerModel {
        VILLAGER,
        PLAYER,
        VANILLA;

        static final PlayerModel[] VALUES = values();
    }
}
