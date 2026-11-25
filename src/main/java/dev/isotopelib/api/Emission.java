package dev.isotopelib.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a source gives off: its activity and how the decays share out between the kinds of radiation (alpha, beta and gamma,
 * together at most 1). Dust also gets into the lungs when carried without protection.
 */
public record Emission(double activityBq, double alpha, double beta, double gamma, boolean dust) {
    public static final Emission NONE = new Emission(0, 0, 0, 0, false);

    public static final Codec<Emission> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, 1e30).fieldOf("activity_bq").forGetter(Emission::activityBq),
            Codec.doubleRange(0, 1).optionalFieldOf("alpha", 0.0).forGetter(Emission::alpha),
            Codec.doubleRange(0, 1).optionalFieldOf("beta", 0.0).forGetter(Emission::beta),
            Codec.doubleRange(0, 1).optionalFieldOf("gamma", 0.0).forGetter(Emission::gamma),
            Codec.BOOL.optionalFieldOf("dust", false).forGetter(Emission::dust)).apply(i, Emission::new));

    public boolean isNone() {
        return activityBq <= 0;
    }

    public Emission scaled(double factor) {
        return new Emission(activityBq * factor, alpha, beta, gamma, dust);
    }
}
