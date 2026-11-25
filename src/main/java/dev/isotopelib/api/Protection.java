package dev.isotopelib.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How much of the radiation a worn piece stops; the four pieces add up. The respirator only counts on the head: nothing over the
 * face means all the dust is breathed in, whatever the rest of the suit is.
 */
public record Protection(double alpha, double beta, double gamma, double respirator) {
    public static final Codec<Protection> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, 1).optionalFieldOf("alpha", 0.0).forGetter(Protection::alpha),
            Codec.doubleRange(0, 1).optionalFieldOf("beta", 0.0).forGetter(Protection::beta),
            Codec.doubleRange(0, 1).optionalFieldOf("gamma", 0.0).forGetter(Protection::gamma),
            Codec.doubleRange(0, 1).optionalFieldOf("respirator", 0.0).forGetter(Protection::respirator)).apply(i, Protection::new));
}
