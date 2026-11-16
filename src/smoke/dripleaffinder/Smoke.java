package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class Smoke implements ClientModInitializer {
    private Path out, tpFile;
    private BlockPos target, negative;
    private double[] camera;
    private String[] search, realworld;
    private boolean searchStarted;
    private int ticks, joinedAt = -1, foundAt = -1, arrivedAt = -1, shotAt = -1, searchDoneAt = -1;
    private BlockPos chosen;
    private java.util.List<BlockPos> targets;
    private int visited, confirmed;
    private boolean widened, shotFirst;
    private int judged;
    private final java.util.Set<Long> stood = new java.util.HashSet<>();
    private int authorityAt = -1;
    private String notes = "";
    private String summary;
    private String result, chosenText, connect;

    @Override
    public void onInitializeClient() {
        String file = System.getProperty("dripleaffinder.smoke");
        if (file == null) return;
        out = Path.of(file);
        FabricLoader.getInstance().getModContainer("dripleaf-finder").ifPresent(mod -> System.out.println("SMOKE mod jar: " + mod.getOrigin().getPaths().get(0)));
        target = pos(System.getProperty("dripleaffinder.smoke.pos"));
        negative = pos(System.getProperty("dripleaffinder.smoke.negative"));
        connect = System.getProperty("dripleaffinder.smoke.connect");
        camera = Arrays.stream(System.getProperty("dripleaffinder.smoke.camera").split(",")).mapToDouble(Double::parseDouble).toArray();
        if (System.getProperty("dripleaffinder.smoke.search") != null) search = System.getProperty("dripleaffinder.smoke.search").split(",");
        if (System.getProperty("dripleaffinder.smoke.realworld") != null) {
            realworld = System.getProperty("dripleaffinder.smoke.realworld").split(",");
            tpFile = Path.of(System.getProperty("dripleaffinder.smoke.tp"));
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static BlockPos pos(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] at = text.split(",");
        return new BlockPos(Integer.parseInt(at[0]), Integer.parseInt(at[1]), Integer.parseInt(at[2]));
    }

    private void tick(Minecraft mc) {
        ticks++;
        if (shotAt >= 0) {
            if (ticks - shotAt >= 60) finish(mc);
            return;
        }
        if (connect != null && SmokeConnect.connect(mc, connect)) connect = null;
        if (mc.level == null || mc.player == null) {
            if (ticks > 20 * 300) shoot(mc, "FAIL never joined the test server");
            return;
        }
        Finder finder = DripleafClient.finder();
        if (joinedAt < 0) {
            joinedAt = ticks;
            if (!finder.enabled()) finder.setEnabled(true);
        }
        if (realworld != null) realworldTick(mc, finder);
        else if (search != null) searchTick(mc, finder);
        else plantedTick(mc, finder);
    }

    private boolean near(Minecraft mc, double x, double z, double distance) {
        return mc.player.position().distanceToSqr(x, mc.player.position().y, z) < distance * distance;
    }

    private void plantedTick(Minecraft mc, Finder finder) {
        if (foundAt < 0 && finder.found.contains(target)) foundAt = ticks;
        if (arrivedAt < 0 && mc.player.position().distanceToSqr(camera[0], camera[1], camera[2]) < 0.25) arrivedAt = ticks;
        if (foundAt >= 0 && arrivedAt >= 0 && ticks - Math.max(foundAt, arrivedAt) >= 60) {
            if (negative != null && finder.found.contains(negative)) {
                shoot(mc, "FAIL reported the half outside lush caves at " + negative.getX() + " " + negative.getY() + " " + negative.getZ());
                return;
            }
            shoot(mc, "PASS reported " + (foundAt - joinedAt) / 20 + " s after joining" + (negative != null ? "; the half outside lush caves was not" : ""));
        } else if (ticks - joinedAt > 20 * 90) {
            shoot(mc, "FAIL " + (foundAt < 0 ? "not reported" : "never teleported") + ": " + diagnose(mc, finder, target));
        }
    }

    private void searchTick(Minecraft mc, Finder finder) {
        if (arrivedAt < 0 && near(mc, camera[0], camera[2], 2)) arrivedAt = ticks;
        if (!searchStarted) {
            if (arrivedAt < 0 || ticks - arrivedAt < 40) {
                if (ticks - joinedAt > 20 * 90) shoot(mc, "FAIL never teleported to the search centre");
                return;
            }
            String flag = search.length > 2 ? search[2] : "";
            String[] before = finder.search.generationVersion();
            notes += "version before typing: " + before[0] + " (" + before[1] + "); ";
            String ping = System.getProperty("dripleaffinder.smoke.ping");
            if (ping != null) notes += "pinged " + ping + ": " + ServerVersion.detect(ping) + "; ";
            if (flag.startsWith("version=")) finder.search.setVersion(flag.substring(8));
            if (flag.equals("authority")) {
                finder.search.setSeed(search[0]);
                long other = Long.parseLong(search[0]) + 1;
                new Thread(() -> FabricLoader.getInstance().getEntrypoints("seedcrackerx", SeedCrackerAPI.class)
                    .forEach(api -> api.pushWorldSeed(other)), "smoke SeedcrackerX").start();
                authorityAt = ticks;
                searchStarted = true;
                return;
            }
            startSearch(mc, finder, search[0], Integer.parseInt(search[1]), flag.equals("seedcracker"));
            return;
        }
        if (authorityAt >= 0) {
            if (ticks - authorityAt < 60) return;
            String handed = Data.read(Finder.DATA.resolve(Finder.connectionKey(mc)).resolve("seedcracker.txt"));
            boolean kept = search[0].equals(finder.search.seed()) && !finder.search.running()
                && String.valueOf(Long.parseLong(search[0]) + 1).equals(handed);
            notes += "typed seed kept over SeedcrackerX's: " + kept + " (SeedcrackerX's stored: " + handed + "); ";
            authorityAt = -1;
            if (!kept) {
                shoot(mc, "FAIL " + notes + "seed now " + finder.search.seed() + ", search running " + finder.search.running());
                return;
            }
            finder.search.start(mc, Integer.parseInt(search[1]));
            return;
        }
        if (!searchDone(finder)) {
            if (ticks - arrivedAt > 20 * 900) shoot(mc, "FAIL search still running after 15 min");
            return;
        }
        String last = finder.search.lastResult, saved = saved(finder);
        boolean refused = !SearchCompat.SUPPORTED && last != null && last.startsWith("Lush caves only generate");
        boolean ok = refused || last != null && last.startsWith("Seed search done") && !finder.search.hits.isEmpty()
            && finder.search.hits.keySet().stream().allMatch(pos -> saved.contains(pos.getX() + "\t" + pos.getY() + "\t" + pos.getZ() + "\ttop"));
        shoot(mc, (ok ? "PASS " : "FAIL ") + notes + last + " Saved: " + saved.replace('\n', '|'));
    }

    private void startSearch(Minecraft mc, Finder finder, String seed, int radius, boolean viaSeedcracker) {
        if (viaSeedcracker) {
            long value = Long.parseLong(seed);
            new Thread(() -> FabricLoader.getInstance().getEntrypoints("seedcrackerx", SeedCrackerAPI.class)
                .forEach(api -> api.pushWorldSeed(value)), "smoke SeedcrackerX").start();
        } else {
            finder.search.setSeed(seed);
            finder.search.start(mc, radius);
        }
        searchStarted = true;
    }

    private boolean searchDone(Finder finder) {
        return !finder.search.running() && (finder.search.lastResult != null || ticks - arrivedAt > 20 * 60);
    }

    private static String saved(Finder finder) {
        try {
            return Files.readString(Finder.DATA.resolve(Finder.connectionKey(Minecraft.getInstance())).resolve("predictions.tsv"));
        } catch (IOException | RuntimeException e) {
            return "no predictions.tsv (" + e.getMessage() + ")";
        }
    }

    private void realworldTick(Minecraft mc, Finder finder) {
        boolean scx = realworld.length > 3 && realworld[3].equals("scx");
        if (Compat.overworld(mc.level)) stood.add(Searcher.key(mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4));
        if (chosen == null) {
            if (scx && !searchStarted) {
                if (finder.search.running()) {
                    searchStarted = true;
                    arrivedAt = ticks;
                } else if (ticks - joinedAt > 20 * 900) {
                    shoot(mc, "FAIL SeedcrackerX did not hand over a seed in 15 min");
                }
                return;
            }
            if (arrivedAt < 0 && near(mc, camera[0], camera[2], 2)) arrivedAt = ticks;
            if (!searchStarted) {
                if (arrivedAt < 0 || ticks - arrivedAt < 40) {
                    if (ticks - joinedAt > 20 * 90) shoot(mc, "FAIL never teleported to the search centre");
                    return;
                }
                startSearch(mc, finder, realworld[0], Integer.parseInt(realworld[1]), realworld.length > 3 && realworld[3].equals("seedcracker"));
                return;
            }
            if (!searchDone(finder)) {
                if (ticks - arrivedAt > 20 * 3000) shoot(mc, "FAIL search still running after 50 min");
                return;
            }
            String last = finder.search.lastResult;
            if (last != null && last.startsWith("Seed search done") && finder.search.hits.isEmpty() && !widened && Integer.parseInt(realworld[1]) < 1500) {
                widened = true;
                finder.search.lastResult = null;
                finder.search.start(mc, 1500);
                return;
            }
            if (last == null || !last.startsWith("Seed search done") || finder.search.hits.isEmpty()) {
                shoot(mc, "FAIL " + (finder.search.hits.isEmpty() && last != null && last.startsWith("Seed search done") ? "no predicted top half to visit: " : "") + last);
                return;
            }
            if (scx && !realworld[0].equals(finder.search.seed())) {
                shoot(mc, "FAIL SeedcrackerX's seed " + finder.search.seed() + " is not the world's " + realworld[0]);
                return;
            }
            BlockPos center = searchCenter(finder);
            targets = finder.search.hits.keySet().stream().sorted(Comparator.comparingDouble(pos -> Finder.flatDistance(pos, center))).toList();
            summary = (scx ? "SeedcrackerX cracked " + realworld[0] + "; " : "") + finder.search.hits.size() + " predicted:";
            next(finder);
            return;
        }
        if (arrivedAt < 0) {
            if (near(mc, chosen.getX() + 0.5, chosen.getZ() + 0.5, 6)) arrivedAt = ticks;
            else if (ticks - searchDoneAt > 20 * 120) {
                summary += " " + chosenText + " never reached;";
                next(finder);
            }
            return;
        }
        boolean found = finder.found.contains(chosen), settled = finder.memory.settled.contains(chosen) && !finder.predicted.containsKey(chosen);
        if (ticks - arrivedAt >= 40 && (found && settled || settled && ticks - arrivedAt > 20 * 5) || ticks - arrivedAt > 20 * 45) {
            boolean loaded = Compat.loadedChunk(mc.level, chosen.getX() >> 4, chosen.getZ() >> 4) != null;
            if (found || settled || loaded) judged++;
            if (found && confirmed++ == 0) {
                SmokeCompat.screenshot(mc);
                shotFirst = true;
            }
            summary += " " + chosenText + (found ? " SPLICED (below " + WorldCompat.blockPath(mc.level.getBlockState(chosen.below()).getBlock()) + ")"
                : !settled && !loaded ? " its chunk never loaded in 45 s (not judged)"
                : " not there (" + mc.level.getBlockState(chosen).getBlock() + " over " + mc.level.getBlockState(chosen.below()).getBlock() + ")") + ";";
            if (!next(finder)) {
                String text = (confirmed > 0 ? "PASS " : "FAIL ") + confirmed + " of " + judged + " judged predictions confirmed in the world. " + summary;
                if (shotFirst) {
                    result = text;
                    shotAt = ticks;
                } else {
                    shoot(mc, text);
                }
            }
        }
    }

    private boolean next(Finder finder) {
        while (true) {
            if (visited >= targets.size() || judged >= 6) return false;
            BlockPos candidate = targets.get(visited++);
            int cx = candidate.getX() >> 4, cz = candidate.getZ() >> 4;
            if (stood.stream().noneMatch(k -> Math.max(Math.abs(Searcher.x(k) - cx), Math.abs(Searcher.z(k) - cz)) <= 9)) {
                chosen = candidate;
                break;
            }
            summary += " " + candidate.getX() + " " + candidate.getY() + " " + candidate.getZ() + " skipped (the player was near before);";
        }
        String[] hit = finder.search.hits.get(chosen);
        double[] from = Guidance.approach(hit[2], hit[3]);
        String order = from == null ? "any" : String.format(java.util.Locale.ROOT, "%.3f %.3f", from[0], from[1]);
        chosenText = chosen.getX() + " " + chosen.getY() + " " + chosen.getZ() + " (" + hit[1] + "; "
            + Guidance.text(chosen.getX(), chosen.getZ(), hit[0], hit[2], hit[3]) + ")";
        searchDoneAt = ticks;
        arrivedAt = -1;
        try {
            Files.writeString(tpFile, visited + " " + chosen.getX() + " " + chosen.getY() + " " + chosen.getZ() + " " + order + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static BlockPos searchCenter(Finder finder) {
        try {
            java.lang.reflect.Field field = SeedSearch.class.getDeclaredField("center");
            field.setAccessible(true);
            return (BlockPos) field.get(finder.search);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private String diagnose(Minecraft mc, Finder finder, BlockPos at) {
        BlockState state = mc.level.getBlockState(at), below = mc.level.getBlockState(at.below());
        return "block " + state + ", below " + below + ", lush near " + Splice.lushNear(mc.level, at) + ", covered "
            + Splice.covered(mc.level, at) + ", chunk loaded " + (Compat.loadedChunk(mc.level, at.getX() >> 4, at.getZ() >> 4) != null)
            + ", player at " + mc.player.position() + ", finder on " + finder.enabled() + ", found " + finder.found.contains(at)
            + ", settled " + finder.memory.settled.contains(at);
    }

    private void shoot(Minecraft mc, String result) {
        this.result = result;
        SmokeCompat.screenshot(mc);
        shotAt = ticks;
    }

    private void finish(Minecraft mc) {
        try {
            Files.writeString(out, "SMOKE " + result + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        shotAt = Integer.MAX_VALUE / 2;
        mc.stop();
    }
}
