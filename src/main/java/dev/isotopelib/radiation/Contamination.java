package dev.isotopelib.radiation;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.isotopelib.Isotope;
import dev.isotopelib.IsotopeConfig;
import dev.isotopelib.api.Emission;
import dev.isotopelib.api.Exposure;
import dev.isotopelib.api.ExposureEvent;
import dev.isotopelib.api.Protection;
import dev.isotopelib.fallout.FalloutData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Radioactive dust on people and things. It settles from fallout (and from whatever another mod reports through
 * {@code IsotopeApi.contaminate}), sits on worn armour first and on the body where nothing is worn, and keeps radiating:
 * from the body as a dose every second, from items wherever they are, like any radioactive item. Water, rain and a water
 * cauldron wash it off; taking off the armour leaves its share on the armour.
 */
public final class Contamination {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Isotope.MODID);

    /** Becquerels of dust on an item stack. */
    public static final Supplier<DataComponentType<Float>> ITEM = COMPONENTS.registerComponentType("contamination",
            b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    /** Becquerels of dust on the skin, where no armour covers it. */
    public static final Supplier<AttachmentType<Float>> BODY = DoseAttachments.ATTACHMENTS.register("contamination",
            () -> AttachmentType.builder(() -> 0f).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Falling dust that shows a contaminated player, from about a megabecquerel on. Fallout dust is grey, not green. */
    private static final BlockParticleOption DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState());

    private Contamination() {
    }

    /** False before a world's config is loaded (e.g. tooltips on the title screen). */
    public static boolean on() {
        return IsotopeConfig.SPEC.isLoaded() && IsotopeConfig.CONTAMINATION.get() && IsotopeConfig.RADIATION.get();
    }

    public static double of(ItemStack stack) {
        Float f = stack.isEmpty() ? null : stack.get(ITEM.get());
        return f == null ? 0 : f;
    }

    public static void set(ItemStack stack, double bq) {
        if (bq < ContaminationMath.MIN_BQ)
            stack.remove(ITEM.get());
        else
            stack.set(ITEM.get(), (float) bq);
    }

    public static Emission emission(double bq) {
        return bq <= 0 ? Emission.NONE : new Emission(bq, ContaminationMath.ALPHA, ContaminationMath.BETA, ContaminationMath.GAMMA, true);
    }

    /** Dust on the skin plus dust on the worn armour, in becquerels. */
    public static double onPerson(Player player) {
        double bq = player.getData(BODY.get());
        for (EquipmentSlot slot : ARMOR)
            bq += of(player.getItemBySlot(slot));
        return bq;
    }

    /** Lets dust settle on a player: on each worn armour piece a quarter, on the skin what no armour catches. */
    public static void contaminate(Player player, double bq) {
        if (!on() || bq <= 0)
            return;
        double skin = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.isEmpty())
                skin += bq / 4;
            else
                set(worn, of(worn) + bq / 4);
        }
        if (skin > 0)
            player.setData(BODY.get(), (float) (player.getData(BODY.get()) + skin));
    }

    // ------------------------------------------------------------------ every second

    public static void onPlayerTick(PlayerTickEvent.Post ev) {
        Player player = ev.getEntity();
        if (player.level().isClientSide || player.tickCount % 20 != 11)
            return;
        if (!on()) {
            if (player.getData(BODY.get()) != 0f)
                player.setData(BODY.get(), 0f);
            return;
        }
        double settle = 0;
        if (player.level() instanceof ServerLevel level && !player.isSpectator() && !player.isCreative()) {
            double fallout = FalloutData.get(level).field().at(player.getX(), player.getZ()) * IsotopeConfig.FALLOUT_DOSE.get();
            if (fallout > 0)
                settle = fallout * ContaminationMath.DEPOSIT_PER_FALLOUT * (level.canSeeSky(player.blockPosition().above()) ? 1.0 : 0.1);
        }
        double wash = player.isInWater() ? ContaminationMath.WASH_WATER : player.isInWaterOrRain() ? ContaminationMath.WASH_RAIN : 0;
        // the dust lands on the outside: armour first, the skin where nothing is worn
        double skinShare = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.isEmpty()) {
                skinShare += 0.25;
                continue;
            }
            double before = of(worn), after = ContaminationMath.afterSecond(before, settle / 4, wash);
            if (after != before)
                set(worn, after);
        }
        double skin = player.getData(BODY.get());
        double after = ContaminationMath.afterSecond(skin, settle * skinShare, wash);
        if ((float) after != (float) skin)
            player.setData(BODY.get(), (float) after);
        double total = onPerson(player);
        if (total >= 1.0e6 && player.level() instanceof ServerLevel level) {
            int n = (int) Math.min(6, 1 + Math.log10(total / 1.0e6) * 1.5);
            level.sendParticles(DUST, player.getX(), player.getY() + 1.0, player.getZ(), n, 0.25, 0.45, 0.25, 0);
        }
    }

    /** The dose from the dust on the person, added to what the player takes this second. */
    public static void onExposure(ExposureEvent ev) {
        if (!on())
            return;
        Player player = ev.getEntity();
        double bq = onPerson(player);
        if (bq <= 0)
            return;
        Protection head = RadiationMaps.protection(player.getItemBySlot(EquipmentSlot.HEAD));
        boolean breathes = head == null || head.respirator() < 0.5;
        double[] d = ContaminationMath.doseRate(bq, IsotopeConfig.DOSE_TIME_SCALE.get(), breathes);
        Exposure e = ev.getExposure();
        ev.setExposure(new Exposure(e.gamma() + d[0], e.beta() + d[1], e.inhaled() + d[2]));
    }

    /** Items lying in water lose their dust like a person does. */
    public static void onEntityTick(EntityTickEvent.Post ev) {
        if (!(ev.getEntity() instanceof ItemEntity item) || item.level().isClientSide || item.tickCount % 20 != 0 || !item.isInWater())
            return;
        ItemStack stack = item.getItem();
        double bq = of(stack);
        if (bq > 0) {
            set(stack, ContaminationMath.afterSecond(bq, 0, ContaminationMath.WASH_WATER));
            item.setItem(stack);
        }
    }

    /** A contaminated item used on a water cauldron is washed clean, like dyed leather armour, for one level of water. */
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock ev) {
        ItemStack stack = ev.getItemStack();
        if (of(stack) <= 0)
            return;
        BlockState state = ev.getLevel().getBlockState(ev.getPos());
        if (!state.is(Blocks.WATER_CAULDRON))
            return;
        if (!ev.getLevel().isClientSide) {
            stack.remove(ITEM.get());
            LayeredCauldronBlock.lowerFillLevel(state, ev.getLevel(), ev.getPos());
            ev.getLevel().playSound(null, ev.getPos(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.2f);
        }
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.sidedSuccess(ev.getLevel().isClientSide));
    }
}
