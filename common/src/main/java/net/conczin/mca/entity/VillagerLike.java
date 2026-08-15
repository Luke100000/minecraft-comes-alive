package net.conczin.mca.entity;

import com.google.common.base.Strings;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
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
import net.conczin.mca.resources.*;
import net.conczin.mca.util.network.datasync.*;
import net.mca.resources.*;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
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
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

public interface VillagerLike<E extends Entity & VillagerLike<E>> extends CTrackedEntity<E>, VillagerDataHolder, Infectable, Messenger {
    CDataParameter<String> VILLAGER_NAME = CParameter.create("villagerName", "");
    CDataParameter<String> CUSTOM_SKIN = CParameter.create("custom_skin", "");
    CDataParameter<String> CLOTHES = CParameter.create("Clothes", "");
    CDataParameter<String> HAIR = CParameter.create("Hair", "");
    CDataParameter<Boolean> CLOTHING_LOCKED = CParameter.create("ClothingLocked", false);
    CDataParameter<String> SKIN = CParameter.create("Skin", "");
    CDataParameter<String> HAIR_STYLE = CParameter.create("HairStyle", "");
    CDataParameter<String> HAIR_BASE = CParameter.create("HairBase", "");
    CDataParameter<String> HAIR_BANGS = CParameter.create("HairBangs", "");
    CDataParameter<String> HAIR_BACK = CParameter.create("HairBack", "");
    CDataParameter<String> HAIR_FRONT = CParameter.create("HairFront", "");
    CDataParameter<String> HAIR_EXTRA = CParameter.create("HairExtra", "");
    CDataParameter<Integer> SKIN_COLOR = CParameter.create("SkinColor", 0xFF000000);
    CDataParameter<Integer> HAIR_COLOR = CParameter.create("HairColor", 0xFF000000);
    CDataParameter<Integer> EYE_COLOR = CParameter.create("EyeColor", 0xFFFFFFFF);
    CDataParameter<Integer> EYE_COLOR_LEFT = CParameter.create("EyeColorLeft", 0xFFFFFFFF);
    CEnumParameter<AgeState> AGE_STATE = CParameter.create("AgeState", AgeState.UNASSIGNED);

    int NO_HAIR_DYE = 0xFF000000;
    UUID SPEED_ID = UUID.fromString("1eaf83ff-7207-5596-c37a-d7a07b3ec4ce");
    UUID DAMAGE_ID = UUID.fromString("c9f8312d-808a-4a1b-b5ea-4c37f0708667");

    static <E extends Entity> CDataManager.Builder<E> createTrackedData(Class<E> type) {
        return new CDataManager.Builder<>(type)
                .addAll(VILLAGER_NAME, CUSTOM_SKIN, CLOTHES, HAIR, CLOTHING_LOCKED, SKIN, HAIR_STYLE, HAIR_BASE, HAIR_BANGS, HAIR_BACK, HAIR_FRONT, HAIR_EXTRA, SKIN_COLOR, HAIR_COLOR, EYE_COLOR, EYE_COLOR_LEFT, AGE_STATE)
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
        return InteractionHand.MAIN_HAND;
    }

