package dev.isotopelib.sim.engine;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import dev.isotopelib.sim.chain.ChainMath;

/**
 * Writes tools/ai/feed.csv: the readings the Safety Operator sees, from the game's own reactor physics (PlantSim for Realistic mode,
 * the controller's formulas for Simple mode), through upsets nobody answers. Runs only when tools/ai/feed.request exists.
 */
class GameFeedTest {
    static final double DT = 0.25, P_LIM = 185.0, BACK_BAR = 40.0;
    static double tLim = 1000.0;
    static final int STEPS = 4 * 360;
    static final String[] KINDS = {"none", "pump_trip", "steam_blocked", "rod_drift", "coolant_loss", "load_swing", "rod_pull", "slow_cooling", "startup", "pump_and_leak", "fast_startup", "blocked_and_pull"};

    @Test
    void writeFeed() throws IOException {
        Path req = Path.of("tools/ai/feed.request");
        Assumptions.assumeTrue(Files.exists(req));
        int runs = Integer.parseInt(Files.readString(req).trim().isEmpty() ? "400" : Files.readString(req).trim());
        Random rnd = new Random(Long.getLong("feed.seed", 11L));
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of("tools/ai/feed.csv")))) {
            out.println("run,mode,kind,t,temp,pressure,power,coolant,pump,blocked,tlim,plim");
            for (int run = 0; run < runs; run++) {
                int kind = rnd.nextInt(KINDS.length);
                boolean realistic = rnd.nextBoolean();
                tLim = 800 + rnd.nextDouble() * 700;
                boolean start = kind == 8 || kind == 10;
                double p0 = start ? 0.002 : 0.2 + rnd.nextDouble() * 0.9, tEv = start ? 0 : 15 + rnd.nextDouble() * 90;
                double severity = 0.3 + rnd.nextDouble() * 0.7;
                if (realistic)
                    realistic(out, run, kind, p0, tEv, severity, rnd, rnd.nextBoolean());
                else
                    simple(out, run, kind, p0, tEv, severity, rnd);
            }
        }
        System.out.println("feed written: " + runs + " runs");
    }

    private static void row(PrintWriter out, int run, String mode, int kind, double t, double temp, double p, double power, double c,
                            boolean pump, boolean blocked) {
        out.println(String.format(Locale.ROOT, "%d,%s,%s,%.2f,%.2f,%.3f,%.5f,%.4f,%d,%d,%.0f,%.0f", run, mode, KINDS[kind], t, temp, p, power, c,
                pump ? 1 : 0, blocked ? 1 : 0, tLim, P_LIM));
    }

    private static void realistic(PrintWriter out, int run, int kind, double p0, double tEv, double sev, Random rnd, boolean gentle) {
        PlantConfig cfg = PlantConfig.standard();
        cfg.timeCompression = 60.0;
        cfg.rodSpeed = 0.015;
        PowerHold hold = gentle ? new PowerHold() : null;
        if (gentle) {
            cfg.rodSpeed = 0.005;
            cfg.thermalTau = 4.0;
            cfg.voidTau = 2.0;
            cfg.dopplerScale = 1.8;
            cfg.fuelTimeConstant = 3.0;
        }
        PlantSim m = new PlantSim(cfg);
        m.setMode(PlantSim.Mode.DRIVEN);
        m.setProtectionEnabled(false);
        m.startAtPower(p0);
        double rods = m.core.rodRegulating(), level = 1.0, sink = 1.0, flow = 1.0, fill = 0.0;
        boolean pump = true, blocked = false;
        for (int i = 0; i < STEPS; i++) {
            double t = i * DT;
            boolean ev = t >= tEv;
            if (ev) {
                switch (kind) {
                    case 1 -> { pump = false; flow = 0.05; }
                    case 2 -> { blocked = true; sink = Math.max(0.05, sink - 0.02 * sev); fill = Math.min(1.0, fill + 0.01 * sev); }
                    case 3 -> rods = Math.max(0.0, rods - 0.002 * sev * DT);
                    case 4 -> level = Math.max(0.0, level - 0.012 * sev * DT);
                    case 5 -> sink = 0.6 + 0.4 * Math.cos((t - tEv) / (10 + 30 * sev));
                    case 6 -> rods = Math.max(0.0, rods - (i == (int) (tEv / DT) + 1 ? 0.25 * sev : 0));
                    case 7 -> sink = Math.max(0.15, 1.0 - 0.004 * sev * (t - tEv));
                    case 9 -> { pump = false; flow = 0.05; level = Math.max(0.0, level - 0.008 * sev * DT); }
                    case 11 -> { blocked = true; sink = Math.max(0.05, sink - 0.02 * sev); fill = Math.min(1.0, fill + 0.01 * sev);
                        rods = Math.max(0.0, rods - 0.003 * sev * DT); }
                    case 10 -> rods = Math.max(0.0, rods - (0.002 + 0.006 * sev) * DT);
                    case 8 -> {
                        PlantSnapshot s0 = m.snapshot();
                        double per = s0.periodSeconds();
                        if (s0.power() < 0.9 && (!Double.isFinite(per) || per <= 0 || per > 40 + 160 * sev))
                            rods = Math.max(0.0, rods - 0.001);
                        else if (s0.power() > 1.0)
                            rods = Math.min(1.0, rods + 0.001);
                    }
                    default -> { }
                }
            }
            m.setRodTargets(rods, 0.0);
            if (hold != null)
                m.core.setBonusReactivity(hold.step(m.core.power(), m.core.omega(), Math.abs(m.core.rodRegulating() - rods) > 1e-6, true, DT));
            double outlet = m.core.outletTemp();
            double heatUp = Math.max(0.0, Math.min(1.0, (outlet - 300.0) / (cfg.inletTemp - 300.0)));
            double pr = (1.0 + (cfg.ratedPressure - 1.0) * heatUp * heatUp
                    + cfg.swellBarPerKelvin * Math.max(0.0, outlet - (cfg.ratedCoolantTemp + 18.0))) * (0.4 + 0.6 * level)
                    + ChainMath.backPressureBar(0.8 + 0.2 * fill, BACK_BAR) * (blocked ? 1 : 0);
            m.setDriven(flow, sink, level, pr);
            m.step(DT);
            PlantSnapshot s = m.snapshot();
            double temp = s.meanFuelK() - 273.15;
            row(out, run, gentle ? "gentle" : "realistic", kind, t, temp, pr, s.power(), level, pump, blocked);
            if (temp > tLim * 1.15 || pr > P_LIM * 1.15)
                break;
        }
    }

    private static void simple(PrintWriter out, int run, int kind, double p0, double tEv, double sev, Random rnd) {
        int insertion = kind == 8 || kind == 10 ? 100 : (int) Math.round(100 * (1 - p0 / 1.0));
        double flux = p0, decay = 0.07 * p0, temp = kind == 8 || kind == 10 ? 20 : 20 + 580 * p0, level = 1.0, sink = 1.0, flowF = 1.0, fill = 0.0;
        boolean pump = true, blocked = false;
        double fine = insertion;
        SimpleCore core = new SimpleCore();
        if (kind != 8 && kind != 10)
            core.startAt(p0);
        for (int i = 0; i < STEPS; i++) {
            double t = i * DT;
            boolean ev = t >= tEv;
            if (ev) {
                switch (kind) {
                    case 1 -> { pump = false; flowF = 0.0; }
                    case 2 -> { blocked = true; sink = Math.max(0.05, sink - 0.02 * sev); fill = Math.min(1.0, fill + 0.01 * sev); }
                    case 3 -> fine = Math.max(0, fine - 0.4 * sev * DT);
                    case 4 -> level = Math.max(0.0, level - 0.012 * sev * DT);
                    case 5 -> sink = 0.6 + 0.4 * Math.cos((t - tEv) / (10 + 30 * sev));
                    case 6 -> fine = Math.max(0, fine - (i == (int) (tEv / DT) + 1 ? 40 * sev : 0));
                    case 7 -> sink = Math.max(0.15, 1.0 - 0.004 * sev * (t - tEv));
                    case 8 -> fine = Math.max(0, fine - (0.05 + 0.3 * (1 - sev)) * DT);
                    case 9 -> { pump = false; flowF = 0.0; level = Math.max(0.0, level - 0.008 * sev * DT); }
                    case 10 -> fine = Math.max(0, fine - (0.6 + 1.5 * sev) * DT);
                    case 11 -> { blocked = true; sink = Math.max(0.05, sink - 0.02 * sev); fill = Math.min(1.0, fill + 0.01 * sev);
                        fine = Math.max(0, fine - 0.5 * sev * DT); }
                    default -> { }
                }
            }
            insertion = (int) Math.round(fine);
            double tgt = ChainMath.simplePowerTarget(insertion, 4.0, 4000, 15, 0.0) / ChainMath.simplePowerTarget(0, 4.0, 4000, 15, 0.0) * 1.0;
            core.step(tgt, false, true, 8.0, DT);
            double p = core.power();
            flux = Math.max(1e-10, p);
            decay = Math.max(decay * 0.997, p * 0.07);
            double heat = p + decay;
            double reach = Math.min(1.0, flowF) * Math.min(1.0, level / 0.5);
            temp = ChainMath.simpleTemperatureStep(temp, heat, reach * sink, 20.0, 3.0, 3.0 / 580.0, DT);
            double pr = 1 + 155 * Math.min(1.35, Math.max(0, (temp - 20) / 800)) * (0.4 + 0.6 * level)
                    + ChainMath.backPressureBar(0.8 + 0.2 * fill, BACK_BAR) * (blocked ? 1 : 0);
            row(out, run, "simple", kind, t, temp, pr, flux, level, pump, blocked);
            if (temp > tLim * 1.15 || pr > P_LIM * 1.15)
                break;
        }
    }
}
