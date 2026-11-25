package dev.isotopelib.sim.engine;

import static dev.isotopelib.sim.engine.PlantConfig.N;

public final class CoreModel {
    public final PlantConfig cfg;

    private final double[] baseBeta = new double[6];
    private double baseBetaSum;
    private double mox;
    private final PointKinetics kin;
    private double rhoTotal;
    private double rhoRods, rhoBoron, rhoXenon, rhoSamarium, rhoFeedback, rhoFuel;

    private double rodReg = 1.0, rodSd = 0.0;
    private double targetReg = 1.0, targetSd = 0.0;
    private boolean scram;
    private double stuckShutdown;
    private double ejected;
    private double boronPpm, boronTarget;
    private double bonusReactivity;
    private double enrichment;

    private final double[] importance = new double[N];
    private final double[] w = new double[N];
    private final double[] dShape = new double[N];
    private final double[] burnup = new double[N];
    private final double[] iodine = new double[N];
    private final double[] xenon = new double[N];
    private double samarium, promethium;
    private final double[] decay = new double[4];

    private final double[] tf = new double[N], tcl = new double[N], enth = new double[N], alpha = new double[N], alphaF = new double[N];
    private double lagThermal;
    private final double[] cover = new double[N];
    private final boolean[] film = new boolean[N];
    private final double[] ox = new double[N], rupture = new double[N], moltenClad = new double[N], moltenFuel = new double[N];
    private final double[] dnbr = new double[N];
    private double h2Total, h2Step;
    private double gapReleased;
    private double minDnbr = 99.0;
    private double heatToCoolant;
    private double satTemp = 618.0;
    private double outletK;

    public CoreModel(PlantConfig cfg) {
        this.cfg = cfg;
        double sumB = 0;
        for (double b : cfg.beta)
            sumB += b;
        for (int i = 0; i < 6; i++)
            baseBeta[i] = cfg.beta[i];
        baseBetaSum = sumB;
        kin = new PointKinetics(cfg.beta, cfg.lambda, cfg.generationTime);
        kin.setEquilibrium(cfg.sourceLevel);
        double sumI = 0;
        for (int i = 0; i < N; i++) {
            double z = (i + 0.5) / N;
            importance[i] = Math.cos(Math.PI * (z - 0.5));
            sumI += importance[i];
        }
        for (int i = 0; i < N; i++) {
            importance[i] /= sumI;
            w[i] = importance[i];
            dShape[i] = importance[i];
        }
        boronPpm = boronTarget = cfg.boronStartPpm;
        enrichment = cfg.nominalEnrichment;
        for (int i = 0; i < N; i++) {
            tf[i] = tcl[i] = cfg.ambientTemp;
            enth[i] = cfg.ambientTemp;
            cover[i] = 1.0;
            dnbr[i] = 99.0;
        }
    }

    private double weight(int i) {
        double s = 0;
        for (int j = 0; j < N; j++)
            s += w[j] * importance[j];
        return w[i] * importance[i] / s;
    }

    private static double rodCover(double ins, int node) {
        return clamp(ins * N - (N - 1 - node), 0.0, 1.0);
    }

    private static double sqr(double x) {
        return x * x;
    }

    /** Doppler reactivity. Simple mode's stronger coefficient acts only above the operating fuel temperature, so a cold core does not gain reactivity from it. */
    private double doppler(double fuelK) {
        double d = cfg.kDoppler * (Math.sqrt(Math.max(fuelK, cfg.minFeedbackFuelTemp)) - Math.sqrt(cfg.refFuelTemp));
        return fuelK > cfg.refFuelTemp ? d * cfg.dopplerScale : d;
    }

    private double localReactivity(int i) {
        double rho = 0;
        rho += doppler(tf[i]);
        double tc = liquidTemp(enth[i]);
        rho += cfg.kModerator * (Math.max(tc, cfg.minFeedbackCoolantTemp) - cfg.refCoolantTemp);
        rho += cfg.kVoid * alphaF[i] * 100.0 * cover[i];
        rho -= cfg.xenonEquilibriumWorth * (1.0 - 0.4 * mox) * xenon[i];
        double reg = rodCover(rodReg, i), sd = rodCover(rodSd, i);
        rho -= (cfg.regulatingRodWorth * reg + cfg.shutdownRodWorth * (1.0 - stuckShutdown) * sd) * (1.0 - MOX_ROD_LOSS * mox);

        double ex = (cfg.fuelExcessBol - cfg.excessLossPerGWd * burnup[i]) * (enrichment / cfg.nominalEnrichment);
        ex -= cfg.burnableAbsorberWorth * Math.exp(-burnup[i] / cfg.burnableAbsorberBurnup);
        rho += ex;

        rho -= (1.0 - cover[i]) * 6.0;

        rho -= moltenFuel[i] * 20.0;
        return rho;
    }