    default InteractionHand getOpposingHand() {
        return InteractionHand.OFF_HAND;
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

    default boolean isClothingLocked() {
        return getTrackedValue(CLOTHING_LOCKED);
    }

    default void setClothingLocked(boolean clothingLocked) {
        setTrackedValue(CLOTHING_LOCKED, clothingLocked);
    }

    default String getSkin() {
        return getTrackedValue(SKIN);
    }

    default void setSkin(String skin) {
        setTrackedValue(SKIN, skin == null ? "" : skin);
    }

    default int getSkinDye() {
        return getTrackedValue(SKIN_COLOR);
    }

    default void setSkinDye(int argb) {
        setTrackedValue(SKIN_COLOR, argb);
    }

    default void clearSkinDye() {
        setSkinDye(0xFF000000);
    }

    default int getEyeDye() {
        return getTrackedValue(EYE_COLOR);
    }

    default void setEyeDye(int argb) {
        setTrackedValue(EYE_COLOR, argb);
    }

    default void clearEyeDye() {
        setEyeDye(0xFFFFFFFF);
    }

    default int getEyeLeftDye() {
        return getTrackedValue(EYE_COLOR_LEFT);
    }

    default void setEyeLeftDye(int argb) {
        setTrackedValue(EYE_COLOR_LEFT, argb);
    }

    default void clearEyeLeftDye() {
        setEyeLeftDye(0xFFFFFFFF);
    }

    default String getHair() {
        return getTrackedValue(HAIR);
    }

    default void setHair(ResourceLocation hair) {
        setHair(hair.toString());
    }

    default void setHair(String hair) {
        setHairStyleId(hair);
    }

    default String getHairStyleId() {
        String hairStyle = getTrackedValue(HAIR_STYLE);
        return MCA.isBlankString(hairStyle) ? getTrackedValue(HAIR) : hairStyle;
    }

    default void setHairStyleId(String hairStyle) {
        setTrackedValue(HAIR_STYLE, hairStyle == null ? "" : hairStyle);
        setTrackedValue(HAIR, "");
    }

    default String getLayeredHair(LayeredHair.Category category) {
        return switch (category) {
            case BASE -> getTrackedValue(HAIR_BASE);
            case BANGS -> getTrackedValue(HAIR_BANGS);
            case BACK -> getTrackedValue(HAIR_BACK);
            case FRONT -> getTrackedValue(HAIR_FRONT);
            case EXTRA -> getTrackedValue(HAIR_EXTRA);
        };
    }

    default void setLayeredHair(LayeredHair.Category category, String value) {
        String safeValue = value == null ? "" : value;
        switch (category) {
            case BASE -> setTrackedValue(HAIR_BASE, safeValue);
            case BANGS -> setTrackedValue(HAIR_BANGS, safeValue);
            case BACK -> setTrackedValue(HAIR_BACK, safeValue);
            case FRONT -> setTrackedValue(HAIR_FRONT, safeValue);
            case EXTRA -> setTrackedValue(HAIR_EXTRA, safeValue);
        }
    }

    default String getHairBase() {
        return getTrackedValue(HAIR_BASE);
    }

    default String getHairBangs() {
        return getTrackedValue(HAIR_BANGS);
    }

    default String getHairBack() {
        return getTrackedValue(HAIR_BACK);
    }

    default String getHairFront() {
        return getTrackedValue(HAIR_FRONT);
    }

    default String getHairExtra() {
        return getTrackedValue(HAIR_EXTRA);
    }

    default void clearLayeredHair() {
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            setLayeredHair(category, "");
        }
    }

