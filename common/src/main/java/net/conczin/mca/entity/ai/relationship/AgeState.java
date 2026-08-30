package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.Config;
import net.conczin.mca.resources.API;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

public enum AgeState implements VillagerDimensions {
    UNASSIGNED(1, 0.9F, 1, 1, 1.0f, 1.0f),
    BABY(0.45F, 0.4F, 0, 1.5F, 0.0f, 1.6f),
    TODDLER(0.6F, 0.55F, 0, 1.3F, 0.65f, 1.4f),
    CHILD(0.7F, 0.65F, 0, 1.2F, 0.9f, 1.2f),
    TEEN(0.85F, 0.85F, 0.5F, 1, 1.05f, 1.0f),
    ADULT(1, 1, 1, 1, 1.0f, 1.0f);

    private static final AgeState[] VALUES = values();

    private final float width;
    private final float height;
    private final float breasts;
    private final float head;
    private final float speed;
    private final float pitch;

    AgeState(float width, float height, float breasts, float head, float speed, float pitch) {
        this.width = width;
        this.height = height;
        this.breasts = breasts;
        this.head = head;
        this.speed = speed;
        this.pitch = pitch;
    }

    public static int getMaxAge() {
        return Config.getServerConfig().villagerMaxAgeTime;
    }

    public static int getStageDuration() {
        return getMaxAge() / 4;
    }

    public static AgeState byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return UNASSIGNED;
        }
        return VALUES[id];
    }

    public static AgeState random() {
        return byCurrentAge((int) (-API.getRng().nextFloat() * getMaxAge()));
    }

    /**
     * Returns a float ranging from 0 to 1 representing the progress between stages.
     */
    public static float getDelta(float age) {
        float stageDuration = getStageDuration();
        return Mth.positiveModulo(age + getMaxAge(), stageDuration) / stageDuration;
    }

    public static int getId(int age) {
        return Mth.clamp(1 + (age + getMaxAge()) / getStageDuration(), 0, 5);
    }

    public static AgeState byCurrentAge(int age) {
        return byId(getId(age));
    }

    public Component getName() {
        return Component.translatable("enum.agestate." + name().toLowerCase(Locale.ENGLISH));
    }

    @Override
    public float getWidth() {
        return width;
    }

    @Override
    public float getHeight() {
        return height;
    }

    @Override
    public float getBreasts() {
        return breasts;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public float getHead() {
        return head;
    }

    public float getSpeed() {
        return speed;
    }

    public AgeState getNext() {
        if (this == ADULT) {
            return this;
        }
        return byId(ordinal() + 1);
    }

    public int toAge() {
        return (ordinal() - 1) * getStageDuration() - getMaxAge();
    }
}
