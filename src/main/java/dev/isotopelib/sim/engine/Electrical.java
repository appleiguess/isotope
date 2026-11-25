package dev.isotopelib.sim.engine;

public final class Electrical {
    public enum DieselState { OFF, STARTING, RUNNING, FAILED, FLOODED, OUT_OF_FUEL }

    private final PlantConfig cfg;
    private boolean offsite = true;
    private final DieselState[] diesel;
    private final double[] dieselStart;
    private double dieselFuel;
    private double battery = 1.0;
    private boolean dieselStartSignal;

    public Electrical(PlantConfig cfg) {
        this.cfg = cfg;
        this.diesel = new DieselState[cfg.diesels];
        this.dieselStart = new double[cfg.diesels];
        java.util.Arrays.fill(diesel, DieselState.OFF);
        dieselFuel = 1.0;
    }

    public void step(double dt) {
        double comp = cfg.timeCompression;

        dieselStartSignal = !offsite;
        for (int i = 0; i < diesel.length; i++) {
            if (diesel[i] == DieselState.FAILED || diesel[i] == DieselState.FLOODED || diesel[i] == DieselState.OUT_OF_FUEL)
                continue;
            if (dieselStartSignal && diesel[i] == DieselState.OFF)
                diesel[i] = DieselState.STARTING;
            if (diesel[i] == DieselState.STARTING) {
                dieselStart[i] += dt;
                if (dieselStart[i] >= cfg.dieselStartSeconds)
                    diesel[i] = DieselState.RUNNING;
            }
            if (!dieselStartSignal && diesel[i] != DieselState.OFF) {
                diesel[i] = DieselState.OFF;
                dieselStart[i] = 0;
            }
        }
        int running = runningDiesels();
        if (running > 0) {
            dieselFuel = Math.max(0.0, dieselFuel - dt * comp / (cfg.dieselFuelHours * 3600.0));
            if (dieselFuel <= 0)
                for (int i = 0; i < diesel.length; i++)
                    if (diesel[i] == DieselState.RUNNING)
                        diesel[i] = DieselState.OUT_OF_FUEL;
        }

        if (acAvailable())
            battery = Math.min(1.0, battery + dt * comp / (cfg.batteryHours * 3600.0) * 4.0);
        else
            battery = Math.max(0.0, battery - dt * comp / (cfg.batteryHours * 3600.0));
    }

    public int runningDiesels() {
        int n = 0;
        for (DieselState s : diesel)
            if (s == DieselState.RUNNING)
                n++;
        return n;
    }

    public boolean offsite() { return offsite; }
    public boolean acAvailable() { return offsite || runningDiesels() > 0; }
    public boolean dcAvailable() { return acAvailable() || battery > 0; }
    public boolean stationBlackout() { return !acAvailable(); }
    public double battery() { return battery; }
    public double dieselFuel() { return dieselFuel; }
    public DieselState dieselState(int i) { return diesel[i]; }

    public void setOffsite(boolean on) { offsite = on; }

    public void floodDiesels() {
        for (int i = 0; i < diesel.length; i++)
            diesel[i] = DieselState.FLOODED;
    }

    public void failDiesel(int i) {
        if (i >= 0 && i < diesel.length)
            diesel[i] = DieselState.FAILED;
    }

    public void refuelDiesels() {
        dieselFuel = 1.0;
        for (int i = 0; i < diesel.length; i++)
            if (diesel[i] == DieselState.OUT_OF_FUEL)
                diesel[i] = DieselState.OFF;
    }

    public void rechargeBattery() {
        battery = 1.0;
    }

    public void save(java.util.List<Double> out) {
        out.add(offsite ? 1.0 : 0.0);
        out.add(dieselFuel);
        out.add(battery);
        for (int i = 0; i < diesel.length; i++) {
            out.add((double) diesel[i].ordinal());
            out.add(dieselStart[i]);
        }
    }

    public int load(double[] a, int k) {
        offsite = a[k++] > 0.5;
        dieselFuel = a[k++];
        battery = a[k++];
        for (int i = 0; i < diesel.length; i++) {
            diesel[i] = DieselState.values()[(int) a[k++]];
            dieselStart[i] = a[k++];
        }
        return k;
    }

    public int saveSize() {
        return 3 + 2 * diesel.length;
    }
}
