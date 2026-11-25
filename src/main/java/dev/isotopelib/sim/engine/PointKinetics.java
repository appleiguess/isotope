package dev.isotopelib.sim.engine;

public final class PointKinetics {
    public static final double SUB_STEP = 0.02;

    private final double[] f = new double[6];
    private final double[] lambda;
    private final double[] c = new double[6];
    private double betaOverGen;
    private double n;
    private double omega;
    private boolean prompt;

    public PointKinetics(double[] beta, double[] lambda, double genTime) {
        this.lambda = lambda.clone();
        setBeta(beta, genTime);
    }

    public void setBeta(double[] beta, double genTime) {
        double b = 0;
        for (double v : beta)
            b += v;
        for (int g = 0; g < 6; g++)
            f[g] = beta[g] / b;
        betaOverGen = b / genTime;
    }

    public void setEquilibrium(double power) {
        n = Math.max(0.0, power);
        for (int g = 0; g < 6; g++)
            c[g] = f[g] * n;
        omega = 0.0;
        prompt = false;
    }

    public void step(double rhoDollars, double source, double dt, double cap) {
        if (dt <= 0)
            return;
        int parts = Math.max(1, (int) Math.ceil(dt / SUB_STEP));
        double h = dt / parts, n0 = n;
        prompt = rhoDollars >= 1.0;
        double a = betaOverGen * (rhoDollars - 1.0), ah = a * h;
        double[] keep = new double[6];
        for (int g = 0; g < 6; g++)
            keep[g] = Math.exp(-lambda[g] * h);
        for (int k = 0; k < parts; k++) {
            double sum = 0;
            for (double v : c)
                sum += v;
            double q = betaOverGen * (sum + Math.max(0.0, source));
            double n1, mean;
            if (Math.abs(ah) < 1e-9) {
                n1 = n + (a * n + q) * h;
                mean = 0.5 * (n + n1);
            } else {
                double level = -q / a, e = Math.exp(Math.min(60.0, ah));
                n1 = level + (n - level) * e;
                mean = level + (n - level) * (e - 1.0) / ah;
            }
            n1 = Math.max(0.0, Math.min(cap, n1));
            mean = Math.max(0.0, Math.min(cap, mean));
            for (int g = 0; g < 6; g++)
                c[g] = c[g] * keep[g] + f[g] * mean * (1.0 - keep[g]);
            n = n1;
        }
        omega = n0 > 0 && n > 0 ? Math.log(n / n0) / dt : 0.0;
    }

    public double power() {
        return n;
    }

    public void setPower(double p) {
        n = Math.max(0.0, p);
    }

    public double omega() {
        return omega;
    }

    public double periodSeconds() {
        return Math.abs(omega) < 1e-6 ? Double.POSITIVE_INFINITY : 1.0 / omega;
    }

    public boolean prompt() {
        return prompt;
    }

    public double[] shares() {
        double s = 0;
        for (double v : c)
            s += v;
        double[] out = new double[6];
        for (int g = 0; g < 6; g++)
            out[g] = s > 0 ? c[g] / s : f[g];
        return out;
    }

    public void save(java.util.List<Double> out) {
        for (double v : c)
            out.add(v);
        out.add(n);
    }

    public int load(double[] a, int k) {
        for (int g = 0; g < 6; g++)
            c[g] = a[k++];
        n = a[k++];
        omega = 0.0;
        return k;
    }

    public static int saveSize() {
        return 7;
    }
}
