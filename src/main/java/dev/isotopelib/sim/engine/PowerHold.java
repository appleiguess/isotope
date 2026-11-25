package dev.isotopelib.sim.engine;

/** Slow chemical shim: once the rods rest and the power settles, it holds that power against xenon, burnup and temperature drift. */
public final class PowerHold {
    public static final double MIN_POWER = 0.02, MAX_REF = 1.0, SETTLE_SECONDS = 10.0, CALM_OMEGA = 1.0 / 60.0;
    public static final double GAIN = 0.2, MAX_RATE = 0.02, MAX_TRIM = 6.0;

    /** Automatic rod control in REALISTIC: enough for the xenon swings after a power change, not a whole core. */
    public static final double REALISTIC_MAX_TRIM = 3.0;

    private double ref = -1.0, trim, settled;

    public double step(double power, double omega, boolean rodsMoving, boolean active, double dt) {
        return step(power, omega, rodsMoving, active, dt, MAX_TRIM);
    }

    public double step(double power, double omega, boolean rodsMoving, boolean active, double dt, double maxTrim) {
        if (!active || rodsMoving) {
            ref = -1.0;
            settled = 0.0;
            return trim;
        }
        if (ref < 0.0) {
            settled = power >= MIN_POWER && Math.abs(omega) < CALM_OMEGA ? settled + dt : 0.0;
            if (settled >= SETTLE_SECONDS)
                ref = Math.min(power, MAX_REF);
            return trim;
        }
        double rate = Math.max(-MAX_RATE, Math.min(MAX_RATE, GAIN * (ref - power)));
        trim = Math.max(-maxTrim, Math.min(maxTrim, trim + rate * dt));
        return trim;
    }

    public double reference() {
        return ref;
    }

    public double trim() {
        return trim;
    }

    public void load(double reference, double value) {
        ref = reference;
        trim = value;
    }
}
