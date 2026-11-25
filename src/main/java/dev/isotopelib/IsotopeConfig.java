package dev.isotopelib;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Same defaults as Going Nuclear had before the split. */
public final class IsotopeConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RADIATION = B
            .comment("If false, nobody takes a dose from anything.")
            .define("radiation", true);
    public static final ModConfigSpec.BooleanValue ITEM_RADIATION = B
            .comment("If false, items, blocks and machines do not radiate. Fallout and reactors still do.")
            .define("itemRadiation", true);
    public static final ModConfigSpec.BooleanValue SICKNESS = B
            .comment("If false, a dose is only counted and has no effect on the player.")
            .define("sickness", true);
    public static final ModConfigSpec.BooleanValue FALLOUT_KILLS_PLANTS = B
            .comment("If false, fallout leaves grass, leaves, flowers and crops alone.")
            .define("falloutKillsPlants", true);
    public static final ModConfigSpec.DoubleValue DOSE_TIME_SCALE = B
            .comment("Time scale of exposure: one game second counts as this many real seconds of exposure.")
            .defineInRange("doseTimeScale", 20000.0, 0.0, 1.0e9);
    public static final ModConfigSpec.DoubleValue FALLOUT_DOSE = B
            .comment("Dose rate in fallout of strength 1 under the open sky, in microsieverts per second. A large reactor explosion is a few thousand at its centre.")
            .defineInRange("falloutDoseRate", 1.0, 0.0, 1.0e6);
    public static final ModConfigSpec.DoubleValue DOSE_RECOVERY = B
            .comment("How many millisieverts of dose the body repairs every second.")
            .defineInRange("doseRecoveryMsvPerSecond", 0.05, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DOSE_HALF_LIFE = B
            .comment("The dose halves in this many seconds. 0 turns it off.")
            .defineInRange("doseHalfLifeSeconds", 600.0, 0.0, 1.0e7);
    public static final ModConfigSpec.DoubleValue BLOCK_THICKNESS_CM = B
            .comment("How many centimetres of material one block counts as when radiation passes through it.")
            .defineInRange("blockThicknessCm", 10.0, 0.1, 1000.0);

    public static final ModConfigSpec SPEC = B.build();

    private IsotopeConfig() {
    }
}