    private double globalReactivity() {
        double r = cfg.boronWorthPerPpm * boronPpm - cfg.samariumEquilibriumWorth * samarium - ejectedWorth() + bonusReactivity;
        return r;
    }

    private double ejectedWorth() {
        return -ejected;
    }

    private double scramBonus() {
        if (cfg.positiveScramDollars <= 0 || rodSd <= 0 || rodSd >= 0.6)
            return 0;
        return cfg.positiveScramDollars * Math.sin(Math.PI * rodSd / 0.6);
    }

    private double liquidTemp(double e) {
        return Math.min(e, satTemp);
    }

    private double fluidTemp(double e) {
        if (e <= satTemp)
            return e;
        if (e <= satTemp + cfg.latentKelvin)
            return satTemp;
        return satTemp + (e - satTemp - cfg.latentKelvin);
    }

    private double quality(double e) {
        return clamp((e - satTemp) / cfg.latentKelvin, 0.0, 1.0);
    }

    public void setRodTargets(double regulating, double shutdown) {
        targetReg = clamp(regulating, 0, 1);
        targetSd = clamp(shutdown, 0, 1);
    }

    public void setScram(boolean on) {
        scram = on;
    }

    public void setStuckShutdownFraction(double v) {
        stuckShutdown = clamp(v, 0, 1);
    }

    public void ejectRod(double dollars) {
        ejected = Math.max(0, dollars);
    }

    public void setBoronTarget(double ppm) {
        boronTarget = clamp(ppm, 0, cfg.boronMaxPpm);
    }

    public void setBoronNow(double ppm) {
        boronPpm = clamp(ppm, 0, cfg.boronMaxPpm);
        boronTarget = boronPpm;
    }

    public void addBoron(double ppm) {
        boronPpm = clamp(boronPpm + ppm, 0, cfg.boronMaxPpm);
    }

    public static final double MOX_BETA = 0.6;

    public static final double MOX_ROD_LOSS = 0.2;

    public void setMox(double share) {
        mox = clamp(share, 0.0, 1.0);
        double scale = 1.0 - (1.0 - MOX_BETA) * mox;
        for (int i = 0; i < 6; i++)
            cfg.beta[i] = baseBeta[i] * scale;
        kin.setBeta(cfg.beta, cfg.generationTime);
    }

    public double mox() {
        return mox;
    }

    private double dollarScale() {
        double sum = 0;
        for (double b : cfg.beta)
            sum += b;
        return baseBetaSum / sum;
    }

    public void setBonusReactivity(double dollars) {
        bonusReactivity = dollars;
    }

    public void setRodsInstant(double regulating, double shutdown) {
        rodReg = targetReg = clamp(regulating, 0, 1);
        rodSd = targetSd = clamp(shutdown, 0, 1);
    }

    public void setTemperaturesInstant(double fuelK, double coolantK) {
        for (int i = 0; i < N; i++) {
            tf[i] = fuelK;
            tcl[i] = 0.5 * (fuelK + coolantK);
            enth[i] = coolantK;
        }
    }

    public void setEnrichment(double e) {
        enrichment = Math.max(0.1, e);
    }

    public void refuel(double frac) {
        for (int i = 0; i < N; i++)
            burnup[i] *= (1.0 - clamp(frac, 0, 1));
    }

    public void step(double dt, double flow, double inletK, double pBar, double levelFrac, double boost) {
        int parts = (int) Math.ceil(dt / 0.1);
        if (parts > 1) {
            double h = dt / parts;
            for (int i = 0; i < parts; i++)
                stepOnce(h, flow, inletK, pBar, levelFrac, boost);
            return;
        }
        stepOnce(dt, flow, inletK, pBar, levelFrac, boost);
    }

