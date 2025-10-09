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
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import java.util.*;

public interface VillagerLike<E extends Entity & VillagerLike<E>> extends CTrackedEntity<E>, VillagerDataHolder, Infectable, Messenger {
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

    default void initialize(MobSpawnType spawnReason) {
        if (spawnReason != MobSpawnType.CONVERSION) {
            if (spawnReason != MobSpawnType.BREEDING) {
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
        setTrackedValue(VILLAGER_NAME, name);
        if (!asEntity().level().isClientSide) {
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
            Minecraft minecraftClient = Minecraft.getInstance();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraftClient.getSkinManager().getInsecureSkinInformation(getGameProfile());
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

    default InteractionHand getDominantHand() {
        return getTraits().hasTrait(Traits.LEFT_HANDED) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    default InteractionHand getOpposingHand() {
        return getDominantHand() == InteractionHand.OFF_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    default EquipmentSlot getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }

    default EquipmentSlot getDominantSlot() {
        return getSlotForHand(getDominantHand());
    }

    default EquipmentSlot getOpposingSlot() {
        return getSlotForHand(getOpposingHand());
    }

    default ResourceLocation getProfessionId() {
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

    default void setClothes(ResourceLocation clothes) {
        setClothes(clothes.toString());
    }

    default void setClothes(String clothes) {
        setTrackedValue(CLOTHES, clothes);
    }

    default String getHair() {
        return getTrackedValue(HAIR);
    }

    default void setHair(ResourceLocation hair) {
        setHair(hair.toString());
    }

    default void setHair(String hair) {
        setTrackedValue(HAIR, hair);
    }

    default void setHairDye(DyeColor color) {
        float[] components = color.getTextureDiffuseColors().clone();

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

        AttributeInstance entityAttributeInstance = asEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        if (entityAttributeInstance != null) {
            if (entityAttributeInstance.getModifier(SPEED_ID) != null) {
                entityAttributeInstance.removeModifier(SPEED_ID);
            }
            AttributeModifier speedModifier = new AttributeModifier(SPEED_ID, "Speed", speed - 1.0f, AttributeModifier.Operation.MULTIPLY_BASE);
            entityAttributeInstance.addTransientModifier(speedModifier);
        }
    }

    default boolean setAgeState(AgeState state) {
        AgeState old = getAgeState();
        if (state == old) {
            return false;
        }

        setTrackedValue(AGE_STATE, state);
        asEntity().refreshDimensions();
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
    default DialogueType getDialogueType(Player receiver) {
        if (!receiver.level().isClientSide) {
            // age specific
            DialogueType type = DialogueType.fromAge(getAgeState());

            // relationship specific
            if (!receiver.level().isClientSide) {
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
                float[] fs = Sheep.getColorArray(DyeColor.byId(p));
                float[] gs = Sheep.getColorArray(DyeColor.byId(q));
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
        if (!asEntity().level().isClientSide()) {
            if (!getClothes().startsWith("immersive_library") && !ClothingList.getInstance().clothing.containsKey(getClothes())) {
                //try to port from old versions
                if (getClothes() != null) {
                    ResourceLocation identifier = new ResourceLocation(getClothes());
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
    default CompoundTag toNbtForConversion(EntityType<?> convertingTo) {
        CompoundTag output = new CompoundTag();
        this.getTypeDataManager().save((E) asEntity(), output);
        return output;
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    default void readNbtForConversion(EntityType<?> convertingFrom, CompoundTag input) {
        this.getTypeDataManager().load((E) asEntity(), input);
    }

    default void copyVillagerAttributesFrom(VillagerLike<?> other) {
        readNbtForConversion(other.asEntity().getType(), other.toNbtForConversion(asEntity().getType()));
    }

    static VillagerLike<?> toVillager(PlayerSaveData player) {
        CompoundTag villagerData = player.getEntityData();
        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.get().create(player.getWorld());
        assert villager != null;
        villager.readAdditionalSaveData(villagerData);
        return villager;
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

        static final PlayerModel[] VALUES = values();
    }
}
