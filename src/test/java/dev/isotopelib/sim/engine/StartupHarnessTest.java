package dev.isotopelib.sim.engine;

import org.junit.jupiter.api.Test;

/**
 * The reactor controller's plant step (applyDesign, stepNewModel, boil) without the world, with an ideal steam side (all steam
 * leaves, all water comes back) and an operator at the desk. Before the rod withdrawal block a startup tripped on short period
 * however the slider was pulled (0.512-4 bug report: "the reactor never works, the Creator's neither"); now pulling the slider
 * out, even all at once, brings the reactor to power without a trip.
 */
class StartupHarnessTest {
    static final double DT = 0.25, STEAM_PER_HEAT = 2.0, STEAM_AT_FULL = 6 * 2400.0;

    static final class Plant {
        final PlantSim m;
        final int channels, rodColumns, columns, graphite;
        int fuelMb;
        double enrichment = 4.0;
        int insertion = 100;
        boolean scram = false, lastScram = false;
        double steamRate, heatBudget;
        double t, peak;
        int hold;
        double meter;
        String log = "";

        Plant(int channels, int rodColumns, int columns, int graphite, int fuelMb) {
            this.channels = channels;
            this.rodColumns = rodColumns;
            this.columns = columns;
            this.graphite = graphite;
            this.fuelMb = fuelMb;
            PlantConfig cfg = PlantConfig.standard();
            cfg.timeCompression = 60.0;
            cfg.rodSpeed = 0.015;
            cfg.tripLowFlow = 0.45;
            m = new PlantSim(cfg);
            m.setMode(PlantSim.Mode.DRIVEN);
            m.core.setTemperaturesInstant(293.15, 300.0);
            m.setDrivenTemperature(293.15);
            m.core.setRodsInstant(1.0, 1.0);
        }

        void applyDesign() {
            double rodRatio = 4.0 / 3.0 * Math.min(1.0, rodColumns / (double) Math.max(1, columns));
            double rodScale = (1.0 - Math.exp(-1.4 * rodRatio)) / (1.0 - Math.exp(-1.4));
            m.cfg.regulatingRodWorth = 12.0 * Math.min(1.2, Math.max(0.05, rodScale));
            double mass = Math.sqrt(Math.min(1.0, fuelMb / (channels * 150.0)));
            m.core.setEnrichment(Math.max(0.1, enrichment * mass));
            m.core.setBonusReactivity(Math.min(1.2, graphite * 0.15));
            PlantConfig d = PlantConfig.standard();
            m.cfg.kVoid = d.kVoid;
            m.cfg.kModerator = d.kModerator;
            m.cfg.positiveScramDollars = d.positiveScramDollars;
            m.cfg.scramTime = d.scramTime;
        }

        /** One controller step (5 ticks). */
        void step() {
            t += DT;
            if (scram)
                insertion = 100;
            applyDesign();
            // the controller's rod withdrawal block (ReactorControllerBlockEntity#rodTarget)
            double target = insertion / 100.0, now = m.core.rodRegulating(), per0 = m.stablePeriodSeconds();
            if (insertion < 99 && m.core.rodShutdown() > 0.001)
                target = Math.max(target, now);
            double pw = m.core.power();
            boolean rising = per0 > 0 && per0 < 1e8;
            boolean block = !scram && (rising && per0 < 30.0 || pw > 0.95 || rising && pw * Math.exp(60.0 / per0) > 1.0
                    || period() > 0 && period() < 15.0);
            boolean insert = rising && per0 < 15.0 || pw > 1.03 || rising && pw * Math.exp(20.0 / per0) > 1.03;
            boolean hold = block && (target < now || insertion < 99 && m.core.rodShutdown() > 0.0);
            double reg;
            if (hold)
                this.hold = 20;
            if (hold)
                reg = insert ? Math.min(1.0, Math.max(target, now + 0.02)) : target >= now ? target : now;
            else if (this.hold > 0 && target < now) {
                this.hold--;
                reg = now;
            } else
                reg = Math.max(target, now - (pw > 0.3 ? 0.001 : 0.004));
            m.setRodTargets(reg, insertion >= 99 ? 1.0 : hold ? m.core.rodShutdown() : 0.0);
            if (scram)
                m.manualTrip();
            else if (lastScram)
                m.resetTrip();
            lastScram = scram;
            double outlet = m.core.outletTemp();
            double fill = 1.0;                                      // ideal: the vessel always full
            double heatUp = Math.max(0.0, Math.min(1.0, (outlet - 300.0) / (m.cfg.inletTemp - 300.0)));
            double p = (1.0 + (m.cfg.ratedPressure - 1.0) * heatUp * heatUp
                    + m.cfg.swellBarPerKelvin * Math.max(0.0, outlet - (m.cfg.ratedCoolantTemp + 18.0))) * fill;
            double sink = dev.isotopelib.sim.chain.ChainMath.heatSink(steamRate / STEAM_PER_HEAT, STEAM_AT_FULL, m.core.thermalPower());
            m.setDriven(1.0, sink, 1.0, p);
            m.step(DT);
            PlantSnapshot s = m.snapshot();
            meter += (m.core.omega() - meter) * (1.0 - Math.exp(-DT / 2.0));
            peak = Math.max(peak, s.power());
            if (s.tripped() && !scram) {
                scram = true;
                log += String.format("t=%.0fs TRIP mask=%s p=%.0f bar P=%.3g%n", t, Integer.toBinaryString(s.tripMask()), p, s.power());
            }
            double period = period();
            if (!scram && Double.isFinite(period) && period > 0 && period < 10.0 && s.power() > 1e-4) {
                scram = true;
                log += String.format("t=%.0fs PERIOD TRIP period=%.1fs P=%.3g ins=%d%n", t, period, s.power(), insertion);
            }
            // boil: steam from the heat, by the hot leg temperature
            double hotC = s.hotLegK() - 273.15;
            double tf = dev.isotopelib.sim.chain.ChainMath.temperatureFactor(hotC, 150.0);
            heatBudget = Math.max(0.0, m.core.thermalPower()) * STEAM_AT_FULL * DT;
            double heat = tf <= 0 ? 0.0 : heatBudget * Math.min(1.0, tf);
            double take = heat * STEAM_PER_HEAT;
            steamRate += (take / DT - steamRate) * 0.3;
        }