    private void stepOnce(double dt, double flow, double inletK, double pBar, double levelFrac, double boost) {
        satTemp = Steam.satTemp(pBar);
        for (int i = 0; i < N; i++)
            cover[i] = clamp(levelFrac * N - i, 0.0, 1.0);
        moveRods(dt);
        updateBoron(dt);
        kineticsAndShape(dt);
        slowProcesses(dt);
        thermal(dt, flow, inletK, pBar, boost);
    }

    private void moveRods(double dt) {
        if (scram) {
            double d = dt / cfg.scramTime;
            rodSd = Math.min(1.0, rodSd + d);
            rodReg = Math.min(1.0, rodReg + d);
            return;
        }
        double max = dt * cfg.rodSpeed;
        double d1 = targetReg - rodReg, d2 = targetSd - rodSd;
        rodReg += Math.abs(d1) <= max ? d1 : Math.signum(d1) * max;
        rodSd += Math.abs(d2) <= max ? d2 : Math.signum(d2) * max;
    }

    private void updateBoron(double dt) {
        double d = boronTarget - boronPpm;
        double max = dt * cfg.boronRatePpmPerSecond;
        boronPpm += Math.abs(d) <= max ? d : Math.signum(d) * max;
    }

    private void kineticsAndShape(double dt) {
        double[] local = new double[N];
        double rhoBar = 0;
        for (int i = 0; i < N; i++) {
            local[i] = localReactivity(i);
            rhoBar += weight(i) * local[i];
        }
        double global = globalReactivity();
        double bonus = scramBonus();
        rhoTotal = (rhoBar + global + bonus) * dollarScale();
        rhoBoron = cfg.boronWorthPerPpm * boronPpm;

        kin.step(rhoTotal, cfg.sourceLevel, dt, cfg.maxPromptPower);

        double z = 0;
        double[] u = new double[N];
        for (int i = 0; i < N; i++) {
            u[i] = importance[i] * Math.exp(cfg.shapeKappa * clamp(local[i] - rhoBar, -12.0, 12.0));
            z += u[i];
        }
        double zs = 0;
        for (int i = 0; i < N; i++) {
            w[i] += dt / cfg.tauShape * (u[i] / z - w[i]);
            w[i] = Math.max(1e-4, w[i]);
            zs += w[i];
        }
        for (int i = 0; i < N; i++)
            w[i] /= zs;

        double rr = 0, xx = 0, fb = 0, fu = 0;
        for (int i = 0; i < N; i++) {
            double wi = weight(i);
            rr -= wi * (cfg.regulatingRodWorth * rodCover(rodReg, i) + cfg.shutdownRodWorth * (1.0 - stuckShutdown) * rodCover(rodSd, i));
            xx -= wi * cfg.xenonEquilibriumWorth * (1.0 - 0.4 * mox) * xenon[i];
            double tc = liquidTemp(enth[i]);
            fb += wi * (doppler(tf[i])
                    + cfg.kModerator * (Math.max(tc, cfg.minFeedbackCoolantTemp) - cfg.refCoolantTemp)
                    + cfg.kVoid * alphaF[i] * 100.0 * cover[i]);
            fu += wi * ((cfg.fuelExcessBol - cfg.excessLossPerGWd * burnup[i]) * (enrichment / cfg.nominalEnrichment)
                    - cfg.burnableAbsorberWorth * Math.exp(-burnup[i] / cfg.burnableAbsorberBurnup));
        }
        rhoRods = rr;
        rhoXenon = xx;
        rhoFeedback = fb;
        rhoFuel = fu;
        rhoSamarium = -cfg.samariumEquilibriumWorth * samarium;
    }

