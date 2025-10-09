package net.conczin.mca.resources.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Analysis implements Iterable<Analysis.AnalysisElement> {
    public static final StreamCodec<FriendlyByteBuf, Analysis> STREAM_CODEC = StreamCodec.composite(
            SerializablePair.streamCodec(ByteBufCodecs.STRING_UTF8, ByteBufCodecs.INT).apply(ByteBufCodecs.list()), Analysis::getSummands,
            Analysis::new
    );
    private final List<SerializablePair<String, Integer>> summands = new LinkedList<>();

    public Analysis() {
    }

    public Analysis(List<SerializablePair<String, Integer>> summands) {
        this.summands.addAll(summands);
    }

    public void add(String key, Integer value) {
        summands.add(new SerializablePair<>(key, value));
    }

    public List<SerializablePair<String, Integer>> getSummands() {
        return summands;
    }

    @NotNull
    @Override
    public Iterator<AnalysisElement> iterator() {
        return new Iterator<>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < summands.size();
            }

            @Override
            public AnalysisElement next() {
                SerializablePair<String, Integer> pair = summands.get(i++);
                return new AnalysisElement(isPositive(pair.right()), asString(pair.right()), pair.left());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public boolean isPositive(Integer v) {
        return v >= 0;
    }

    public String asString(Integer v) {
        return String.valueOf(v);
    }

    public Integer getTotal() {
        return getSummands().stream().mapToInt(SerializablePair::right).sum();
    }

    public String getTotalAsString() {
        float positive = getSummands().stream().mapToInt(SerializablePair::right).filter(v -> v > 0).sum();
        float negative = -getSummands().stream().mapToInt(SerializablePair::right).filter(v -> v < 0).sum();
        int chance = (int) (positive / (positive + negative) * 100.0f);
        return chance + "% chance";
    }

    public record AnalysisElement(boolean positive, String value, String key) {
    }
}
