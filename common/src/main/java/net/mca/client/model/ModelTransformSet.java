package net.mca.client.model;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

public interface ModelTransformSet {
    interface Op {
        Op KEEP = (delta, a, b) -> a;
        Op SET = (delta, a, b) -> b;
        Op ADD = (delta, a, b) -> a + b;
        Op LERP = Mth::lerp;

        float apply(float delta, float a, float b);
    }

    Transformer get(String key);

    default ModelTransformSet interpolate(ModelTransformSet to, float delta) {
        if (delta <= 0) return this; // skip on the ends
        if (delta >= 1) return to;
        // variables can't be modified by lambdas, but arrays can
        Transformer[] components = new Transformer[2];
        Transformer combined = (part, op, scale) -> {
            components[0].applyTo(part, Op.LERP, delta);
            components[1].applyTo(part, Op.LERP, 1 - delta);
        };
        return key -> {
            components[0] = Preconditions.checkNotNull(get(key), "Cannot interpolate because the source set was missing key `" + key + "`");
            components[1] = Preconditions.checkNotNull(to.get(key), "Cannot interpolate because the target set was missing key `" + key + "`");
            return combined;
        };
    }

    interface Transformer {
        default void applyTo(ModelPart part) {
            applyTo(part, Op.SET, 1);
        }

        void applyTo(ModelPart part, Op op, float scale);
    }

    class Builder {
        private static final float TO_RADIANS = (float)Math.PI / 180F;
        private final Map<String, Transformer> transforms = new HashMap<>();

        public Builder with(String key, float x, float y, float z, float pitch, float yaw, float roll) {
            return with(key, x, y, z, pitch, yaw, roll, Op.SET, Op.SET);
        }

        public Builder with(String key, float x, float y, float z, float pitch, float yaw, float roll, Op pivot) {
            return with(key, x, y, z, pitch, yaw, roll, pivot, Op.SET);
        }

        public Builder rotate(String key, float pitch, float yaw, float roll) {
            return rotate(key, pitch, yaw, roll, Op.SET);
        }

        public Builder rotate(String key, float pitch, float yaw, float roll, Op op) {
            return with(key, 0, 0, 0, pitch, yaw, roll, Op.KEEP, op);
        }

        public Builder with(String key, float x, float y, float z, float pitch, float yaw, float roll, Op pivot, Op rotate) {
            PartPose transform = createTransform(x, y, z, pitch, yaw, roll);
            transforms.put(key, (part, op, delta) -> {
                part.x = op.apply(delta, part.x, pivot.apply(delta, part.x, transform.x));
                part.y = op.apply(delta, part.y, pivot.apply(delta, part.y, transform.y));
                part.z = op.apply(delta, part.z, pivot.apply(delta, part.z, transform.z));
                part.xRot = op.apply(delta, part.xRot, rotate.apply(delta, part.xRot, transform.xRot));
                part.yRot = op.apply(delta, part.yRot, rotate.apply(delta, part.yRot, transform.yRot));
                part.zRot = op.apply(delta, part.zRot, rotate.apply(delta, part.zRot, transform.zRot));
            });
            return this;
        }

        public static PartPose createTransform(float x, float y, float z, float pitch, float yaw, float roll) {
            return PartPose.offsetAndRotation(x, y, z, pitch * TO_RADIANS, yaw * TO_RADIANS, roll * TO_RADIANS);
        }

        public ModelTransformSet build() {
            return new HashMap<>(transforms)::get;
        }
    }
}