    private void slowProcesses(double dt) {
        double comp = cfg.timeCompression;
        double p = Math.min(kin.power(), 1.5);

        for (int g = 0; g < 4; g++) {
            double tau = cfg.decayTaus[g] / comp;
            decay[g] += (cfg.decayAmplitudes[g] * p - decay[g]) * (1.0 - Math.exp(-dt / tau));
        }
        for (int i = 0; i < N; i++)
            dShape[i] += dt / cfg.tauDecayShape * (w[i] - dShape[i]);
        double lamI = Math.log(2) / (cfg.iodineHalfLifeHours * 3600.0) * comp;
        double lamX = Math.log(2) / (cfg.xenonHalfLifeHours * 3600.0) * comp;
        double b = cfg.xenonBurnRatio * lamX, a = lamX + b;
        for (int i = 0; i < N; i++) {
            double phi = Math.min(3.0, p * w[i] * N);
            iodine[i] = Math.max(0, iodine[i] + lamI * (phi - iodine[i]) * dt);
            double dx = a * (cfg.xenonDirectShare * phi + (1 - cfg.xenonDirectShare) * iodine[i]) - lamX * xenon[i] - b * phi * xenon[i];
            xenon[i] = Math.max(0, xenon[i] + dx * dt);
            burnup[i] = Math.min(cfg.maxBurnup * 1.3, burnup[i] + phi * dt / cfg.assemblyLifeSeconds * cfg.maxBurnup);
        }
        double lamPm = Math.log(2) / (cfg.promethiumHalfLifeHours * 3600.0) * comp;
        double burnSm = comp / (cfg.samariumTauHours * 3600.0);
        promethium = Math.max(0, promethium + lamPm * (p - promethium) * dt);
        samarium = clamp(samarium + burnSm * (promethium - p * samarium) * dt, 0, 3);
    }

    private void thermal(double dt, double flow, double inletK, double pBar, double boost) {
        double hg = 1.0 / (N * (cfg.ratedFuelTemp - cfg.ratedCladTemp));
        double hc0 = 1.0 / (N * (cfg.ratedCladTemp - cfg.ratedCoolantTemp));
        double cf = hg * cfg.fuelTimeConstant;
        double cc = (hg + hc0) * cfg.cladTimeConstant;
        double rateRated = N / cfg.coreTransitTime;
        double cw = (1.0 / N) / (rateRated * (25.0 / N));
        double dD = 0;
        for (double d : decay)
            dD += d;
        double flowRate = Math.max(0.0, flow) * rateRated;
        double pFactor = Math.pow(Math.max(0.05, pBar / 155.0), 0.25);
        double heat = 0;
        minDnbr = 99.0;
        h2Step = 0;
        double eUp = inletK;
        for (int i = 0; i < N; i++) {
            double qFiss = kin.power() * w[i];
            double qDecay = dD * dShape[i] * boost;
            double intact = 1.0 - moltenFuel[i];
            double q = (qFiss + qDecay) * intact;

            double tFluid = fluidTemp(enth[i]);
            double al = alpha[i];

            double flowRel = Math.max(0.0, Math.min(1.3, flow));
            double chf = (cfg.ratedDnbr / N) * (0.35 + 0.65 * flowRel) * (1.0 - 0.6 * Math.min(1.0, al / 0.8)) * pFactor * cfg.chfFactor;
            double qFlux = Math.max(1e-9, hc0 * (tcl[i] - liquidTemp(enth[i])));
            dnbr[i] = Math.min(99.0, chf / qFlux);
            if (dnbr[i] < 1.0 && cover[i] > 0.5)
                film[i] = true;
            else if (film[i] && (tcl[i] < satTemp + 50.0 || dnbr[i] > 1.4))
                film[i] = false;
            minDnbr = Math.min(minDnbr, dnbr[i]);
            double hRegime = film[i] ? cfg.filmBoilingFactor : 1.0 + 0.5 * Math.min(al, 0.3);
            double hc = hc0 * (cover[i] * hRegime + (1.0 - cover[i]) * cfg.steamCoolingFactor);
            double hcFluid = cover[i] * 1.0 + (1.0 - cover[i]) * 1.0;

            double avail = clamp(Math.max(al * 1.2, 1.0 - cover[i]), 0.0, 1.0);
            double dOx = 0;
            if (tcl[i] > cfg.oxidationStartK && avail > 0 && ox[i] < 1.0 && moltenClad[i] < 1.0) {
                double k = cfg.oxidationRate * Math.exp(-cfg.oxidationActivationK / tcl[i]);
                dOx = Math.min((1.0 - ox[i]) / dt, avail * k / (2.0 * Math.max(0.02, ox[i])));
                ox[i] += dOx * dt;
                h2Step += dOx * dt / N * cfg.zirconiumHydrogenKg;
                h2Total += dOx * dt / N * cfg.zirconiumHydrogenKg;
            }
            double qOx = dOx * (cfg.oxidationEnergySeconds / N);

            double qGap = hg * (tf[i] - tcl[i]);
            double qOut = hc * (tcl[i] - tFluid);
            tf[i] += (q - qGap) / cf * dt;
            tcl[i] += (qGap + qOx - qOut) / cc * dt;
            tf[i] = Math.max(tf[i], cfg.ambientTemp);
            tcl[i] = Math.max(tcl[i], cfg.ambientTemp);

            double dE = qOut / cw;
            enth[i] = (enth[i] + dt * (dE + flowRate * eUp)) / (1.0 + dt * flowRate);
            heat += Math.max(0.0, qOut);
            eUp = enth[i];

            double qual = quality(enth[i]);
            alpha[i] = enth[i] < satTemp - cfg.subcooledBoilingKelvin ? 0.0
                    : enth[i] < satTemp ? 0.12 * (enth[i] - (satTemp - cfg.subcooledBoilingKelvin)) / cfg.subcooledBoilingKelvin
                    : Math.max(0.12, Steam.voidFraction(qual, pBar));
            alphaF[i] = cfg.voidTau > 0 ? alphaF[i] + (alpha[i] - alphaF[i]) * Math.min(1.0, dt / cfg.voidTau) : alpha[i];

            if (tcl[i] > cfg.cladRuptureK)
                rupture[i] = Math.min(1.0, rupture[i] + (tcl[i] - cfg.cladRuptureK) / 400.0 * dt);
            if (tcl[i] > cfg.zircMeltK)
                moltenClad[i] = Math.min(1.0, moltenClad[i] + (tcl[i] - cfg.zircMeltK) / 300.0 * dt);
            if (tf[i] > cfg.uo2MeltK) {
                moltenFuel[i] = Math.min(1.0, moltenFuel[i] + (tf[i] - cfg.uo2MeltK) / 400.0 * dt);
                tf[i] = Math.min(tf[i], cfg.uo2MeltK + 120.0);
            }
        }
        outletK = fluidTemp(enth[N - 1]);
        heatToCoolant = heat;
        lagThermal = cfg.thermalTau > 0 ? lagThermal + (thermalPower() - lagThermal) * Math.min(1.0, dt / cfg.thermalTau) : thermalPower();
        double gap = 0;
        for (int i = 0; i < N; i++)
            gap += rupture[i];
        gapReleased = Math.max(gapReleased, gap / N);
    }

