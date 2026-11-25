package dev.isotopelib.fallout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The fallout field of a dimension, saved as isotope_fallout. A world without one is searched for goingnuclear_fallout, the file
 * of Going Nuclear before 0.612, and goes on from there.
 */
public final class FalloutData extends SavedData {
    public static final String NAME = "isotope_fallout";
    public static final String LEGACY_NAME = "goingnuclear_fallout";
    private static final int INTERVAL = 100;

    private final FalloutField field;

    private FalloutData(FalloutField field) {
        this.field = field;
    }

    public static FalloutData get(ServerLevel level) {
        return get(level.getDataStorage());
    }

    public static FalloutData get(DimensionDataStorage store) {
        FalloutData data = store.get(new Factory<>(() -> new FalloutData(new FalloutField()), FalloutData::load, null), NAME);
        if (data != null)
            return data;
        FalloutData old = store.get(new Factory<>(() -> new FalloutData(new FalloutField()), FalloutData::loadLegacy, null), LEGACY_NAME);
        // A new object: the storage would otherwise write the same one back under the old name too.
        data = new FalloutData(old == null ? new FalloutField() : old.field);
        if (old != null)
            data.setDirty();
        store.set(NAME, data);
        return data;
    }

    public FalloutField field() {
        return field;
    }

    public void release(BlockPos pos, double strength) {
        field.release(pos.getX(), pos.getZ(), strength);
        setDirty();
    }

    public int clearAround(double x, double z, int radius) {
        int n = field.clearAround(x, z, radius);
        if (n > 0)
            setDirty();
        return n;
    }

    public static void onLevelTick(LevelTickEvent.Post ev) {
        if (!(ev.getLevel() instanceof ServerLevel level) || level.getGameTime() % 4 != 0)
            return;
        FalloutData data = get(level);
        if (data.field.isEmpty())
            return;
        long time = level.getGameTime();
        if (time % INTERVAL == 0) {
            data.field.step(level.random::nextDouble);
            data.setDirty();
        }
        FalloutEffects.tick(level, data.field, time);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
        Map<Long, Float> cells = field.cells();
        long[] keys = new long[cells.size()];
        int[] values = new int[cells.size()];
        int i = 0;
        for (Map.Entry<Long, Float> e : cells.entrySet()) {
            keys[i] = e.getKey();
            values[i++] = Float.floatToIntBits(e.getValue());
        }
        tag.putLongArray("Chunks", keys);
        tag.putIntArray("Strength", values);
        tag.putLongArray("Sources", field.sources().stream().mapToLong(s -> FalloutField.key((int) s[0], (int) s[1])).toArray());
        tag.putFloat("WindAngle", field.windAngle());
        tag.putFloat("WindSpeed", field.windSpeed());
        return tag;
    }

    public static FalloutData load(CompoundTag tag, HolderLookup.Provider regs) {
        FalloutField f = new FalloutField();
        long[] keys = tag.getLongArray("Chunks");
        int[] values = tag.getIntArray("Strength");
        for (int i = 0; i < keys.length && i < values.length; i++)
            f.cells().put(keys[i], Float.intBitsToFloat(values[i]));
        for (long k : tag.getLongArray("Sources"))
            f.sources().add(new long[]{FalloutField.keyX(k), FalloutField.keyZ(k)});
        f.setWind(tag.getFloat("WindAngle"), tag.contains("WindSpeed") ? tag.getFloat("WindSpeed") : 0.5f);
        return new FalloutData(f);
    }

    public static FalloutData loadLegacy(CompoundTag tag, HolderLookup.Provider regs) {
        Map<Long, Float> old = new HashMap<>();
        ListTag keys = tag.getList("Keys", Tag.TAG_LONG), values = tag.getList("Values", Tag.TAG_FLOAT);
        for (int i = 0; i < keys.size() && i < values.size(); i++)
            old.put(((LongTag) keys.get(i)).getAsLong(), values.getFloat(i));
        List<long[]> sources = new ArrayList<>();
        for (long l : tag.getLongArray("Sources")) {
            BlockPos p = BlockPos.of(l);
            sources.add(new long[]{p.getX(), p.getZ()});
        }
        return new FalloutData(FalloutField.fromLegacy(old, sources, tag.getFloat("WindAngle"),
                tag.contains("WindSpeed") ? tag.getFloat("WindSpeed") : 0.5f));
    }
}
