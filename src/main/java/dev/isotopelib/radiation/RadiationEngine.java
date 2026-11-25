package dev.isotopelib.radiation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.isotopelib.Isotope;
import dev.isotopelib.IsotopeConfig;
import dev.isotopelib.api.Emission;
import dev.isotopelib.api.Exposure;
import dev.isotopelib.api.ExposureEvent;
import dev.isotopelib.api.Protection;
import dev.isotopelib.api.RadiationField;
import dev.isotopelib.api.RadiationSource;
import dev.isotopelib.fallout.FalloutData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Adds up every source around a player once a second, takes off the protection and keeps the dose. */
public final class RadiationEngine {
    public static final List<RadiationField> FIELDS = new CopyOnWriteArrayList<>();

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Isotope.id("radiation"));

    private static final double BETA_CONSTANT = 6.0e-7, INHALED_CONSTANT = 2.0e-7;
    private static final double CARRY_DISTANCE = 0.5;
    private static final int BLOCK_RADIUS = 6, CONTAINER_RADIUS = 8, SOURCE_RADIUS = 24;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private RadiationEngine() {
    }

    // ------------------------------------------------------------------ exposure

    private static final class Sum {
        double gamma, beta, inhaled;

        void point(Level level, Emission e, Vec3 from, Vec3 to, double scale) {
            double dist = Math.max(RadiationMath.MIN_DISTANCE, from.distanceTo(to));
            double k = scale * e.activityBq() / 1.0e6 / (dist * dist);
            if (e.gamma() > 0)
                gamma += RadiationMath.GAMMA_CONSTANT * k * e.gamma() * transmission(level, from, to, RadiationMath.RadiationType.GAMMA);
            if (e.beta() > 0)
                beta += BETA_CONSTANT * k * e.beta() * transmission(level, from, to, RadiationMath.RadiationType.BETA);
        }
    }

    public static Exposure exposure(Player player) {
        Level level = player.level();
        long now = level.getGameTime();
        double scale = IsotopeConfig.DOSE_TIME_SCALE.get();
        Vec3 eye = player.getEyePosition();
        Sum sum = new Sum();
        if (IsotopeConfig.ITEM_RADIATION.get())
            materials(player, level, eye, now, scale, sum);
        if (level instanceof ServerLevel sl) {
            double c = FalloutData.get(sl).field().at(player.getX(), player.getZ());
            if (c > 0)
                sum.gamma += c * IsotopeConfig.FALLOUT_DOSE.get() * (level.canSeeSky(player.blockPosition().above()) ? 1.0 : 0.2);
        }
        for (RadiationField f : FIELDS)
            sum.gamma += Math.max(0, f.gammaAt(level, eye));
        return new Exposure(sum.gamma, sum.beta, sum.inhaled);
    }

    private static void materials(Player player, Level level, Vec3 eye, long now, double scale, Sum sum) {
        carried(player, now, scale, sum);
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))) {
            Emission e = RadiationMaps.emission(ie.getItem(), now);
            if (!e.isNone())
                sum.point(level, e, ie.position().add(0, 0.2, 0), eye, scale * RadiationMath.stackSelfShield(ie.getItem().getCount()));
        }
        for (BlockPos p : blocksNear(level, player)) {
            Emission e = RadiationMaps.emission(level.getBlockState(p));
            if (!e.isNone())
                sum.point(level, e, Vec3.atCenterOf(p), eye, scale);
        }
        blockEntities(level, player.blockPosition(), eye, now, scale, sum);
    }

    private static void carried(Player player, long now, double scale, Sum sum) {
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand))
            for (ItemStack stack : list) {
                Emission e = RadiationMaps.emission(stack, now);
                if (e.isNone())
                    continue;
                double mbq = scale * e.activityBq() / 1.0e6 * RadiationMath.stackSelfShield(stack.getCount());
                sum.gamma += RadiationMath.GAMMA_CONSTANT * mbq * e.gamma() / (CARRY_DISTANCE * CARRY_DISTANCE);
                sum.beta += BETA_CONSTANT * mbq * e.beta() / (CARRY_DISTANCE * CARRY_DISTANCE);
                if (e.dust())
                    sum.inhaled += INHALED_CONSTANT * mbq * e.alpha();
            }
    }

    private static void blockEntities(Level level, BlockPos origin, Vec3 eye, long now, double scale, Sum sum) {
        int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null)
                    continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    double d2 = be.getBlockPos().distSqr(origin);
                    if (be instanceof RadiationSource rs) {
                        Emission e = d2 <= SOURCE_RADIUS * SOURCE_RADIUS ? rs.emission() : Emission.NONE;
                        if (!e.isNone())
                            sum.point(level, e, Vec3.atCenterOf(be.getBlockPos()), eye, scale);
                    } else if (be instanceof Container box && d2 <= CONTAINER_RADIUS * CONTAINER_RADIUS) {
                        Vec3 at = Vec3.atCenterOf(be.getBlockPos());
                        for (int i = 0; i < box.getContainerSize(); i++) {
                            ItemStack stack = box.getItem(i);
                            Emission e = RadiationMaps.emission(stack, now);
                            if (!e.isNone())
                                sum.point(level, e, at, eye, scale * 0.5 * RadiationMath.stackSelfShield(stack.getCount()));
                        }
                    }
                }
            }
    }

    private record Scan(Level level, BlockPos at, long time, List<BlockPos> found) {
    }

    private static final Map<UUID, Scan> SCANS = new ConcurrentHashMap<>();

    /** Radioactive blocks nearby. Scanned again after three seconds or two blocks of walking, and not at all if no block radiates. */
    private static List<BlockPos> blocksNear(Level level, Player player) {
        if (!RadiationMaps.anyBlockEmits())
            return List.of();
        BlockPos origin = player.blockPosition();
        long now = level.getGameTime();
        Scan old = SCANS.get(player.getUUID());
        if (old != null && old.level == level && now - old.time < 60 && now >= old.time && old.at.distSqr(origin) <= 4)
            return old.found;
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-BLOCK_RADIUS, -BLOCK_RADIUS, -BLOCK_RADIUS),
                origin.offset(BLOCK_RADIUS, BLOCK_RADIUS, BLOCK_RADIUS)))
            if (!RadiationMaps.emission(level.getBlockState(p)).isNone())
                found.add(p.immutable());
        SCANS.put(player.getUUID(), new Scan(level, origin, now, found));
        return found;
    }

    /** Share of the radiation that gets from one point to another through the blocks between them. */
    public static double transmission(Level level, Vec3 from, Vec3 to, RadiationMath.RadiationType type) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5)
            return 1.0;
        int steps = (int) Math.min(80, Math.ceil(len / 0.25));
        double blockCm = IsotopeConfig.BLOCK_THICKNESS_CM.get(), exponent = 0.0;
        BlockPos last = null;
        for (int i = 1; i < steps; i++) {
            BlockPos bp = BlockPos.containing(from.add(d.scale(i / (double) steps)));
            if (bp.equals(last))
                continue;
            last = bp;
            double hvl = RadiationMaps.halfValueLayerCm(level.getBlockState(bp)) * type.hvlFactor;
            if (!Double.isInfinite(hvl))
                exponent += blockCm / hvl;
        }
        return Math.pow(0.5, exponent);
    }

    // ------------------------------------------------------------------ protection

    /** Share of alpha (breathed in), beta and gamma that the worn armor stops. */
    public static double[] protection(Player player) {
        double a = 0, b = 0, g = 0;
        for (EquipmentSlot slot : ARMOR) {
            Protection p = RadiationMaps.protection(player.getItemBySlot(slot));
            if (p == null)
                continue;
            a += p.alpha();
            b += p.beta();
            g += p.gamma();
        }
        Protection head = RadiationMaps.protection(player.getItemBySlot(EquipmentSlot.HEAD));
        double alpha = head == null ? 0.0 : Math.max(head.respirator(), a);
        return new double[]{Math.min(1, alpha), Math.min(1, b), Math.min(1, g)};
    }

    // ------------------------------------------------------------------ dose

    public static void onPlayerTick(PlayerTickEvent.Post ev) {
        Player player = ev.getEntity();
        if (!player.level().isClientSide && player.tickCount % 20 == 0)
            second(player);
    }

    /** One second of exposure. */
    public static void second(Player player) {
        if (!IsotopeConfig.RADIATION.get() || player.isCreative() || player.isSpectator()) {
            player.setData(DoseAttachments.AMBIENT_USV.get(), 0f);
            player.setData(DoseAttachments.EFFECTIVE_USV.get(), 0f);
            return;
        }
        double[] p = protection(player);
        ExposureEvent ev = new ExposureEvent(player, exposure(player), p[0], p[1], p[2]);
        double effective = NeoForge.EVENT_BUS.post(ev).isCanceled() ? 0 : ev.effective();

        float before = player.getData(DoseAttachments.DOSE_MSV.get());
        float dose = RadiationMath.doseAfterSecond(before, effective, IsotopeConfig.DOSE_RECOVERY.get(), IsotopeConfig.DOSE_HALF_LIFE.get());
        player.setData(DoseAttachments.AMBIENT_USV.get(), (float) ev.reading());
        player.setData(DoseAttachments.EFFECTIVE_USV.get(), (float) effective);
        if (dose != before)
            player.setData(DoseAttachments.DOSE_MSV.get(), dose);
        if (IsotopeConfig.SICKNESS.get())
            sickness(player, dose);
    }

    private static void sickness(Player player, float msv) {
        if (msv >= 100f) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, msv >= 1000f ? 1 : 0, false, true, true));
        }
        if (msv < 1000f || !(player.level() instanceof ServerLevel level))
            return;
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1, false, false, true));
        float sv = msv / 1000f;
        if (player.getRandom().nextFloat() >= Math.min(0.15f, 0.008f * sv * sv))
            return;
        float damage = msv >= 6000f ? 6f : msv >= 4000f ? 4f : msv >= 2000f ? 2f : 1f;
        player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE)), damage);
    }

    public static void onHeal(LivingHealEvent ev) {
        if (IsotopeConfig.SICKNESS.get() && ev.getEntity() instanceof Player p && p.getData(DoseAttachments.DOSE_MSV.get()) >= 1000f)
            ev.setCanceled(true);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent ev) {
        SCANS.remove(ev.getEntity().getUUID());
    }
}
