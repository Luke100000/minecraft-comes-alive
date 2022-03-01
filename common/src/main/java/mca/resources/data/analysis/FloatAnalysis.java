package mca.resources.data.analysis;

import java.io.Serial;
import mca.resources.data.SerializablePair;

public class FloatAnalysis extends Analysis<Float> {
    @Serial
    private static final long serialVersionUID = -3009100555809907786L;

    @Override
    public boolean isPositive(Float v) {
        return v >= 0;
    }

    @Override
    public String asString(Float v) {
        return (int)(v * 100.0f) + "%";
    }

    @Override
    public Float getTotal() {
        return (float)Math.max(0.0, getSummands().stream().mapToDouble(SerializablePair::getRight).sum());
    }
}
