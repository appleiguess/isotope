package dev.isotopelib.radiation;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.isotopelib.Isotope;
import dev.isotopelib.IsotopeConfig;
import dev.isotopelib.radiation.SyndromeMath.Phase;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Radiation sickness that runs its real course: nausea first, then a calm phase in which you feel fine, then the illness.
 * The state is three attachments on the player; death clears them. The timing is in {@link SyndromeMath}.
 */
public final class RadiationSyndrome {
    /** 0 none, 1 prodromal, 2 latent, 3 manifest. */
    public static final Supplier<AttachmentType<Integer>> PHASE = DoseAttachments.ATTACHMENTS.register("syndrome_phase",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** Game time at which the current phase ends. */
    public static final Supplier<AttachmentType<Long>> PHASE_END = DoseAttachments.ATTACHMENTS.register("syndrome_phase_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** Highest dose of this course, mSv. */
    public static final Supplier<AttachmentType<Float>> PEAK_MSV = DoseAttachments.ATTACHMENTS.register("syndrome_peak_msv",
            () -> AttachmentType.builder(() -> 0f).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());

    public static final ResourceLocation MODIFIER_ID = Isotope.id("radiation_syndrome");

    /** Vomiting in the prodromal phase, in seconds. */
    private static final int VOMIT_PERIOD = 30;

    private RadiationSyndrome() {
    }

    /** Called from the mod constructor so that the attachments are registered before the registry is. */
    public static void init() {
    }

    public static Phase phase(Player player) {
        return Phase.of(player.getData(PHASE.get()));
    }

    /** Sickness switched on, and the course of illness with it. */
    public static boolean enabled() {
        return IsotopeConfig.SICKNESS.get() && IsotopeConfig.ACUTE_RADIATION_SYNDROME.get();
    }

    /** Natural healing is stopped: in the manifest phase, or above 1 Sv when the course is switched off. */
    public static boolean blocksHealing(Player player) {
        if (!IsotopeConfig.SICKNESS.get())
            return false;
        if (!IsotopeConfig.ACUTE_RADIATION_SYNDROME.get())
            return player.getData(DoseAttachments.DOSE_MSV.get()) >= 1000f;
        return phase(player) == Phase.MANIFEST;
    }

    /** One second, with the dose after it. Only called when {@link #enabled()}. */
    public static void tick(Player player, float msv) {
        long now = player.level().getGameTime();
        Phase phase = phase(player);

        if (phase != Phase.NONE && msv < SyndromeMath.END_MSV) {
            end(player);
            phase = Phase.NONE;
        }
        if (phase == Phase.NONE) {
            if (msv >= SyndromeMath.START_MSV) {
                begin(player, msv, now);
                phase = Phase.PRODROMAL;
            } else {
                setModifier(player, 0.0);
                if (msv >= 100f) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true, true));
                }
                return;
            }
        }

        double speed = IsotopeConfig.SYNDROME_SPEED.get();
        if (phase != Phase.MANIFEST)
            phase = advance(player, phase, msv, now, speed);

        switch (phase) {
            case PRODROMAL -> prodromal(player);
            case MANIFEST -> manifest(player, msv);
            default -> {
            }
        }
    }

    private static void begin(Player player, float msv, long now) {
        player.setData(PEAK_MSV.get(), msv);
        player.setData(PHASE.get(), Phase.PRODROMAL.ordinal());
        player.setData(PHASE_END.get(), now + SyndromeMath.ticks(SyndromeMath.prodromalMinutes(msv / 1000.0), IsotopeConfig.SYNDROME_SPEED.get()));
    }

    /** Ends a running course, e.g. when the setting was switched off in the middle of one, so no health penalty stays behind. */
    public static void stop(Player player) {
        if (player.getData(PHASE.get()) != 0)
            end(player);
    }

    private static void end(Player player) {
        player.setData(PHASE.get(), 0);
        player.setData(PHASE_END.get(), 0L);
        player.setData(PEAK_MSV.get(), 0f);
        setModifier(player, 0.0);
    }

    /** A higher peak shortens what is left of the phase; then phases pass when their time is up. */
    private static Phase advance(Player player, Phase phase, float msv, long now, double speed) {
        float peak = player.getData(PEAK_MSV.get());
        long end = player.getData(PHASE_END.get());
        if (msv >= peak + 1000f) {
            double oldSv = peak / 1000.0, newSv = msv / 1000.0;
            double oldMin = phase == Phase.PRODROMAL ? SyndromeMath.prodromalMinutes(oldSv) : SyndromeMath.latentMinutes(oldSv);
            double newMin = phase == Phase.PRODROMAL ? SyndromeMath.prodromalMinutes(newSv) : SyndromeMath.latentMinutes(newSv);
            end = now + SyndromeMath.rescaleRemaining(Math.max(0L, end - now), oldMin, newMin);
            peak = msv;
            player.setData(PEAK_MSV.get(), peak);
            player.setData(PHASE_END.get(), end);
        }
        if (phase == Phase.PRODROMAL && now >= end) {
            long latent = SyndromeMath.ticks(SyndromeMath.latentMinutes(peak / 1000.0), speed);
            if (latent > 0) {
                phase = Phase.LATENT;
                end = now + latent;
                player.setData(PHASE_END.get(), end);
            } else
                phase = Phase.MANIFEST;
            player.setData(PHASE.get(), phase.ordinal());
        }
        if (phase == Phase.LATENT && now >= end) {
            phase = Phase.MANIFEST;
            player.setData(PHASE.get(), phase.ordinal());
        }
        return phase;
    }

    private static void prodromal(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true, true));
        long second = player.tickCount / 20;
        if ((second + Math.floorMod(player.getUUID().hashCode(), VOMIT_PERIOD)) % VOMIT_PERIOD == 0) {
            int lost = 2 + player.getRandom().nextInt(2);
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - lost));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0, false, false, true));
        }
    }

    private static void manifest(Player player, float msv) {
        setModifier(player, SyndromeMath.maxHealthPenalty(msv));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1, false, false, true));
        if (!(player.level() instanceof ServerLevel level))
            return;
        float sv = msv / 1000f;
        if (player.getRandom().nextFloat() >= Math.min(0.15f, 0.008f * sv * sv))
            return;
        float damage = msv >= 6000f ? 6f : msv >= 4000f ? 4f : msv >= 2000f ? 2f : 1f;
        player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(RadiationEngine.DAMAGE)), damage);
    }

    /** Maximum health lowered by the given half hearts; 0 removes it. */
    private static void setModifier(Player player, double penalty) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null)
            return;
        if (penalty <= 0.0) {
            if (max.hasModifier(MODIFIER_ID))
                max.removeModifier(MODIFIER_ID);
            return;
        }
        AttributeModifier old = max.getModifier(MODIFIER_ID);
        if (old != null && old.amount() == -penalty)
            return;
        max.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, -penalty, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth())
            player.setHealth(player.getMaxHealth());
    }
}
