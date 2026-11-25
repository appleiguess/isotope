package dev.isotopelib.sim.engine;

public final class ReactorPhysics {
    public static final double JOULE_PER_FISSION = 200.0 * 1.602176634e-13;
    public static final double AVOGADRO = 6.02214076e23;

    public static final double U235_SIGMA_F_BARN = 585.1;
    public static final double U235_G_F = 0.976;
    public static final double T_REF = 293.6;

    public static final double V_REF = 2.2e5;

    public static final double SUR_CONSTANT = 60.0 / Math.log(10.0);

    public static final double RATED_FLUX = 2.9e13;

    private ReactorPhysics() {
    }

    public static double inhourRho(double omega, double[] beta, double[] lambda, double genTime) {
        double b = sum(beta);
        double r = omega * genTime / b;
        for (int i = 0; i < beta.length; i++)
            r += beta[i] / b * omega / (omega + lambda[i]);
        return r;
    }

    public static double inhourOmega(double rhoDollars, double[] beta, double[] lambda, double genTime) {
        if (Math.abs(rhoDollars) < 1e-12)
            return 0.0;
        double lo, hi;
        if (rhoDollars > 0) {
            lo = 0.0;
            hi = 1e-3;
            while (inhourRho(hi, beta, lambda, genTime) < rhoDollars) {
                hi *= 2.0;
                if (hi > 1e7)
                    return Double.NaN;
            }
        } else {
            double lamMin = Double.MAX_VALUE;
            for (double l : lambda)
                lamMin = Math.min(lamMin, l);
            lo = -lamMin;
            hi = 0.0;
        }
        for (int i = 0; i < 200; i++) {
            double mid = 0.5 * (lo + hi);
            if (inhourRho(mid, beta, lambda, genTime) < rhoDollars)
                lo = mid;
            else
                hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    public static double periodSeconds(double rhoDollars, double[] beta, double[] lambda, double genTime) {
        double w = inhourOmega(rhoDollars, beta, lambda, genTime);
        if (Double.isNaN(w))
            return 0.0;
        return w == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / w;
    }

    public static double startupRate(double periodSeconds) {
        return Double.isFinite(periodSeconds) && periodSeconds != 0.0 ? SUR_CONSTANT / periodSeconds : 0.0;
    }

    public static double kEff(double rhoDollars, double betaEff) {
        return 1.0 / (1.0 - rhoDollars * betaEff);
    }

    public static double multiplication(double k) {
        return k < 1.0 ? 1.0 / (1.0 - k) : Double.POSITIVE_INFINITY;
    }

    public static double fissionRate(double fissionWatts) {
        return Math.max(0.0, fissionWatts) / JOULE_PER_FISSION;
    }

    public static double maxwellianSigma(double sigma0, double g, double tempK) {
        return Math.sqrt(Math.PI) / 2.0 * g * sigma0 * Math.sqrt(T_REF / Math.max(1.0, tempK));
    }

    public static double meanNeutronSpeed(double tempK) {
        return 2.0 / Math.sqrt(Math.PI) * V_REF * Math.sqrt(Math.max(1.0, tempK) / T_REF);
    }

    public static double uraniumMolarMass(double enrichWtPct) {
        double e = clamp(enrichWtPct / 100.0, 0.0, 1.0);
        return 1.0 / (e / 235.0439 + (1.0 - e) / 238.0508);
    }

    public static double u235Density(double enrichWtPct, double uo2Density, double fuelFraction) {
        double mU = uraniumMolarMass(enrichWtPct);
        double nU = uo2Density * AVOGADRO / (mU + 2.0 * 15.9994) * fuelFraction;
        return nU * clamp(enrichWtPct / 100.0, 0.0, 1.0) * mU / 235.0439;
    }

    private static final double[] CP_T = {25, 100, 150, 200, 250, 275, 290, 300, 310, 320, 330, 340, 344};
    private static final double[] CP_V = {4.15, 4.19, 4.28, 4.46, 4.78, 5.03, 5.22, 5.42, 5.68, 6.04, 6.62, 7.75, 8.60};

    public static double waterCp(double tempC) {
        if (tempC <= CP_T[0])
            return CP_V[0];
        for (int i = 1; i < CP_T.length; i++)
            if (tempC <= CP_T[i]) {
                double f = (tempC - CP_T[i - 1]) / (CP_T[i] - CP_T[i - 1]);
                return CP_V[i - 1] + f * (CP_V[i] - CP_V[i - 1]);
            }
        return CP_V[CP_V.length - 1];
    }

    public static double carnot(double hotK, double coldK) {
        return hotK <= coldK ? 0.0 : 1.0 - coldK / hotK;
    }

    public static double pelletCentreRise(double linearWattsPerMetre, double conductivity) {
        return Math.max(0.0, linearWattsPerMetre) / (4.0 * Math.PI * Math.max(0.1, conductivity));
    }

    public static double wayWigner(double secondsSinceShutdown, double operatingSeconds) {
        double t = Math.max(1.0, secondsSinceShutdown);
        return 0.0622 * (Math.pow(t, -0.2) - Math.pow(t + Math.max(0.0, operatingSeconds), -0.2));
    }

    public static double xenonEquilibrium(double p, double burnRatio) {
        p = Math.max(0.0, p);
        return (1.0 + burnRatio) * p / (1.0 + burnRatio * p);
    }

    public static double xenonAfterTrip(double i0, double x0, double tHours, double lamI, double lamX, double a, double gamma) {
        double k = a * (1.0 - gamma) / (lamX - lamI);
        return k * i0 * Math.exp(-lamI * tHours) + (x0 - k * i0) * Math.exp(-lamX * tHours);
    }

    public static double xenonPeakHours(double i0, double x0, double lamI, double lamX, double a, double gamma) {
        if (a * (1.0 - gamma) * i0 - lamX * x0 <= 0.0)
            return -1.0;
        double k = a * (1.0 - gamma) / (lamX - lamI);
        double ca = k * i0, cb = x0 - k * i0;
        double arg = lamX * cb / (-lamI * ca);
        if (!(arg > 0.0))
            return -1.0;
        double t = Math.log(arg) / (lamX - lamI);
        return t > 0.0 ? t : -1.0;
    }

    public static final class History {
        public double operatingSeconds;
        public double sinceShutdown;

        public double p0;
        public boolean running;

        public void step(double gameSeconds, double power, double timeCompression) {
            double real = gameSeconds * Math.max(1.0, timeCompression);
            if (power > 0.05) {
                if (!running)
                    sinceShutdown = 0.0;
                running = true;
                operatingSeconds = Math.min(operatingSeconds + real, 3.15e7);
                p0 = p0 <= 0.0 ? power : p0 + (power - p0) * Math.min(1.0, gameSeconds / 30.0);
            } else if (power < 0.01 && running) {
                running = false;
                sinceShutdown = 0.0;
            }
            if (!running && operatingSeconds > 0.0)
                sinceShutdown = Math.min(sinceShutdown + real, 3.15e8);
        }
    }

    public static final int CURVE_POINTS = 32;
    public static final double CURVE_HOURS = 48.0;

    public static PhysicsReadout analyze(PlantSim sim, PlantSnapshot s, History h) {
        PlantConfig cfg = sim.cfg;
        CoreModel core = sim.core;
        double beta = sum(cfg.beta);
        double toPcm = beta * 1e5;

        double rho = core.reactivityDollars();
        double period = sim.periodSeconds();
        double k = kEff(rho, beta);
        double sdm = core.reactivityWithRods(1.0, 1.0);
        double ecp = core.criticalRegulatingPosition();

        double fissionW = Math.max(0.0, core.power()) * cfg.ratedThermalMW * 1e6;
        double rate = fissionRate(fissionW);
        double tMod = core.meanCoolantTemp();
        double sigma = maxwellianSigma(U235_SIGMA_F_BARN, U235_G_F, tMod);
        double enrich = core.enrichmentPercent() * Math.exp(-core.meanBurnup() / cfg.u235DepletionBurnup);
        double n235 = u235Density(enrich, cfg.uo2Density, cfg.fuelVolumeFraction);
        double sigmaF = n235 * sigma * 1e-24;
        double flux = rate / Math.max(1e-30, sigmaF * cfg.coreVolumeM3 * 1e6);
        double density = flux / meanNeutronSpeed(tMod);

        double hot = s.hotLegK(), cold = s.coldLegK();
        double cpRated = waterCp(cfg.inletTemp + 0.5 * cfg.ratedLoopDeltaT - 273.15);
        double mdotRated = cfg.ratedThermalMW * 1e6 / (cpRated * 1e3 * cfg.ratedLoopDeltaT);
        double mdot = Math.max(0.0, s.flow()) * mdotRated;
        double cp = waterCp(0.5 * (hot + cold) - 273.15);
        double qCal = mdot * cp * 1e3 * (hot - cold) / 1e6;
        double tSat = Steam.satTemp(s.pressureBar());
        double thermalMW = core.thermalPower() * cfg.ratedThermalMW;
        double qAvg = thermalMW * 1e6 / (cfg.fuelRods * cfg.activeHeightM);
        double fq = core.axialPeaking() * cfg.radialPeaking;
        double qMax = qAvg * fq;
        double rise = pelletCentreRise(qMax, cfg.fuelConductivity);
        double etaC = carnot(Steam.satTemp(cfg.secondaryRatedBar), cfg.condenserK);
        double mwe = sim.electricFraction() * cfg.ratedThermalMW * cfg.turbineEfficiency;

        double decayModel = core.decayHeat();
        double ww = h.running || h.operatingSeconds <= 0 ? -1.0 : wayWigner(h.sinceShutdown, h.operatingSeconds) * h.p0;

        double lamI = Math.log(2) / cfg.iodineHalfLifeHours, lamX = Math.log(2) / cfg.xenonHalfLifeHours;
        double b = cfg.xenonBurnRatio * lamX, a = lamX + b, g = cfg.xenonDirectShare;
        double x0 = core.meanXenon(), i0 = core.meanIodine();
        double worth = cfg.xenonEquilibriumWorth;
        double[] curve = new double[CURVE_POINTS];
        for (int j = 0; j < CURVE_POINTS; j++)
            curve[j] = -worth * xenonAfterTrip(i0, x0, CURVE_HOURS * j / (CURVE_POINTS - 1), lamI, lamX, a, g);
        double peakH = xenonPeakHours(i0, x0, lamI, lamX, a, g);
        double peakWorth = -worth * (peakH > 0 ? xenonAfterTrip(i0, x0, peakH, lamI, lamX, a, g) : x0);

        double fbHzp = cfg.kDoppler * (Math.sqrt(Math.max(cfg.inletTemp, cfg.minFeedbackFuelTemp)) - Math.sqrt(cfg.refFuelTemp))
                + cfg.kModerator * (Math.max(cfg.inletTemp, cfg.minFeedbackCoolantTemp) - cfg.refCoolantTemp);
        double restart = core.reactivityWithRods(0.0, 0.0) - core.feedbackDollars() + fbHzp;
        double deadFrom = -1, deadTo = -1;
        for (double t = 0; t <= 72.0; t += 0.05) {
            double avail = restart - worth * (xenonAfterTrip(i0, x0, t, lamI, lamX, a, g) - x0);
            if (avail < 0) {
                if (deadFrom < 0)
                    deadFrom = t;
                deadTo = t;
            }
        }

        return new PhysicsReadout(
                rho, rho * toPcm, beta, cfg.generationTime, period, startupRate(period), k, multiplication(k), core.isPrompt(),
                sdm, ecp, core.dopplerCoefficient() * toPcm, core.moderatorCoefficient() * toPcm, core.voidCoefficient() * toPcm,
                cfg.boronWorthPerPpm * toPcm, core.precursorShares(),
                thermalMW, rate, tMod, sigma, n235, sigmaF, flux, density,
                mdot, cp, qCal, tSat, tSat - hot, qAvg / 1000.0, qMax / 1000.0, fq, rise, etaC, cfg.turbineEfficiency, mwe,
                decayModel, ww, h.running ? 0.0 : h.sinceShutdown, h.operatingSeconds,
                x0, i0, xenonEquilibrium(Math.min(1.5, core.power()), cfg.xenonBurnRatio), peakH, peakWorth, curve, deadFrom, deadTo,
                restart, cfg.timeCompression);
    }

    static double sum(double[] a) {
        double s = 0;
        for (double v : a)
            s += v;
        return s;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
