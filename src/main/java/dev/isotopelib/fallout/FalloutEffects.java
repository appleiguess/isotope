package dev.isotopelib.fallout;

import org.joml.Vector3f;

import dev.isotopelib.IsotopeConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** What fallout does to the land around players: plants die, grass turns to bare dirt, dust hangs in the air. */
public final class FalloutEffects {
    private FalloutEffects() {
    }

    static void tick(ServerLevel level, FalloutField field, long time) {
        for (ServerPlayer player : level.players()) {
            if (IsotopeConfig.FALLOUT_KILLS_PLANTS.get())
                plants(level, field, player);
            if (time % 8 == 0)
                dust(level, field, player);
        }
    }

    private static void plants(ServerLevel level, FalloutField field, ServerPlayer player) {
        for (int i = 0; i < 10; i++) {
            int x = player.getBlockX() + level.random.nextInt(97) - 48, z = player.getBlockZ() + level.random.nextInt(97) - 48;
            double c = field.at(x + 0.5, z + 0.5);
            if (c < 4.0 || level.random.nextDouble() > Math.min(0.8, c / 300.0) || !level.hasChunkAt(new BlockPos(x, 64, z)))
                continue;
            wither(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z), c);
        }
    }

    private static void dust(ServerLevel level, FalloutField field, ServerPlayer player) {
        for (int i = 0; i < 18; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 40, z = player.getZ() + (level.random.nextDouble() - 0.5) * 40;
            double c = field.at(x, z);
            if (c < 2.0 || level.random.nextDouble() > Math.min(1.0, c / 80.0 + 0.15))
                continue;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) + 0.4 + level.random.nextDouble() * 2.5;
            level.sendParticles(player, new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.2f), 1.3f), false, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
            if (level.random.nextInt(3) == 0)
                level.sendParticles(player, ParticleTypes.ASH, false, x, y, z, 1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    /** Kills the plants on one column of ground, starting at its top block. */
    public static void wither(ServerLevel level, BlockPos top, double c) {
        for (int dy = 0; dy < 4; dy++) {
            BlockPos pos = top.below(dy);
            BlockState s = level.getBlockState(pos);
            Block to = s.is(BlockTags.LEAVES) ? Blocks.AIR
                    : s.is(Blocks.GRASS_BLOCK) ? (c > 40 ? Blocks.COARSE_DIRT : Blocks.PODZOL)
                    : s.is(Blocks.PODZOL) && c > 40 ? Blocks.COARSE_DIRT
                    : s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.FERN)
                            || s.is(Blocks.TALL_GRASS) || s.is(Blocks.LARGE_FERN) || s.is(BlockTags.CROPS) ? (c > 20 ? Blocks.AIR : Blocks.DEAD_BUSH)
                    : null;
            if (to != null) {
                level.setBlock(pos, to.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
        }
    }
}