    default void setHairStyle(HairStyle style) {
        setHairStyleId(style.getIdentifier());
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            setLayeredHair(category, style.layer(category));
        }
    }

    default void setHairDye(DyeColor color) {
        float[] components = color.getTextureDiffuseColors().clone();

        if (hasHairDye()) {
            float[] dye = unpackHairDyeRgb(getHairDye());
            components[0] = components[0] * 0.5f + dye[0] * 0.5f;
            components[1] = components[1] * 0.5f + dye[1] * 0.5f;
            components[2] = components[2] * 0.5f + dye[2] * 0.5f;
        }

        setHairDye(components[0], components[1], components[2]);
    }

    default void setHairDye(int color) {
        setTrackedValue(HAIR_COLOR, color);
    }

    default void setHairDye(float r, float g, float b) {
        setHairDye(packHairDyeArgb(r, g, b));
    }

    default void clearHairDye() {
        setHairDye(NO_HAIR_DYE);
    }

    default int getHairDye() {
        return getTrackedValue(HAIR_COLOR);
    }

    default boolean hasHairDye() {
        return getHairDye() != NO_HAIR_DYE;
    }

    static int packHairDyeArgb(float r, float g, float b) {
        int red = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
        int green = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
        int blue = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static float[] unpackHairDyeRgb(int argb) {
        if (argb == NO_HAIR_DYE) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f
        };
    }

    default AgeState getAgeState() {
        return getTrackedValue(AGE_STATE);
    }

    default VillagerDimensions getVillagerDimensions() {
        return getAgeState();
    }

    default void updateAttributes() {
        float speed = 1.0f;
        if (getTraits().hasTrait(Traits.ATHLETIC)) {
            speed *= 1.1f;
        }

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

        float damageMultiplier = 1.0f;
        if (getTraits().hasTrait(Traits.WEAK)) {
            damageMultiplier *= 0.75f;
        }
        if (getTraits().hasTrait(Traits.TOUGH)) {
            damageMultiplier *= 1.5f;
        }

        AttributeInstance attackAttributeInstance = asEntity().getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttributeInstance != null) {
            if (attackAttributeInstance.getModifier(DAMAGE_ID) != null) {
                attackAttributeInstance.removeModifier(DAMAGE_ID);
            }
            AttributeModifier damageModifier = new AttributeModifier(DAMAGE_ID, "Damage", damageMultiplier - 1.0f, AttributeModifier.Operation.MULTIPLY_BASE);
            attackAttributeInstance.addTransientModifier(damageModifier);
        }
    }

    default boolean setAgeState(AgeState state) {
        AgeState old = getAgeState();
        if (state == old) {
            return false;
        }

        setTrackedValue(AGE_STATE, state);
        updateAttributes();

        return old != AgeState.UNASSIGNED;
    }

    default float getHorizontalScaleFactor() {
        if (getGenetics() == null || Config.getInstance().useSquidwardModels) {
            return asEntity().isBaby() ? 0.5f : 1.0f;
        }
        return Math.min(0.999f, getRawHorizontalScaleFactor());
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
        }
        return getGenetics().getVerticalScaleFactor()
                * getTraits().getVerticalScaleFactor()
                * getVillagerDimensions().getHeight()
                * getGenetics().getGender().getScaleFactor();
    }

    /**
     * Legacy alias retained for 1.20.1 renderer integrations.
     */
    default float getRawScaleFactor() {
        return getRawVerticalScaleFactor();
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
        randomizeSkin();
        randomizeClothes();
        randomizeHair();

        // Colored hair is only randomized for NPCs, never for player-editor previews.
        if (!isPlayer) {
            Mob entity = asEntity();
            if (entity.getRandom().nextFloat() < Config.getInstance().coloredHairChance) {
                int n = entity.getRandom().nextInt(25);
                int count = DyeColor.values().length;
                int first = n % count;
                int second = (n + 1) % count;
                float mix = entity.getRandom().nextFloat();
                float[] firstColor = DyeColor.byId(first).getTextureDiffuseColors();
                float[] secondColor = DyeColor.byId(second).getTextureDiffuseColors();
                setHairDye(
                        firstColor[0] * (1.0f - mix) + secondColor[0] * mix,
                        firstColor[1] * (1.0f - mix) + secondColor[1] * mix,
                        firstColor[2] * (1.0f - mix) + secondColor[2] * mix
                );
            }
        }
    }

    default void randomizeClothes() {
        if (isClothingLocked()) {
            return;
        }
        forceRandomizeClothes();
    }

    default void forceRandomizeClothes() {
        setClothes(ClothingList.getInstance().getPool(this).pickOne());
    }

    default void randomizeSkin() {
        BodySkinList list = BodySkinList.getInstance();
        if (list == null) {
            setSkin("");
            return;
        }
        setSkin(list.getPool(getGenetics().getGender()).pickOne());
    }

    default void randomizeHair() {
        HairStyleList styles = HairStyleList.getInstance();
        Gender gender = getGenetics().getGender();

        if (styles != null) {
            HairStyle style = styles.pick(gender);
            if (style != null) {
                setHairStyle(style);
                return;
            }
        }

        clearLayeredHair();
        setHairStyleId("");
    }

    default void validateClothes() {
        if (!asEntity().level().isClientSide()) {
            migrateLegacyHairStyle();

            if (!MCA.isBlankString(getSkin()) && !SkinVisualIds.isBodySkin(getSkin())) {
                MCA.LOGGER.info("Villagers skin {} does not exist!", getSkin());
                randomizeSkin();
            }

            if (!SkinVisualIds.isClothing(getClothes())) {
                // Keep the old identifier migration before randomizing invalid clothing.
                if (!MCA.isBlankString(getClothes())) {
                    ResourceLocation identifier = ResourceLocation.tryParse(getClothes());
                    if (identifier != null) {
                        String migrated = identifier.getNamespace() + ":skins/clothing/normal/" + identifier.getPath();
                        if (SkinVisualIds.isClothing(migrated)) {
                            setClothes(migrated);
                        } else {
                            MCA.LOGGER.info("Villagers clothing {} does not exist!", getClothes());
                            forceRandomizeClothes();
                        }
                    } else {
                        forceRandomizeClothes();
                    }
                } else {
                    forceRandomizeClothes();
                }
            }

            if (!MCA.isBlankString(getHairStyleId()) && !SkinVisualIds.isHairStyle(getHairStyleId())) {
                MCA.LOGGER.info("Villagers hair style {} does not exist!", getHairStyleId());
                randomizeHair();
            }

            for (LayeredHair.Category category : LayeredHair.Category.values()) {
                String hair = getLayeredHair(category);
                if (!MCA.isBlankString(hair) && !SkinVisualIds.isHairLayer(hair, category)) {
                    MCA.LOGGER.info("Villagers layered hair {} does not exist!", hair);
                    randomizeHair();
                    break;
                }
            }
        }
    }

    default void migrateLegacyHairStyle() {
        String legacyHair = getTrackedValue(HAIR);
        if (MCA.isBlankString(getTrackedValue(HAIR_STYLE)) && !MCA.isBlankString(legacyHair)) {
            HairStyleList styles = HairStyleList.getInstance();
            HairStyle style = styles == null ? null : styles.get(legacyHair);
            setHairStyle(style == null ? HairStyle.singleLayer(legacyHair, getGenetics().getGender(), 1.0F) : style);
            return;
        }

        String hairStyle = getHairStyleId();
        if (!MCA.isBlankString(hairStyle) && !hasLayeredHair()) {
            HairStyleList styles = HairStyleList.getInstance();
            HairStyle style = styles == null ? null : styles.get(hairStyle);
            if (style != null || isImmersiveLibraryId(hairStyle)) {
                setHairStyle(style == null ? HairStyle.singleLayer(hairStyle, getGenetics().getGender(), 1.0F) : style);
            }
        }
    }

    private static boolean isImmersiveLibraryId(String identifier) {
        return identifier != null && identifier.startsWith("immersive_library");
    }

    private boolean hasLayeredHair() {
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            if (!MCA.isBlankString(getLayeredHair(category))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static <T extends Mob> T convertPreservingUuid(Mob source, EntityType<T> type, boolean keepEquipment,
                                                         ToDoubleFunction<EquipmentSlot> dropChanceGetter) {
        if (source.isRemoved()) {
            return null;
        }

        T converted = type.create(source.level());
        if (converted == null) {
            return null;
        }

        Entity vehicle = source.getVehicle();
        converted.copyPosition(source);
        converted.setBaby(source.isBaby());
        converted.setNoAi(source.isNoAi());
        if (source.hasCustomName()) {
            converted.setCustomName(source.getCustomName());
            converted.setCustomNameVisible(source.isCustomNameVisible());
        }
        if (source.isPersistenceRequired()) {
            converted.setPersistenceRequired();
        }
        converted.setInvulnerable(source.isInvulnerable());
        if (keepEquipment) {
            converted.setCanPickUpLoot(source.canPickUpLoot());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = source.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    converted.setItemSlot(slot, stack.copyAndClear());
                    converted.setDropChance(slot, (float) dropChanceGetter.applyAsDouble(slot));
                }
            }
        }
        converted.setUUID(source.getUUID());

        source.discard();
        source.level().addFreshEntity(converted);
        if (vehicle != null) {
            converted.startRiding(vehicle, true);
        }
        return converted;
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    default CompoundTag toNbtForConversion() {
        CompoundTag output = new CompoundTag();
        this.getTypeDataManager().save((E) asEntity(), output);
        writeAdditionalConversionData(output);
        return output;
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    default void readNbtForConversion(CompoundTag input) {
        this.getTypeDataManager().load((E) asEntity(), input);
        readAdditionalConversionData(input);
    }

    default void writeAdditionalConversionData(CompoundTag output) {
    }

    default void readAdditionalConversionData(CompoundTag input) {
    }

    void readAdditionalSaveDataForEditor(CompoundTag nbt);

    default void syncFromEditor(CompoundTag nbt) {
        Mob entity = asEntity();
        readAdditionalSaveDataForEditor(nbt);

        if (nbt.contains("CustomName", 8)) {
            String name = nbt.getString("CustomName");
            if (!MCA.isBlankString(name)) {
                try {
                    entity.setCustomName(cleanCustomName(Component.Serializer.fromJson(name)));
                } catch (Exception exception) {
                    MCA.LOGGER.warn("Failed to parse entity custom name {}", name, exception);
                    entity.setCustomName(Component.literal(name));
                }
            }
        }
    }


    default void copyVillagerAttributesFrom(VillagerLike<?> other) {
        readNbtForConversion(other.toNbtForConversion());
    }

    static VillagerLike<?> toVillager(PlayerSaveData player) {
        CompoundTag villagerData = player.getEntityData();
        Gender gender = player.getGender();
        if (gender != Gender.UNASSIGNED) {
            CompoundTag mcaData = villagerData.contains(VillagerEntityMCA.MCA_DATA_KEY, 10)
                    ? villagerData.getCompound(VillagerEntityMCA.MCA_DATA_KEY)
                    : villagerData;
            Genetics.writeGender(mcaData, gender);
        }
        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.get().create(player.getWorld());
        if (villager == null) {
            return null;
        }
        villager.readAdditionalSaveDataForEditor(villagerData);
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

    static Component cleanCustomName(Component name) {
        if (name == null) {
            return null;
        }
        String string = name.getString();
        if (string.startsWith("\"") && string.endsWith("\"") && string.length() >= 2) {
            return Component.literal(string.substring(1, string.length() - 1)).setStyle(name.getStyle());
        }
        return name;
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
}
