package dev.isotopelib.api;

import dev.isotopelib.fallout.FalloutData;
import dev.isotopelib.radiation.DoseAttachments;
import dev.isotopelib.radiation.RadiationEngine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Everything another mod needs, next to the data maps (isotope:emission, isotope:shielding, isotope:protection). */
public final class IsotopeApi {
    private IsotopeApi() {
    }

    /** Total dose of a player in millisieverts. */
    public static float dose(Player player) {
        return player.getData(DoseAttachments.DOSE_MSV.get());
    }

    public static void setDose(Player player, float msv) {
        player.setData(DoseAttachments.DOSE_MSV.get(), Math.max(0f, msv));
    }

    public static void addDose(Player player, float msv) {
        setDose(player, dose(player) + msv);
    }

    /** Dose rate at the player right now, before protection. */
    public static Exposure exposure(Player player) {
        return RadiationEngine.exposure(player);
    }

    /** Adds a source that is neither a block nor an item. Call it once, during mod setup. */
    public static void registerField(RadiationField field) {
        RadiationEngine.FIELDS.add(field);
    }

    /** Releases fallout, whatever the cause. A large reactor explosion is a few thousand. */
    public static void releaseFallout(ServerLevel level, BlockPos pos, double strength) {
        FalloutData.get(level).release(pos, strength);
    }

    public static double falloutAt(ServerLevel level, double x, double z) {
        return FalloutData.get(level).field().at(x, z);
    }

    /** Lets radioactive dust (becquerels) settle on a player: on the worn armour, and on the skin where none is worn. */
    public static void contaminate(Player player, double bq) {
        dev.isotopelib.radiation.Contamination.contaminate(player, bq);
    }

    /** Becquerels of dust on the player's skin and worn armour. */
    public static double contamination(Player player) {
        return dev.isotopelib.radiation.Contamination.onPerson(player);
    }

    /** Becquerels of dust on an item stack; 0 removes it. */
    public static double contamination(net.minecraft.world.item.ItemStack stack) {
        return dev.isotopelib.radiation.Contamination.of(stack);
    }

    public static void setContamination(net.minecraft.world.item.ItemStack stack, double bq) {
        dev.isotopelib.radiation.Contamination.set(stack, bq);
    }

    public static int clearFallout(ServerLevel level, double x, double z, int radius) {
        return FalloutData.get(level).clearAround(x, z, radius);
    }

    /** Phase of the player's radiation sickness: none, prodromal (nausea), latent (feels fine) or manifest (the illness). */
    public static dev.isotopelib.radiation.SyndromeMath.Phase syndromePhase(Player player) {
        return dev.isotopelib.radiation.RadiationSyndrome.phase(player);
    }
}
