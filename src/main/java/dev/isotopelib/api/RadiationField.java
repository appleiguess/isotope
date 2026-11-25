package dev.isotopelib.api;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A source that is neither a block nor an item, such as a running reactor: the gamma dose rate it causes at a point. */
@FunctionalInterface
public interface RadiationField {
    /** Microsieverts per game second at a point, shielding included. */
    double gammaAt(Level level, Vec3 point);
}