        /** The desk's period meter (ReactorControllerBlockEntity#measuredPeriod). */
        double period() {
            return Math.abs(meter) < 1e-4 ? 1e9 : 1.0 / meter;
        }

        String state() {
            PlantSnapshot s = m.snapshot();
            return String.format("t=%5.0fs ins=%3d scram=%s P=%.3g rho=%+.3f$ period=%.0f hot=%.0fC steam=%.0f", t, insertion, scram, s.power(),
                    s.reactivityDollars(), s.periodSeconds(), s.hotLegK() - 273.15, steamRate);
        }
    }

    /** The critical rod position, as the Physics Desk's rod table would show it. */
    static int critical(Plant pl) {
        pl.applyDesign();
        for (int i = 100; i >= 0; i--)
            if (pl.m.core.reactivityWithRods(i / 100.0, 0.0) >= 0)
                return i;
        return -1;
    }

    static void run(String name, Plant pl, int target, boolean careful) {
        System.out.println("=== " + name + (careful ? " careful" : " naive") + " target " + target);
        System.out.println("critical rod position: " + critical(pl) + " %, rods all out: "
                + String.format("%+.2f$", pl.m.core.reactivityWithRods(0, 0)));
        for (int i = 0; i < 4 * 60 * (careful ? 30 : 20); i++) {   // 20 minutes, 30 for a careful start
            if (careful) {
                // a careful operator: 1 % every 10 s while the reactor is clearly subcritical; once a positive period under 5 minutes shows,
                // 1 % every 40 s so the flux shape can settle after each pull, and only while the period is longer than two minutes (1 % can be 0.25 $ near mid-stroke)
                PlantSnapshot s = pl.m.snapshot();
                double per = pl.period();
                boolean rising = Double.isFinite(per) && per > 0 && per < 300;
                if (i % (rising ? 160 : 40) == 0 && pl.insertion > target && (!rising || per > 120) && s.power() < 0.3)
                    pl.insertion--;
                if (s.power() > 0.3 && Double.isFinite(per) && per > 0 && per < 120 && i % 40 == 0)
                    pl.insertion++;
            } else {
                pl.insertion = target;
            }
            pl.step();
            if (i % 480 == 0)
                System.out.println(pl.state());
        }
        System.out.println(pl.state());
        System.out.print(pl.log);
        org.junit.jupiter.api.Assertions.assertEquals("", pl.log, name + ": no trip");
        org.junit.jupiter.api.Assertions.assertTrue(pl.peak > 0.2, name + ": reaches power " + pl.peak);
        org.junit.jupiter.api.Assertions.assertTrue(pl.peak < 1.09, name + ": no overpower " + pl.peak);
    }

    @Test
    void creatorPlant() {
        run("creator 15 ch", new Plant(15, 5, 5, 8, 4000), 70, false);
        run("creator 15 ch", new Plant(15, 5, 5, 8, 4000), 40, false);
        run("creator 15 ch", new Plant(15, 5, 5, 8, 4000), 0, true);
    }

    @Test
    void rodsAlmostInHoldTheColdCore() {
        // START releases the shutdown rods while the regulating rods stay in: the cold core must stay well subcritical there
        for (Plant pl : new Plant[]{new Plant(15, 5, 5, 8, 4000), new Plant(85, 17, 17, 0, 16000), new Plant(85, 17, 17, 8, 16000)}) {
            for (double doppler : new double[]{1.0, 1.8}) {
                pl.applyDesign();
                pl.m.cfg.dopplerScale = doppler;                   // Simple mode's stronger Doppler must not wake a cold core
                double rho = pl.m.core.reactivityWithRods(0.98, 0.0);
                org.junit.jupiter.api.Assertions.assertTrue(rho < -1.0, "rods at 98 %, shutdown rods out, Doppler x" + doppler + ": " + rho + " $");
            }
        }
    }

    @Test
    void playersPlant() {
        // 17 columns, 5 high, a drive on every column, no graphite
        run("player 85 ch 8000 mB", new Plant(85, 17, 17, 0, 8000), 0, true);
        run("player 85 ch 8000 mB", new Plant(85, 17, 17, 0, 8000), 30, false);
        run("player 85 ch 16000 mB", new Plant(85, 17, 17, 0, 16000), 0, true);
    }
}