    public void startAtPower(double p, double pBar) {
        double sumA = 0;
        for (double a : cfg.decayAmplitudes)
            sumA += a;
        double pf = p / (1.0 + sumA);
        satTemp = Steam.satTemp(pBar);
        kin.setEquilibrium(pf);
        for (int g = 0; g < 4; g++)
            decay[g] = cfg.decayAmplitudes[g] * pf;
        double lamX = Math.log(2) / (cfg.xenonHalfLifeHours * 3600.0) * cfg.timeCompression;
        double b = cfg.xenonBurnRatio * lamX;
        for (int i = 0; i < N; i++) {
            w[i] = importance[i];
            dShape[i] = importance[i];
            double phi = pf * w[i] * N;
            iodine[i] = phi;
            xenon[i] = (lamX + b) * phi / (lamX + b * phi);
            double qn = p * w[i];
            tcl[i] = cfg.inletTemp + (cfg.ratedCoolantTemp - cfg.inletTemp) * p;
            enth[i] = cfg.inletTemp + (25.0 * p) * (i + 1.0) / N;
            tcl[i] = enth[i] + (cfg.ratedCladTemp - cfg.ratedCoolantTemp) * qn * N;
            tf[i] = tcl[i] + (cfg.ratedFuelTemp - cfg.ratedCladTemp) * qn * N;
            cover[i] = 1.0;
            alpha[i] = 0;
            alphaF[i] = 0;
        }
        samarium = pf > 0.01 ? 1.0 : 0.0;
        promethium = pf > 0.01 ? pf : 0.0;

        rodReg = 1.0;
        for (int iter = 0; iter < 10; iter++) {
            for (int i = 0; i < 2500; i++)
                thermal(0.1, 1.0, cfg.inletTemp, pBar, 1.0);
            double lo = 0, hi = 1;
            for (int it = 0; it < 50; it++) {
                double mid = 0.5 * (lo + hi);
                rodReg = mid;
                if (currentReactivity() > 0)
                    lo = mid;
                else
                    hi = mid;
            }

            double[] local = new double[N];
            double rhoBar = 0;
            for (int i = 0; i < N; i++) {
                local[i] = localReactivity(i);
                rhoBar += weight(i) * local[i];
            }
            double z = 0;
            double[] u = new double[N];
            for (int i = 0; i < N; i++) {
                u[i] = importance[i] * Math.exp(cfg.shapeKappa * clamp(local[i] - rhoBar, -12.0, 12.0));
                z += u[i];
            }
            for (int i = 0; i < N; i++) {
                w[i] = u[i] / z;
                dShape[i] = w[i];
            }
        }
        targetReg = rodReg;
        outletK = fluidTemp(enth[N - 1]);
    }

