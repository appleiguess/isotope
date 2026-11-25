package dev.isotopelib.api;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Fired on the server once a second for every player, before the dose is taken. Listeners may change the exposure or the share
 * of each kind that is stopped. Cancelling it means no dose this second.
 */
public class ExposureEvent extends PlayerEvent implements ICancellableEvent {
    private Exposure exposure;
    private double alphaProtection, betaProtection, gammaProtection, reading;

    public ExposureEvent(Player player, Exposure exposure, double alpha, double beta, double gamma) {
        super(player);
        this.exposure = exposure;
        this.alphaProtection = alpha;
        this.betaProtection = beta;
        this.gammaProtection = gamma;
    }

    public Exposure getExposure() {
        return exposure;
    }

    public void setExposure(Exposure exposure) {
        this.exposure = exposure;
    }

    public double getAlphaProtection() {
        return alphaProtection;
    }

    public double getBetaProtection() {
        return betaProtection;
    }

    public double getGammaProtection() {
        return gammaProtection;
    }

    public void setProtection(double alpha, double beta, double gamma) {
        alphaProtection = Math.max(0, Math.min(1, alpha));
        betaProtection = Math.max(0, Math.min(1, beta));
        gammaProtection = Math.max(0, Math.min(1, gamma));
    }

    /** Adds to what meters show without adding to the dose, for radiation another mod already doses the player with. */
    public void addReading(double usvPerSecond) {
        reading += Math.max(0, usvPerSecond);
    }

    /** What meters show, microsieverts per game second. */
    public double reading() {
        return exposure.ambient() + reading;
    }

    /** Microsieverts per game second that get through. */
    public double effective() {
        return exposure.gamma() * (1 - gammaProtection) + exposure.beta() * (1 - betaProtection) + exposure.inhaled() * (1 - alphaProtection);
    }
}
