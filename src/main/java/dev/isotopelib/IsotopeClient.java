package dev.isotopelib;

import java.util.Locale;

import dev.isotopelib.radiation.Contamination;
import dev.isotopelib.radiation.ContaminationMath;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** The settings screen (Mods, Isotope, Config), and how contaminated items look in a slot and in their tooltip. */
@Mod(value = Isotope.MODID, dist = Dist.CLIENT)
public final class IsotopeClient {
    /** Dust specks in the lower left of a slot, placed one by one, in the order they appear as the dust grows. */
    private static final int[][] SPECKS = {{2, 13}, {4, 11}, {1, 10}, {5, 14}, {3, 8}};
    private static final int SPECK = 0xFFD6CDB0, SPECK_DARK = 0xFF4A4436;  // pale grey-brown dust, not radioactive green

    private static final IItemDecorator DUST = (g, font, stack, x, y) -> {
        int n = ContaminationMath.specks(Contamination.of(stack));
        if (n == 0)
            return false;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        for (int i = 0; i < n; i++) {
            int px = x + SPECKS[i][0], py = y + SPECKS[i][1];
            g.fill(px + 1, py + 1, px + 2, py + 2, SPECK_DARK);
            g.fill(px, py, px + 1, py + 1, SPECK);
        }
        g.pose().popPose();
        return false;
    };

    public IsotopeClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener((RegisterItemDecorationsEvent ev) -> BuiltInRegistries.ITEM.forEach(item -> ev.register(item, DUST)));
        NeoForge.EVENT_BUS.addListener(IsotopeClient::tooltip);
    }

    private static void tooltip(ItemTooltipEvent ev) {
        double bq = Contamination.of(ev.getItemStack());
        if (bq >= ContaminationMath.MIN_BQ)
            ev.getToolTip().add(Component.translatable("tooltip.isotope.contaminated").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" " + becquerel(bq)).withStyle(ChatFormatting.GOLD)));
    }

    static String becquerel(double bq) {
        String[] unit = {"Bq", "kBq", "MBq", "GBq", "TBq"};
        int i = 0;
        while (bq >= 1000 && i < unit.length - 1) {
            bq /= 1000;
            i++;
        }
        return String.format(Locale.ROOT, bq < 10 ? "%.1f %s" : "%.0f %s", bq, unit[i]);
    }
}
