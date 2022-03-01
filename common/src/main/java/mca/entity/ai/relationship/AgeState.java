package mca.entity.ai.relationship;

import java.util.Locale;
import mca.Config;
import mca.resources.API;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.MathHelper;

public enum AgeState implements VillagerDimensions {
    UNASSIGNED(1, 0.9F, 1, 1, 1.0f),
    BABY      (0.45F, 0.45F, 0, 1.5F, 0.0f),
    TODDLER   (0.6F, 0.6F, 0, 1.3F, 0.6f),
    CHILD     (0.7F, 0.7F, 0, 1.2F, 0.85f),
    TEEN      (0.85F, 0.85F, 0.5F, 1, 1.05f),
    ADULT     (1, 1, 1, 1, 1.0f);

    private static final AgeState[] VALUES = values();

    private final float width;
    private final float height;
    private final float breasts;
    private final float head;
    private final float speed;

    public static int getMaxAge() {
        return Config.getInstance().villagerMaxAgeTime;
    }

    public static int getStageDuration() {
        return getMaxAge() / 4;
    }

    AgeState(float width, float height, float breasts, float head, float speed) {
        this.width = width;
        this.height = height;
        this.breasts = breasts;
        this.head = head;
        this.speed = speed;
    }

    public Text getName() {
        return new TranslatableText("enum.agestate." + name().toLowerCase(Locale.ENGLISH));
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

    public static AgeState byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return UNASSIGNED;
        }
        return VALUES[id];
    }

    public static AgeState random() {
        return byCurrentAge((int)(-API.getRng().nextFloat() * getMaxAge()));
    }

    /**
     * Returns a float ranging from 0 to 1 representing the progress between stages.
     */
    public static float getDelta(float age) {
        return 1 - (-age % getStageDuration()) / getStageDuration();
    }

    public static int getId(int age) {
        return MathHelper.clamp(1 + (age + getMaxAge()) / getStageDuration(), 0, 5);
    }

    public static AgeState byCurrentAge(int age) {
        return byId(getId(age));
    }
}
