package dripleaffinder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public final class Finder {
    static final int COLOR = 0xFF00FF00;
    static final int PREDICTED_COLOR = 0xFFFFAA00;
    static final int LUSH_COLOR = 0xFFF0F0F0;
    static final int AZALEA_COLOR = 0xFFFF66CC;
    static final int DRAW_RANGE = 384, LUSH_PING = 128;
    public static final float LINE_WIDTH = 1.5f;
    static final Path DATA = FabricLoader.getInstance().getConfigDir().resolve("dripleaf");

    public interface BoxSink {
        void box(AABB box, int color);
    }

    public final Settings settings;
    final Set<BlockPos> found = new HashSet<>();
    private final Set<LevelChunk> scanned = Collections.newSetFromMap(new WeakHashMap<>());
    final Map<BlockPos, String> predicted = new HashMap<>();
    private final Set<BlockPos> ordered = new HashSet<>();
    boolean folia;
    private final Map<BlockPos, Integer> near = new HashMap<>();
    private final Map<BlockPos, Integer> lush = new HashMap<>();
    private final List<AABB> azaleas = new ArrayList<>();
    final Memory memory = new Memory();
    private ClientLevel level;
    private int ticks;
    private Consumer<Boolean> enabledListener;

    public final Seeds seeds = new Seeds(this);
    public final SeedSearch search = new SeedSearch(this);
    final ServerVersion serverVersion = new ServerVersion();

    Finder(Settings settings) {
        this.settings = settings;
    }

    public void onEnabledChanged(Consumer<Boolean> listener) {
        enabledListener = listener;
    }

    public boolean enabled() {
        return settings.enabled;
    }

    public void setEnabled(boolean on) {
        if (settings.enabled == on) return;
        settings.enabled = on;
        settings.save();
        if (!on) {
            clearWorld();
            memory.forget();
        }
        message("Dripleaf Finder " + (on ? "on" : "off"));
        if (enabledListener != null) enabledListener.accept(on);
    }

    public void toggle() {
        setEnabled(!settings.enabled);
    }

    public void setMasked(boolean on) {
        if (settings.masked == on) return;
        settings.masked = on;
        settings.save();
        scanned.clear();
    }

    public void setLush(boolean on) {
        if (settings.lush == on) return;
        settings.lush = on;
        settings.save();
    }

    public void setAzalea(boolean on) {
        if (settings.azalea == on) return;
        settings.azalea = on;
        settings.save();
        if (!on) azaleas.clear();
    }

    void status() {
        message("Dripleaf Finder " + (settings.enabled ? "on" : "off") + " | masked server " + (settings.masked ? "yes" : "no")
            + " | lush caves " + (settings.lush ? lush.size() + " known" : "off")
            + " | azalea " + (settings.azalea ? "on" : "off") + " | predictions " + predicted.size()
            + ". Commands: /dripleaf on|off, masked on|off, azalea on|off, lush [on|off], predictions, seed [<seed>|clear], version [<version>|auto], search [<radius>|stop]");
    }

    boolean lushNear(BlockPos pos) {
        return level != null && Splice.lushNear(level, pos);
    }

    private void clearWorld() {
        level = null;
        found.clear();
        scanned.clear();
        predicted.clear();
        ordered.clear();
        near.clear();
        lush.clear();
        azaleas.clear();
    }

    private void enterWorld(Minecraft mc, ClientLevel next) {
        clearWorld();
        level = next;
        memory.enter(DataKey.of(Compat.serverAddress(mc), Compat.singleplayerWorld(mc)));
        checkServerType();
        reloadData();
        if (!predicted.isEmpty() && !memory.predictionsAnnounced && mc.player != null) {
            memory.predictionsAnnounced = true;
            BlockPos here = mc.player.blockPosition();
            List<BlockPos> shown = shown();
            if (!shown.isEmpty()) message(shown.size() + " spliced small dripleaf top halves predicted from the seed for " + memory.key() + ":");
            shown.stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(here))).limit(5).forEach(pos ->
                message("  " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (" + predicted.get(pos) + ", "
                    + (int) Math.sqrt(pos.distSqr(here)) + " blocks away)"));
            if (shown.size() < predicted.size()) message(hiddenNote());
        }
    }

    void checkServerType() {
        folia = ServerType.FOLIA.equals(seeds.serverType()[0]);
    }

    boolean hidden(BlockPos pos) {
        return folia && ordered.contains(pos);
    }

    List<BlockPos> shown() {
        return predicted.keySet().stream().filter(pos -> !hidden(pos)).toList();
    }

    String hiddenNote() {
        return ServerType.hiddenNote(predicted.keySet().stream().filter(this::hidden).count(), predicted.size());
    }

    void reloadData() {
        predicted.clear();
        ordered.clear();
        lush.clear();
        if (level == null || !Compat.overworld(level)) return;
        Path folder = DATA.resolve(memory.key());
        for (String line : lines(folder.resolve("predictions.tsv"))) {
            Prediction row = Prediction.parse(line);
            if (row != null && row.survived()) addPrediction(row.pos(), row.half() + " half, " + row.why(), row.anyOrder());
        }
        for (String line : lines(folder.resolve("lush.tsv"))) {
            int[] f = Data.ints(line, 4);
            if (f != null) lush.put(new BlockPos(f[0], f[1], f[2]), f[3]);
        }
    }

    public static String connectionKey(Minecraft mc) {
        return mc.level == null ? null : DataKey.of(Compat.serverAddress(mc), Compat.singleplayerWorld(mc));
    }

    void markPredictionsAnnounced() {
        memory.predictionsAnnounced = true;
    }

    void addPrediction(BlockPos pos, String text, boolean anyOrder) {
        if (level == null || !Compat.overworld(level) || memory.settled.contains(pos)) return;
        predicted.put(pos, text);
        if (anyOrder) ordered.remove(pos);
        else ordered.add(pos);
    }

    void announceSavedOnReturn(String key) {
        if (key.equals(memory.key())) memory.predictionsAnnounced = false;
    }

    void announce(String text) {
        message(text);
        Compat.toast(Minecraft.getInstance(), text);
    }

    private List<String> lines(Path file) {
        try {
            return Data.lines(file);
        } catch (IOException | RuntimeException e) {
            message("Could not read " + file + " (" + e.getMessage() + ")");
            return List.of();
        }
    }

    static double flatDistance(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    static String lushText(BlockPos pos, int floor) {
        return "Lush cave at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (densest chunk of a 128-block area with "
            + floor + " lush cave floor blocks)";
    }

    private String whyEmpty(String none, String noneKnown) {
        if (!settings.enabled) return none + ". The finder is off.";
        if (level == null) return none + ". Join a world first.";
        if (!Compat.overworld(level)) return none + ". Only kept for the Overworld.";
        return noneKnown + " for " + memory.key() + " yet. Set World Seed and press Seed Search.";
    }

    public void listNearestLush() {
        if (lush.isEmpty()) {
            message(whyEmpty("No lush caves", "No lush caves known"));
            return;
        }
        BlockPos here = Minecraft.getInstance().player.blockPosition();
        lush.keySet().stream().sorted(Comparator.comparingDouble(pos -> flatDistance(pos, here))).limit(5).forEach(pos ->
            message(lushText(pos, lush.get(pos)) + ", " + (int) flatDistance(pos, here) + " blocks away"));
    }

    public void listNearest() {
        List<BlockPos> shown = shown();
        if (shown.isEmpty()) {
            message(predicted.isEmpty() ? whyEmpty("No predicted positions", "No spliced dripleaf predicted") : hiddenNote());
            return;
        }
        BlockPos here = Minecraft.getInstance().player.blockPosition();
        shown.stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(here))).limit(5).forEach(pos ->
            message("Predicted at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (" + predicted.get(pos)
                + ", " + (int) Math.sqrt(pos.distSqr(here)) + " blocks away)"));
    }

    void tick(Minecraft mc) {
        if (!settings.enabled) return;
        if (mc.level == null || mc.player == null) {
            if (level != null) clearWorld();
            return;
        }
        if (mc.level != level) enterWorld(mc, mc.level);
        if (ticks % 100 == 0) scanned.clear();
        if (ticks++ % 10 != 0) return;
        checkServerType();
        boolean masked = settings.masked;
        found.removeIf(pos -> {
            LevelChunk chunk = Compat.loadedChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
            return chunk != null && !Splice.spliced(chunk, pos, chunk.getBlockState(pos), masked, this::lushNear);
        });
        BlockPos here = mc.player.blockPosition();
        int radius = Compat.renderDistance(mc) + 2, cx = here.getX() >> 4, cz = here.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = Compat.loadedChunk(level, cx + dx, cz + dz);
                if (chunk == null || !scanned.add(chunk)) continue;
                Splice.scan(chunk, masked, this::lushNear, this::report);
                if (settings.azalea) Azalea.canopies(level, chunk).forEach(this::azalea);
            }
        }
        predicted.keySet().removeIf(pos -> {
            if (hidden(pos) || pos.distSqr(here) > Splice.SETTLE_RANGE * Splice.SETTLE_RANGE || Compat.loadedChunk(level, pos.getX() >> 4, pos.getZ() >> 4) == null) {
                near.remove(pos);
                return false;
            }
            Boolean there = Splice.settle(level, pos, near.merge(pos, 10, Integer::sum), this::lushNear);
            if (there == null) return false;
            if (there) report(pos, level.getBlockState(pos));
            else message("Predicted spliced dripleaf at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " is not there");
            memory.settled.add(pos);
            near.remove(pos);
            return true;
        });
        if (settings.lush) {
            for (Map.Entry<BlockPos, Integer> tile : lush.entrySet()) {
                BlockPos pos = tile.getKey();
                if (flatDistance(pos, here) > LUSH_PING || !memory.lushPinged.add(pos)) continue;
                String text = lushText(pos, tile.getValue());
                message(text);
                Compat.toast(mc, text);
            }
        }
    }

    private void azalea(AABB box) {
        for (int i = 0; i < azaleas.size(); i++) {
            if (azaleas.get(i).inflate(1).intersects(box)) {
                azaleas.set(i, azaleas.get(i).minmax(box));
                return;
            }
        }
        azaleas.add(box);
    }

    private void report(BlockPos pos, BlockState state) {
        found.add(pos);
        if (!memory.announced.add(pos)) return;
        announce("Spliced small dripleaf top half at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    public void boxes(double camX, double camY, double camZ, BoxSink sink) {
        Minecraft mc = Minecraft.getInstance();
        if (!settings.enabled || level == null || Compat.hudHidden(mc)) return;
        double range = (double) DRAW_RANGE * DRAW_RANGE;
        for (BlockPos pos : found) sink.box(new AABB(pos), COLOR);
        if (settings.lush) {
            for (BlockPos pos : lush.keySet()) {
                if (distSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, camX, camY, camZ) > range) continue;
                sink.box(new AABB(pos.getX() - 8, pos.getY() - 1, pos.getZ() - 8, pos.getX() + 8, pos.getY() + 3, pos.getZ() + 8), LUSH_COLOR);
            }
        }
        if (settings.azalea) {
            for (AABB box : azaleas) {
                if (distSq((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2, camX, camY, camZ) > range) continue;
                sink.box(box, AZALEA_COLOR);
            }
        }
        for (BlockPos pos : predicted.keySet()) {
            if (found.contains(pos) || hidden(pos)) continue;
            if (distSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, camX, camY, camZ) > range) continue;
            sink.box(new AABB(pos), PREDICTED_COLOR);
        }
    }

    private static double distSq(double x, double y, double z, double cx, double cy, double cz) {
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
    }

    static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) Compat.chat(mc, text);
    }
}
