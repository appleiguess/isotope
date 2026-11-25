package dev.isotopelib;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The settings screen (Mods, Isotope, Config). */
@Mod(value = Isotope.MODID, dist = Dist.CLIENT)
public final class IsotopeClient {
    public IsotopeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
