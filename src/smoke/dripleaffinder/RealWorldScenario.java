package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

final class RealWorldScenario implements Scenario {
    private final Smoke smoke;
    private final String seed, flag;
    private final int radius;
    private final double[] camera;
    private final Path tpFile;
    private final Set<Long> stood = new HashSet<>();
    private boolean started, widened, shotFirst;
    private int arrivedAt = -1, searchDoneAt = -1, visited, confirmed, judged;
    private List<BlockPos> targets;
    private BlockPos chosen;
    private String chosenText, summary;

    RealWorldScenario(Smoke smoke, String[] args, double[] camera, Path tpFile) {
        this.smoke = smoke;
        this.seed = args[0];
        this.radius = Integer.parseInt(args[1]);
        this.flag = args.length > 3 ? args[3] : "";
        this.camera = camera;
        this.tpFile = tpFile;
    }

    @Override
    public void tick(Minecraft mc, Finder finder) {
        if (Compat.overworld(mc.level)) stood.add(Searcher.key(mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4));
        if (chosen == null) searchTick(mc, finder);
        else visitTick(mc, finder);
    }

    private void searchTick(Minecraft mc, Finder finder) {
        int ticks = smoke.ticks;
        boolean scx = flag.equals("scx");
        if (scx && !started) {
            if (finder.search.running()) {
                started = true;
                arrivedAt = ticks;
            } else if (ticks - smoke.joinedAt > 20 * 900) {
                smoke.shoot(mc, "FAIL SeedcrackerX did not hand over a seed in 15 min");
            }
            return;
        }
        if (arrivedAt < 0 && Smoke.near(mc, camera[0], camera[2], 2)) arrivedAt = ticks;
        if (!started) {
            if (arrivedAt < 0 || ticks - arrivedAt < 40) {
                if (ticks - smoke.joinedAt > 20 * 90) smoke.shoot(mc, "FAIL never teleported to the search centre");
                return;
            }
            Smoke.startSearch(mc, finder, seed, radius, flag.equals("seedcracker"));
            started = true;
            return;
        }
        if (!smoke.searchDone(finder, arrivedAt)) {
            if (ticks - arrivedAt > 20 * 3000) smoke.shoot(mc, "FAIL search still running after 50 min");
            return;
        }
        String last = finder.search.lastResult();
        boolean done = last != null && last.startsWith("Done:");
        if (done && finder.search.hits.isEmpty() && !widened && radius < 1500) {
            widened = true;
            finder.search.results.remove(Finder.connectionKey(mc));
            finder.search.start(mc, 1500, false);
            return;
        }
        if (!done || finder.search.hits.isEmpty()) {
            smoke.shoot(mc, "FAIL " + (finder.search.hits.isEmpty() && done ? "no predicted top half to visit: " : "") + last);
            return;
        }
        if (scx && !seed.equals(finder.seeds.seed())) {
            smoke.shoot(mc, "FAIL SeedcrackerX's seed " + finder.seeds.seed() + " is not the world's " + seed);
            return;
        }
        BlockPos center = finder.search.center();
        targets = finder.search.hits.keySet().stream().sorted(Comparator.comparingDouble(pos -> Finder.flatDistance(pos, center))).toList();
        summary = (scx ? "SeedcrackerX cracked " + seed + "; " : "") + finder.search.hits.size() + " predicted:";
        next(finder);
    }

    private void visitTick(Minecraft mc, Finder finder) {
        int ticks = smoke.ticks;
        if (arrivedAt < 0) {
            if (Smoke.near(mc, chosen.getX() + 0.5, chosen.getZ() + 0.5, 6)) arrivedAt = ticks;
            else if (ticks - searchDoneAt > 20 * 120) {
                summary += " " + chosenText + " never reached;";
                next(finder);
            }
            return;
        }
        boolean found = finder.found.contains(chosen), settled = finder.memory.settled.contains(chosen) && !finder.predicted.containsKey(chosen);
        if (!(ticks - arrivedAt >= 40 && (found && settled || settled && ticks - arrivedAt > 20 * 5) || ticks - arrivedAt > 20 * 45)) return;
        boolean loaded = Compat.loadedChunk(mc.level, chosen.getX() >> 4, chosen.getZ() >> 4) != null;
        if (found || settled || loaded) judged++;
        if (found && confirmed++ == 0) {
            SmokeCompat.screenshot(mc);
            shotFirst = true;
        }
        summary += " " + chosenText + (found ? " SPLICED (below " + WorldCompat.blockPath(mc.level.getBlockState(chosen.below()).getBlock()) + ")"
            : !settled && !loaded ? " its chunk never loaded in 45 s (not judged)"
            : " not there (" + mc.level.getBlockState(chosen).getBlock() + " over " + mc.level.getBlockState(chosen.below()).getBlock() + ")") + ";";
        if (next(finder)) return;
        String text = (confirmed > 0 ? "PASS " : "FAIL ") + confirmed + " of " + judged + " judged predictions confirmed in the world. " + summary;
        if (shotFirst) smoke.end(text);
        else smoke.shoot(mc, text);
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
        double[] from = approach(hit[2], hit[3]);
        String order = from == null ? "any" : String.format(Locale.ROOT, "%.3f %.3f", from[0], from[1]);
        chosenText = chosen.getX() + " " + chosen.getY() + " " + chosen.getZ() + " (" + hit[1] + "; "
            + Guidance.text(chosen.getX(), chosen.getZ(), hit[0], hit[2], hit[3]) + ")";
        searchDoneAt = smoke.ticks;
        arrivedAt = -1;
        try {
            Files.writeString(tpFile, visited + " " + chosen.getX() + " " + chosen.getY() + " " + chosen.getZ() + " " + order + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static double[] approach(String first, String then) {
        if (Guidance.anyOrder(first, then) || !Guidance.known(first) || !Guidance.known(then)) return null;
        String[] a = first.split(","), b = then.split(",");
        double dx = Integer.parseInt(a[0]) - Integer.parseInt(b[0]), dz = Integer.parseInt(a[1]) - Integer.parseInt(b[1]);
        double length = Math.hypot(dx, dz);
        return new double[] {dx / length, dz / length};
    }
}