    private double currentReactivity() {
        double rhoBar = 0;
        for (int i = 0; i < N; i++)
            rhoBar += weight(i) * localReactivity(i);
        return (rhoBar + globalReactivity() + scramBonus()) * dollarScale();
    }

    public double reactivityWithRods(double regulating, double shutdown) {
        double r0 = rodReg, s0 = rodSd;
        rodReg = clamp(regulating, 0, 1);
        rodSd = clamp(shutdown, 0, 1);
        double rho = currentReactivity();
        rodReg = r0;
        rodSd = s0;
        return rho;
    }

    public double criticalRegulatingPosition() {
        if (reactivityWithRods(0, 0) < 0)
            return -1;
        if (reactivityWithRods(1, 0) > 0)
            return 2;
        double lo = 0, hi = 1;
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (lo + hi);
            if (reactivityWithRods(mid, 0) > 0)
                lo = mid;
            else
                hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    public double dopplerCoefficient() {
        double s = 0;
        for (int i = 0; i < N; i++)
            if (tf[i] > cfg.minFeedbackFuelTemp)
                s += weight(i) * cfg.kDoppler * (tf[i] > cfg.refFuelTemp ? cfg.dopplerScale : 1.0) / (2.0 * Math.sqrt(tf[i]));
        return s;
    }

    public double moderatorCoefficient() {
        double s = 0;
        for (int i = 0; i < N; i++)
            if (liquidTemp(enth[i]) > cfg.minFeedbackCoolantTemp)
                s += weight(i) * cfg.kModerator;
        return s;
    }

    public double voidCoefficient() {
        double s = 0;
        for (int i = 0; i < N; i++)
            s += weight(i) * cfg.kVoid * cover[i];
        return s;
    }

    public double axialPeaking() {
        double m = 0;
        for (double v : w)
            m = Math.max(m, v);
        return m * N;
    }

    public double meanCoolantTemp() {
        double s = 0;
        for (double e : enth)
            s += fluidTemp(e);
        return s / N;
    }

    public double[] precursorShares() {
        return kin.shares();
    }

    public void save(java.util.List<Double> out) {
        kin.save(out);
        out.add(rodReg); out.add(rodSd); out.add(targetReg); out.add(targetSd);
        out.add(scram ? 1.0 : 0.0); out.add(stuckShutdown); out.add(ejected);
        out.add(boronPpm); out.add(boronTarget); out.add(enrichment); out.add(samarium); out.add(promethium); out.add(h2Total); out.add(gapReleased);
        for (double v : decay) out.add(v);
        for (int i = 0; i < N; i++) {
            out.add(w[i]); out.add(dShape[i]); out.add(burnup[i]); out.add(iodine[i]); out.add(xenon[i]);
            out.add(tf[i]); out.add(tcl[i]); out.add(enth[i]); out.add(alpha[i]);
            out.add(ox[i]); out.add(rupture[i]); out.add(moltenClad[i]); out.add(moltenFuel[i]); out.add(film[i] ? 1.0 : 0.0);
        }
    }

    public int load(double[] a, int k) {
        return load(a, k, true);
    }

    public int load(double[] a, int k, boolean withPromethium) {
        k = kin.load(a, k);
        rodReg = a[k++]; rodSd = a[k++]; targetReg = a[k++]; targetSd = a[k++];
        scram = a[k++] > 0.5; stuckShutdown = a[k++]; ejected = a[k++];
        boronPpm = a[k++]; boronTarget = a[k++]; enrichment = a[k++]; samarium = a[k++]; promethium = withPromethium ? a[k++] : samarium * Math.min(1.0, kin.power()); h2Total = a[k++]; gapReleased = a[k++];
        for (int g = 0; g < 4; g++) decay[g] = a[k++];
        for (int i = 0; i < N; i++) {
            w[i] = a[k++]; dShape[i] = a[k++]; burnup[i] = a[k++]; iodine[i] = a[k++]; xenon[i] = a[k++];
            tf[i] = a[k++]; tcl[i] = a[k++]; enth[i] = a[k++]; alpha[i] = a[k++];
            alphaF[i] = alpha[i];
            ox[i] = a[k++]; rupture[i] = a[k++]; moltenClad[i] = a[k++]; moltenFuel[i] = a[k++]; film[i] = a[k++] > 0.5;
        }
        return k;
    }

    public static int saveSize() {
        return 25 + 14 * N;
    }

    public double power() { return kin.power(); }
    public double omega() { return kin.omega(); }
    public double periodSeconds() { return kin.periodSeconds(); }
    public boolean isPrompt() { return kin.prompt(); }
    public double reactivityDollars() { return rhoTotal; }
    public double rodWorthDollars() { return rhoRods; }
    public double xenonWorthDollars() { return rhoXenon; }
    public double samariumWorthDollars() { return rhoSamarium; }
    public double boronWorthDollars() { return rhoBoron; }
    public double feedbackDollars() { return rhoFeedback; }
    public double fuelReactivityDollars() { return rhoFuel; }
    public double decayHeat() { double s = 0; for (double d : decay) s += d; return s; }
    public double thermalPower() { return kin.power() + decayHeat(); }
    public double heatToCoolant() { return heatToCoolant; }
    public double laggedThermalPower() { return lagThermal; }
    public double outletTemp() { return outletK; }
    public double minDnbr() { return minDnbr; }
    public double[] dnbrNodes() { return dnbr.clone(); }
    public double rodRegulating() { return rodReg; }
    public double rodShutdown() { return rodSd; }
    public boolean isScrammed() { return scram; }
    public double boronPpm() { return boronPpm; }
    public double enrichmentPercent() { return enrichment; }
    public double hydrogenStepKg() { return h2Step; }
    public double hydrogenTotalKg() { return h2Total; }
    public double gapReleasedFraction() { return gapReleased; }
    public double samarium() { return samarium; }
    public double promethium() { return promethium; }
    public double[] shape() { return w.clone(); }
    public double[] fuelTemps() { return tf.clone(); }
    public double[] cladTemps() { return tcl.clone(); }
    public double[] voidNodes() { return alpha.clone(); }
    public double[] burnupNodes() { return burnup.clone(); }

    public double meanXenon() {
        double s = 0;
        for (int i = 0; i < N; i++) s += weight(i) * xenon[i];
        return s;
    }

    public double meanIodine() {
        double s = 0;
        for (int i = 0; i < N; i++) s += weight(i) * iodine[i];
        return s;
    }

    public double meanBurnup() {
        double s = 0;
        for (double b : burnup) s += b;
        return s / N;
    }

    public double maxFuelTemp() { double m = 0; for (double t : tf) m = Math.max(m, t); return m; }
    public double maxCladTemp() { double m = 0; for (double t : tcl) m = Math.max(m, t); return m; }

    public double meanFuelTemp() {
        double s = 0;
        for (double t : tf) s += t;
        return s / N;
    }

    public double meanVoidPercent() {
        double s = 0;
        for (double a : alpha) s += a;
        return 100.0 * s / N;
    }

    public double axialOffset() {
        double top = 0, bottom = 0;
        for (int i = 0; i < N; i++) {
            if (i >= N / 2) top += w[i];
            else bottom += w[i];
        }
        return top - bottom;
    }

    public double rodReserveDollars() {
        double s = 0;
        for (int i = 0; i < N; i++)
            s += weight(i) * cfg.regulatingRodWorth * rodCover(rodReg, i);
        return s;
    }

    public double meltFraction() {
        double s = 0;
        for (double m : moltenFuel) s += m;
        return s / N;
    }

    public double claddingDamageFraction() {
        double s = 0;
        for (int i = 0; i < N; i++)
            s += Math.max(rupture[i], Math.max(ox[i], moltenClad[i]));
        return s / N;
    }

    public double oxidationFraction() {
        double s = 0;
        for (double o : ox) s += o;
        return s / N;
    }

    public double ruptureFraction() {
        double s = 0;
        for (double r : rupture) s += r;
        return s / N;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
