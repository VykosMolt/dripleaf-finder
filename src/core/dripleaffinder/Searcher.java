package dripleaffinder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class Searcher implements ModInitializer {
    static final int BATCH = 256, SPAWN_SCAN = 12;
    static final Comparator<Long> ORDER = Comparator.<Long>comparingInt(Searcher::x).thenComparingInt(Searcher::z);

    @Override
    public void onInitialize() {
        String spec = System.getProperty("dripleaffinder.search");
        if (spec == null) return;
        Thread thread = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 600_000;
            while (System.currentTimeMillis() < deadline) {
                @SuppressWarnings("deprecation")
                Object game = FabricLoader.getInstance().getGameInstance();
                if (game instanceof MinecraftServer server && server.isReady()) {
                    run(server, Path.of(spec));
                    return;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
            }
            Runtime.getRuntime().halt(1);
        }, "Dripleaf seed search");
        thread.setDaemon(true);
        thread.start();
    }

    static void run(MinecraftServer server, Path specFile) {
        Out out = null;
        try {
            Properties spec = new Properties();
            try (Reader in = Files.newBufferedReader(specFile, StandardCharsets.UTF_8)) {
                spec.load(in);
            }
            out = new Out(Path.of(spec.getProperty("out")));
            int x = Integer.parseInt(spec.getProperty("x")), z = Integer.parseInt(spec.getProperty("z"));
            int radius = Integer.parseInt(spec.getProperty("radius"));
            ServerLevel level = server.overworld();
            long started = System.currentTimeMillis();
            out.line("status", "finding lush caves");
            Set<Long> lush = lushChunks(level, x, z, radius);
            out.line("status", lush.size() + " lush cave chunks within " + radius + " blocks");
            out.line("status", "scanning spawn");
            BlockPos spawn = SearchCompat.spawn(level);
            int reach = startupReach(server, level, spawn);
            Set<Long> startup = reach < 0 ? Set.of() : dilate(Set.of(key(spawn.getX() >> 4, spawn.getZ() >> 4)), reach + 1);
            if (!Collections.disjoint(dilate(lush, 3), startup)) out.line("spawn", spawn.getX() + "\t" + spawn.getZ() + "\t" + (reach + 2) * 16);
            lush.removeAll(startup);
            if (Boolean.parseBoolean(spec.getProperty("floors"))) {
                out.line("status", "measuring floors");
                floors(server, level, lush, out);
            }
            int hits = Boolean.parseBoolean(spec.getProperty("splices"))
                ? splices(server, level, lush, startup, "desc".equals(spec.getProperty("order")), out) : 0;
            out.line("done", hits + "\t" + (System.currentTimeMillis() - started) / 1000);
        } catch (Throwable t) {
            if (out != null) out.line("error", String.valueOf(t));
            t.printStackTrace();
        } finally {
            Runtime.getRuntime().halt(0);
        }
    }

    static Set<Long> lushChunks(ServerLevel level, int centerX, int centerZ, int radius) {
        int minX = (centerX - radius) >> 4, maxX = (centerX + radius) >> 4, minZ = (centerZ - radius) >> 4, maxZ = (centerZ + radius) >> 4;
        Set<Long> lush = ConcurrentHashMap.newKeySet();
        IntStream.rangeClosed(minX, maxX).parallel().forEach(cx -> {
            for (int cz = minZ; cz <= maxZ; cz++) {
                sample:
                for (int qx = 0; qx < 4; qx++) for (int qz = 0; qz < 4; qz++) {
                    Climate.TargetPoint column = SearchCompat.column(level, (cx << 2) + qx, (cz << 2) + qz);
                    for (int qy = -16; qy <= 31; qy++) {
                        if (SearchCompat.lushNoise(level, column, (cx << 2) + qx, qy, (cz << 2) + qz)) {
                            lush.add(key(cx, cz));
                            break sample;
                        }
                    }
                }
            }
        });
        return lush;
    }

    static void floors(MinecraftServer server, ServerLevel level, Set<Long> lush, Out out) {
        List<Long> chunks = new ArrayList<>(lush);
        Map<Long, long[]> tiles = new HashMap<>();
        for (int from = 0; from < chunks.size(); from += BATCH) {
            List<Long> batch = chunks.subList(from, Math.min(from + BATCH, chunks.size()));
            server.submit(() -> {
                CompletableFuture.runAsync(() -> batch.forEach(k -> SearchCompat.requestTerrain(level, x(k), z(k)))).join();
                List<ChunkAccess> ready = batch.stream().map(k -> SearchCompat.terrain(level, x(k), z(k))).toList();
                List<long[]> floors = IntStream.range(0, ready.size()).parallel()
                    .mapToObj(i -> floor(level, ready.get(i), x(batch.get(i)), z(batch.get(i)))).toList();
                for (int i = 0; i < batch.size(); i++) {
                    long k = batch.get(i);
                    addFloor(tiles.computeIfAbsent(key(x(k) >> 3, z(k) >> 3), t -> new long[4]), k, floors.get(i));
                }
            }).join();
        }
        for (long[] tile : tiles.values()) {
            if (tile[0] == 0) continue;
            out.line("lush", (x(tile[2]) * 16 + 8) + "\t" + Math.floorDiv(tile[3], tile[1]) + "\t" + (z(tile[2]) * 16 + 8) + "\t" + tile[0]);
        }
    }

    static void addFloor(long[] tile, long chunk, long[] floor) {
        tile[0] += floor[0];
        if (floor[0] > tile[1] || floor[0] > 0 && floor[0] == tile[1] && ORDER.compare(chunk, tile[2]) < 0) {
            tile[1] = floor[0];
            tile[2] = chunk;
            tile[3] = floor[1];
        }
    }

    static long[] floor(ServerLevel level, ChunkAccess chunk, int cx, int cz) {
        long cells = 0, summedY = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx * 16; x < cx * 16 + 16; x++) {
            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                boolean solidBelow = false;
                for (int y = SearchCompat.minY(chunk); y <= 128; y++) {
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (state.isAir() && solidBelow && SearchCompat.lushStored(level, chunk, x >> 2, y >> 2, z >> 2)) {
                        cells++;
                        summedY += y;
                    }
                    solidBelow = !state.isAir() && state.getFluidState().isEmpty();
                }
            }
        }
        return new long[] {cells, summedY};
    }

    static int startupReach(MinecraftServer server, ServerLevel level, BlockPos spawn) {
        int x = spawn.getX() >> 4, z = spawn.getZ() >> 4;
        return server.submit(() -> {
            int far = -1;
            for (int dx = -SPAWN_SCAN; dx <= SPAWN_SCAN; dx++) {
                for (int dz = -SPAWN_SCAN; dz <= SPAWN_SCAN; dz++) {
                    if (SearchCompat.decoratedAlready(level, x + dx, z + dz)) far = Math.max(far, Math.max(Math.abs(dx), Math.abs(dz)));
                }
            }
            return far;
        }).join();
    }

    static int splices(MinecraftServer server, ServerLevel level, Set<Long> lush, Set<Long> startup, boolean descending, Out out)
            throws InterruptedException {
        List<Long> decorate = new ArrayList<>(dilate(lush, 2)), report = new ArrayList<>(dilate(lush, 1));
        decorate.removeAll(startup);
        report.removeAll(dilate(startup, 1));
        decorate.sort(descending ? ORDER.reversed() : ORDER);
        Map<Long, Long> placedBy = new HashMap<>(), removedBy = new HashMap<>();
        out.line("status", "decorated 0 of " + decorate.size() + " chunks");
        for (int from = 0; from < decorate.size(); from += BATCH) {
            List<Long> batch = decorate.subList(from, Math.min(from + BATCH, decorate.size()));
            Set<Long> near = dilate(new HashSet<>(batch), 1);
            server.submit(() -> {
                CompletableFuture.runAsync(() -> near.forEach(k -> SearchCompat.requestTerrain(level, x(k), z(k)))).join();
                near.forEach(k -> SearchCompat.terrain(level, x(k), z(k)));
                for (long k : batch) {
                    Set<Long> before = bottoms(level, x(k), z(k));
                    SearchCompat.decorate(level, x(k), z(k));
                    Set<Long> after = bottoms(level, x(k), z(k));
                    for (long p : after) if (!before.contains(p)) placedBy.putIfAbsent(p, k);
                    for (long p : before) if (!after.contains(p)) removedBy.put(p, k);
                }
            }).join();
            out.line("status", "decorated " + Math.min(from + BATCH, decorate.size()) + " of " + decorate.size() + " chunks");
        }

        List<BlockPos> hits = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        long[] plants = new long[1];
        List<Long> scan = report.stream().sorted(ORDER).toList();
        Map<Long, List<BlockPos>> hitsIn = new HashMap<>();
        Map<Long, List<BlockState>> statesIn = new HashMap<>();
        for (int from = 0; from < scan.size(); from += BATCH) {
            List<Long> batch = scan.subList(from, Math.min(from + BATCH, scan.size()));
            server.submit(() -> {
                for (long k : batch) {
                    ChunkAccess chunk = SearchCompat.decorate(level, x(k), z(k));
                    for (var section : chunk.getSections()) {
                        if (!WorldCompat.maybeHas(section, Splice::isLeaf)) continue;
                        for (int i = 0; i < 4096; i++) if (Splice.isLeaf(section.getBlockState(i & 15, i >> 8, (i >> 4) & 15))) plants[0]++;
                    }
                    Splice.scan(chunk, false, at -> true, (at, state) -> {
                        hitsIn.computeIfAbsent(k, c -> new ArrayList<>()).add(at.immutable());
                        statesIn.computeIfAbsent(k, c -> new ArrayList<>()).add(state);
                    });
                }
            }).join();
        }
        for (long k : report) {
            hits.addAll(hitsIn.getOrDefault(k, List.of()));
            states.addAll(statesIn.getOrDefault(k, List.of()));
        }
        server.submit(() -> {
            for (BlockPos at : hits) level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
        }).join();
        out.line("status", hits.size() + " candidates among " + plants[0] + " small dripleaf blocks; checking them after their chunks tick for 10 s");

        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline && !server.submit(() ->
            hits.stream().allMatch(at -> level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) != null)).join()) {
            Thread.sleep(200);
        }
        int until = server.getTickCount() + Splice.SETTLE_TICKS;
        while (server.getTickCount() < until) Thread.sleep(50);
        int[] unloaded = new int[1];
        List<String> kept = server.submit(() -> {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                BlockPos at = hits.get(i);
                if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) {
                    unloaded[0]++;
                    continue;
                }
                if (level.getBlockState(at) != states.get(i) || !Splice.spliced(level, at, states.get(i), false, p -> true)) continue;
                long bottom = BlockPos.asLong(at.getX(), at.getY() - 1, at.getZ());
                lines.add(at.getX() + "\t" + at.getY() + "\t" + at.getZ() + "\t" + WorldCompat.blockPath(level.getBlockState(at.below()).getBlock())
                    + "\t" + chunk(placedBy.get(bottom)) + "\t" + chunk(removedBy.get(bottom)));
            }
            return lines;
        }).join();
        out.line("status", kept.size() + " of " + hits.size() + " still there after ticking"
            + (unloaded[0] > 0 ? " (" + unloaded[0] + " never loaded, not checked)" : ""));
        for (String line : kept) out.line("hit", line);
        return kept.size();
    }

    static String chunk(Long key) {
        return key == null ? "-" : x(key) + "," + z(key);
    }

    static Set<Long> bottoms(ServerLevel level, int cx, int cz) {
        Set<Long> out = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkAccess chunk = SearchCompat.terrain(level, cx + dx, cz + dz);
                var sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (!WorldCompat.maybeHas(sections[i], Splice::isLeaf)) continue;
                    int baseY = WorldCompat.sectionBaseY(chunk, i);
                    for (int j = 0; j < 4096; j++) {
                        BlockState state = sections[i].getBlockState(j & 15, j >> 8, (j >> 4) & 15);
                        if (Splice.isLeaf(state) && !Splice.isUpper(state)) {
                            out.add(BlockPos.asLong(((cx + dx) << 4) + (j & 15), baseY + (j >> 8), ((cz + dz) << 4) + ((j >> 4) & 15)));
                        }
                    }
                }
            }
        }
        return out;
    }

    static Set<Long> dilate(Set<Long> chunks, int by) {
        Set<Long> grown = new HashSet<>();
        for (long k : chunks) {
            for (int dx = -by; dx <= by; dx++) {
                for (int dz = -by; dz <= by; dz++) grown.add(key(x(k) + dx, z(k) + dz));
            }
        }
        return grown;
    }

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static int x(long key) {
        return (int) (key >> 32);
    }

    static int z(long key) {
        return (int) key;
    }

    static final class Out {
        private final BufferedWriter writer;

        Out(Path file) throws IOException {
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        synchronized void line(String kind, String text) {
            try {
                writer.write(kind + "\t" + text.replace('\n', ' ') + "\n");
                writer.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
