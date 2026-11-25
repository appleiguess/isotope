package dev.isotopelib.sim.engine;

public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    public long state() {
        return state;
    }

    public void setState(long s) {
        this.state = s;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public double next() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public int nextInt(int bound) {
        return (int) (next() * bound);
    }

    public boolean chance(double p) {
        return next() < p;
    }
}
