package dripleaffinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

final class Azalea {
    static final int MAX_CANOPY = 400;

    static boolean isLeaves(BlockState state) {
        return state.is(Blocks.AZALEA_LEAVES) || state.is(Blocks.FLOWERING_AZALEA_LEAVES);
    }

    static List<AABB> canopies(BlockGetter world, ChunkAccess chunk) {
        List<BlockPos> seeds = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (!WorldCompat.maybeHas(section, Azalea::isLeaves)) continue;
            int baseY = WorldCompat.sectionBaseY(chunk, i);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isLeaves(section.getBlockState(x, y, z))) seeds.add(chunk.getPos().getBlockAt(x, baseY + y, z));
                    }
                }
            }
        }
        Set<BlockPos> seen = new HashSet<>();
        List<AABB> boxes = new ArrayList<>();
        for (BlockPos seed : seeds) {
            if (!seen.add(seed)) continue;
            ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(seed));
            AABB box = new AABB(seed);
            boolean trunk = false;
            int size = 0;
            while (!todo.isEmpty() && size++ < MAX_CANOPY) {
                BlockPos pos = todo.poll();
                box = box.minmax(new AABB(pos));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos next = pos.offset(dx, dy, dz);
                            BlockState state = world.getBlockState(next);
                            if (state.is(Blocks.OAK_LOG)) trunk = true;
                            else if (isLeaves(state) && seen.add(next)) todo.add(next);
                        }
                    }
                }
            }
            if (trunk) boxes.add(box);
        }
        return boxes;
    }
}
