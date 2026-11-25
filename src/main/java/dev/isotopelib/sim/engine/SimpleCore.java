package dev.isotopelib.sim.engine;

public final class SimpleCore {
    public static final double SOURCE = 1e-6;
    public static final double SCRAM_DOLLARS = -8.0;
    public static final double PULL_RATE = 0.1, SUBCRITICAL_PULL_RATE = 1.0, DRIVE_IN_RATE = 1.0, DROP_RATE = 4.0;
    public static final double FUEL_SECONDS = 1.0;
    public static final double LAMBDA_EFF = 0.0767;
    public static final double XENON_WORTH = 0.3;
    public static final double CAP = 3.0;

    private final PlantConfig cfg = new PlantConfig();
    private final PointKinetics kin = new PointKinetics(cfg.beta, cfg.lambda, cfg.generationTime);
    private double rho = SCRAM_DOLLARS, iodine, xenon, fuel;

    public SimpleCore() {
        kin.setEquilibrium(SOURCE / -SCRAM_DOLLARS);
    }

    /** Real seconds per game second for xenon. */
    public void setTimeCompression(double factor) {
        cfg.timeCompression = factor;
    }

    public static double maxPull(double power) {
        double t = Math.max(0.0, Math.min(1.0, Math.log10(Math.max(1e-12, power)) + 3.0));
        return 0.5 - 0.15 * t;
    }

    public double xenonEquilibrium(double p) {
        p = Math.max(0.0, Math.min(3.0, p));
        return (1.0 + cfg.xenonBurnRatio) * p / (1.0 + cfg.xenonBurnRatio * p);
    }

    public void step(double target, boolean scram, boolean fuelled, double tauSeconds, double dt) {
        double want = SCRAM_DOLLARS;
        if (!scram && target > 0) {
            double k = 1.0 / (LAMBDA_EFF * Math.max(0.5, tauSeconds));
            want = k * Math.log((target + SOURCE) / (fuel + SOURCE)) - XENON_WORTH * (xenon - xenonEquilibrium(target));
            want = Math.max(SCRAM_DOLLARS, Math.min(maxPull(kin.power()), want));
        }
        double up = (rho < 0 ? SUBCRITICAL_PULL_RATE : PULL_RATE) * dt;
        rho += Math.max(-(scram ? DROP_RATE : DRIVE_IN_RATE) * dt, Math.min(up, want - rho));
        kin.step(rho, fuelled ? SOURCE : 0.0, dt, CAP);
        fuel += (kin.power() - fuel) * (1.0 - Math.exp(-dt / FUEL_SECONDS));
        poisons(dt);
    }

    private void poisons(double dt) {
        double comp = cfg.timeCompression;
        double lamI = Math.log(2) / (cfg.iodineHalfLifeHours * 3600.0) * comp;
        double lamX = Math.log(2) / (cfg.xenonHalfLifeHours * 3600.0) * comp;
        double b = cfg.xenonBurnRatio * lamX, a = lamX + b;
        double phi = Math.min(3.0, kin.power());
        iodine = Math.max(0, iodine + lamI * (phi - iodine) * dt);
        double dx = a * (cfg.xenonDirectShare * phi + (1 - cfg.xenonDirectShare) * iodine) - lamX * xenon - b * phi * xenon;
        xenon = Math.max(0, xenon + dx * dt);
    }

    public void startAt(double p) {
        p = Math.max(0.0, p);
        kin.setEquilibrium(p);
        rho = 0.0;
        fuel = p;
        iodine = p;
        xenon = xenonEquilibrium(p);
    }

    public double power() {
        return kin.power();
    }

    public double omega() {
        return kin.omega();
    }

    public double reactivityDollars() {
        return rho;
    }

    public double betaEff() {
        return ReactorPhysics.sum(cfg.beta);
    }

    public double iodine() {
        return iodine;
    }

    public double xenon() {
        return xenon;
    }

    public double xenonWorthDollars() {
        return -XENON_WORTH * xenon;
    }

    public double[] save() {
        java.util.List<Double> out = new java.util.ArrayList<>();
        out.add(rho);
        out.add(iodine);
        out.add(xenon);
        out.add(fuel);
        kin.save(out);
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++)
            a[i] = out.get(i);
        return a;
    }

    public boolean load(double[] a) {
        if (a == null || a.length != 4 + PointKinetics.saveSize())
            return false;
        rho = a[0];
        iodine = a[1];
        xenon = a[2];
        fuel = a[3];
        kin.load(a, 4);
        return true;
    }
}
