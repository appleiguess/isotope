package dev.isotopelib.api;

import net.minecraft.world.item.ItemStack;

/** Implemented by an item whose radiation depends on the stack. A fixed value needs no code: use the isotope:emission data map. */
public interface RadioactiveItem {
    /** Radiation of ONE item of the stack. */
    Emission emission(ItemStack stack, long gameTime);
}
