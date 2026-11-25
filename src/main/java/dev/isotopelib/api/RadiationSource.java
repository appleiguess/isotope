package dev.isotopelib.api;

/** Implemented by a block entity whose radiation changes over time, such as a tank of spent fuel. */
public interface RadiationSource {
    Emission emission();
}
