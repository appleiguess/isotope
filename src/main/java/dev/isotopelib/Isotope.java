package dev.isotopelib;

import dev.isotopelib.fallout.FalloutData;
import dev.isotopelib.radiation.Contamination;
import dev.isotopelib.radiation.DoseAttachments;
import dev.isotopelib.radiation.RadiationEngine;
import dev.isotopelib.radiation.RadiationMaps;
import dev.isotopelib.radiation.RadiationSyndrome;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Isotope.MODID)
public final class Isotope {
    public static final String MODID = "isotope";

    /** Going Nuclear, where this engine comes from. Worlds saved before 0.612 use its names; they are read as ours. */
    public static final String LEGACY = "goingnuclear";

    public Isotope(IEventBus bus, ModContainer container) {
        RadiationSyndrome.init();
        DoseAttachments.ATTACHMENTS.register(bus);
        bus.addListener(RadiationMaps::register);
        container.registerConfig(ModConfig.Type.SERVER, IsotopeConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(FalloutData::onLevelTick);
        NeoForge.EVENT_BUS.addListener(RadiationEngine::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RadiationEngine::onHeal);
        NeoForge.EVENT_BUS.addListener(RadiationEngine::onLogout);
        // dust that sticks to people and things (loads its attachment into DoseAttachments before registration)
        Contamination.COMPONENTS.register(bus);
        NeoForge.EVENT_BUS.addListener(Contamination::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(Contamination::onExposure);
        NeoForge.EVENT_BUS.addListener(Contamination::onEntityTick);
        NeoForge.EVENT_BUS.addListener(Contamination::onUseBlock);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static ResourceLocation legacyId(String path) {
        return ResourceLocation.fromNamespaceAndPath(LEGACY, path);
    }
}
