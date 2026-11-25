package dev.isotopelib.api;

import net.minecraft.world.level.block.state.BlockState;

/** Implemented by a block whose radiation depends on its state. A fixed value needs no code: use the isotope:emission data map. */
public interface RadioactiveBlock {
    Emission emission(BlockState state);
}
