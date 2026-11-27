package dev.isotopelib.radiation;

import com.mojang.serialization.Codec;

import dev.isotopelib.Isotope;
import dev.isotopelib.api.Emission;
import dev.isotopelib.api.Protection;
import dev.isotopelib.api.RadioactiveBlock;
import dev.isotopelib.api.RadioactiveItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Data maps: any mod or data pack says what radiates, shields or protects without code, and tags work as keys. */
public final class RadiationMaps {
    /** data/isotope/data_maps/item/emission.json: radiation of one item. */
    public static final DataMapType<Item, Emission> ITEM_EMISSION =
            DataMapType.builder(Isotope.id("emission"), Registries.ITEM, Emission.CODEC).synced(Emission.CODEC, false).build();

    /** data/isotope/data_maps/block/emission.json: radiation of a placed block. */
    public static final DataMapType<Block, Emission> BLOCK_EMISSION =
            DataMapType.builder(Isotope.id("emission"), Registries.BLOCK, Emission.CODEC).synced(Emission.CODEC, false).build();

    private static final Codec<Double> HVL = Codec.doubleRange(0.01, 1e6);

    /** data/isotope/data_maps/block/shielding.json: half value layer for gamma, in centimetres. */
    public static final DataMapType<Block, Double> SHIELDING =
            DataMapType.builder(Isotope.id("shielding"), Registries.BLOCK, HVL).synced(HVL, false).build();

    /** data/isotope/data_maps/item/protection.json: what a worn piece stops. */
    public static final DataMapType<Item, Protection> PROTECTION =
            DataMapType.builder(Isotope.id("protection"), Registries.ITEM, Protection.CODEC).synced(Protection.CODEC, false).build();

    private static final double WATER_HVL = 17.0, SOLID_HVL = 12.0;

    private static Boolean codedBlocks;

    private RadiationMaps() {
    }

    public static void register(RegisterDataMapTypesEvent ev) {
        ev.register(ITEM_EMISSION);
        ev.register(BLOCK_EMISSION);
        ev.register(SHIELDING);
        ev.register(PROTECTION);
    }

    /** Radiation of one item of the stack. */
    public static Emission emission(ItemStack stack, long gameTime) {
        if (stack.isEmpty())
            return Emission.NONE;
        Emission e = stack.getItem() instanceof RadioactiveItem r ? r.emission(stack, gameTime) : stack.getItemHolder().getData(ITEM_EMISSION);
        if (e == null)
            e = Emission.NONE;
        double dust = Contamination.on() ? Contamination.of(stack) : 0;
        // the dust sits on the stack as a whole; callers scale one item by the stack's self-shielding
        return dust > 0 ? combine(e, Contamination.emission(dust / Math.max(1, stack.getCount()))) : e;
    }

    /** Two sources in one: the activities add up, the shares of each kind are weighted by activity. */
    static Emission combine(Emission a, Emission b) {
        if (a.isNone())
            return b;
        if (b.isNone())
            return a;
        double t = a.activityBq() + b.activityBq();
        return new Emission(t, (a.alpha() * a.activityBq() + b.alpha() * b.activityBq()) / t,
                (a.beta() * a.activityBq() + b.beta() * b.activityBq()) / t,
                (a.gamma() * a.activityBq() + b.gamma() * b.activityBq()) / t, a.dust() || b.dust());
    }

    public static Emission emission(BlockState state) {
        Emission e = state.getBlock() instanceof RadioactiveBlock r ? r.emission(state) : state.getBlockHolder().getData(BLOCK_EMISSION);
        return e == null ? Emission.NONE : e;
    }

    public static boolean anyBlockEmits() {
        if (codedBlocks == null)
            codedBlocks = BuiltInRegistries.BLOCK.stream().anyMatch(RadioactiveBlock.class::isInstance);
        return codedBlocks || !BuiltInRegistries.BLOCK.getDataMap(BLOCK_EMISSION).isEmpty();
    }

    public static Protection protection(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItemHolder().getData(PROTECTION);
    }

    /** Half value layer for gamma in centimetres, infinite when the block lets everything through. */
    public static double halfValueLayerCm(BlockState state) {
        if (state.isAir())
            return Double.POSITIVE_INFINITY;
        Double hvl = state.getBlockHolder().getData(SHIELDING);
        if (hvl != null)
            return hvl;
        if (state.getFluidState().is(FluidTags.WATER))
            return WATER_HVL;
        return state.canOcclude() ? SOLID_HVL : Double.POSITIVE_INFINITY;
    }
}
