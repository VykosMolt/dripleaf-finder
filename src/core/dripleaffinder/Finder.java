package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
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
    static final int DRAW_RANGE = 384, LUSH_PING = 128, SETTLE_RANGE = 32;
    static final Path DATA = FabricLoader.getInstance().getConfigDir().resolve("dripleaf");

    public interface BoxSink {
        void box(AABB box, int color);
    }

    public final Settings settings;
    final Set<BlockPos> found = new HashSet<>();
    private final Set<LevelChunk> scanned = Collections.newSetFromMap(new WeakHashMap<>());
    final Map<BlockPos, String> predicted = new HashMap<>();
    private final Map<BlockPos, Integer> near = new HashMap<>();
    private final Map<BlockPos, Integer> lush = new HashMap<>();
    private final List<AABB> azaleas = new ArrayList<>();
    final Memory memory = new Memory();
    private ServerProfile profile;
    private ClientLevel level;
    private int ticks;
    private Consumer<Boolean> enabledListener;
    private boolean settingsOwnedElsewhere;

    final SeedSearch search = new SeedSearch(this);

    Finder(Settings settings) {
        this.settings = settings;
    }

    public void attachFrontEnd(Consumer<Boolean> enabledListener) {
        this.enabledListener = enabledListener;
        this.settingsOwnedElsewhere = true;
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

    void set(String name, boolean value) {
        if (settingsOwnedElsewhere) {
            message("Set " + name + " in the AUTISM module (Spliced Dripleaf Finder); it owns this setting.");
            return;
        }
        apply(name, value);
        message(name + " " + (value ? "on" : "off"));
    }

    public void apply(String name, boolean value) {
        boolean before = switch (name) {
            case "masked" -> settings.masked;
            case "lush" -> settings.lush;
            case "azalea" -> settings.azalea;
            default -> throw new IllegalArgumentException(name);
        };
        if (before == value) return;
        switch (name) {
            case "masked" -> settings.masked = value;
            case "lush" -> settings.lush = value;
            default -> settings.azalea = value;
        }
        settings.save();
        if (name.equals("azalea") && !value) azaleas.clear();
        if (name.equals("masked")) scanned.clear();
    }

    void status() {
        message("Dripleaf Finder " + (settings.enabled ? "on" : "off") + " | masked server " + (masked() ? "yes" : "no")
            + (profile != null ? " (" + memory.key() + " profile)" : "") + " | lush caves " + (settings.lush ? lush.size() + " known" : "off")
            + " | azalea " + (settings.azalea ? "on" : "off") + " | predictions " + predicted.size()
            + ". Commands: /dripleaf on|off, masked on|off, azalea on|off, lush [on|off], predictions, seed [<seed>|clear], version [<version>|auto], search [<radius>|stop]");
    }

    boolean masked() {
        return settings.masked || profile != null && profile.maskedSections();
    }

    boolean lushNear(BlockPos pos) {
        return level != null && Splice.lushNear(level, pos);
    }

    private void clearWorld() {
        level = null;
        found.clear();
        scanned.clear();
        predicted.clear();
        near.clear();
        lush.clear();
        azaleas.clear();
    }

    private void enterWorld(Minecraft mc, ClientLevel next) {
        clearWorld();
        level = next;
        String address = Compat.serverAddress(mc);
        profile = ServerProfile.forHost(ServerProfile.host(address));
        memory.enter(ServerProfile.dataKey(profile, address, Compat.singleplayerWorld(mc)));
        loadData();
        if (!predicted.isEmpty() && !memory.predictionsAnnounced && mc.player != null) {
            memory.predictionsAnnounced = true;
            BlockPos here = mc.player.blockPosition();
            message(predicted.size() + " spliced small dripleaf top halves predicted from the seed for " + memory.key() + ":");
            predicted.keySet().stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(here))).limit(5).forEach(pos ->
                message("  " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (" + predicted.get(pos) + ", "
                    + (int) Math.sqrt(pos.distSqr(here)) + " blocks away)"));
        }
    }

    private void loadData() {
        predicted.clear();
        lush.clear();
        if (level == null || !Compat.overworld(level)) return;
        Path folder = DATA.resolve(memory.key());
        for (String[] f : rows(folder.resolve("predictions.tsv"), null, 6, 3)) {
            BlockPos pos = new BlockPos(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
            if (f.length > 6 && !f[6].equals("yes")) continue;
            String why = f.length > 9 ? Guidance.text(pos.getX(), pos.getZ(), f[4], f[8], f[9]) : f[5];
            if (!memory.settled.contains(pos)) predicted.put(pos, f[3] + " half, " + why);
        }
        for (String[] f : rows(folder.resolve("lush.tsv"), profile == null ? null : profile.lushResource(), 4, 4)) {
            lush.put(new BlockPos(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])), Integer.parseInt(f[3]));
        }
    }

    void reloadData() {
        loadData();
    }

    String dataKey() {
        return memory.key();
    }

    static String connectionKey(Minecraft mc) {
        if (mc.level == null) return null;
        String address = Compat.serverAddress(mc);
        return ServerProfile.dataKey(connectionProfile(mc), address, Compat.singleplayerWorld(mc));
    }

    static ServerProfile connectionProfile(Minecraft mc) {
        return mc.level == null ? null : ServerProfile.forHost(ServerProfile.host(Compat.serverAddress(mc)));
    }

    void markPredictionsAnnounced() {
        memory.predictionsAnnounced = true;
    }

    void addPrediction(BlockPos pos, String text) {
        if (level != null && Compat.overworld(level) && !memory.settled.contains(pos)) predicted.put(pos, text);
    }

    void announceSavedOnReturn(String key) {
        if (key.equals(memory.key())) memory.predictionsAnnounced = false;
    }

    void announce(String text) {
        message(text);
        Compat.toast(Minecraft.getInstance(), text);
    }

    private List<String[]> rows(Path file, String resource, int fields, int ints) {
        try {
            return Data.rows(Data.lines(file, resource), fields, ints);
        } catch (IOException | RuntimeException e) {
            message("Could not read " + (Files.exists(file) ? file : resource) + " (" + e.getMessage() + ")");
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

    private String emptyReason(boolean empty, String file, String tool) {
        if (!empty) return null;
        if (!settings.enabled) return "The finder is off (/dripleaf on).";
        if (level == null) return "Join a world first.";
        if (!Compat.overworld(level)) return "Only kept for the Overworld.";
        return "Nothing known for " + memory.key() + ". " + tool + " writes " + DATA.resolve(memory.key()).resolve(file)
            + "; rejoin after.";
    }

    public void listNearestLush() {
        String why = emptyReason(lush.isEmpty(), "lush.tsv", "lush-caves.sh <seed> <x> <z> <radius> <host>");
        if (why != null) {
            message("No lush caves. " + why);
            return;
        }
        BlockPos here = Minecraft.getInstance().player.blockPosition();
        lush.keySet().stream().sorted(Comparator.comparingDouble(pos -> flatDistance(pos, here))).limit(5).forEach(pos ->
            message(lushText(pos, lush.get(pos)) + ", " + (int) flatDistance(pos, here) + " blocks away"));
    }

    public void listNearest() {
        String why = emptyReason(predicted.isEmpty(), "predictions.tsv",
            "seed-search.sh <seed> <x> <z> <radius> <host> (only useful where the server's features follow the world seed)");
        if (why != null) {
            message("No predicted positions. " + why);
            return;
        }
        BlockPos here = Minecraft.getInstance().player.blockPosition();
        predicted.keySet().stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(here))).limit(5).forEach(pos ->
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
        boolean masked = masked();
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
            if (pos.distSqr(here) > SETTLE_RANGE * SETTLE_RANGE || Compat.loadedChunk(level, pos.getX() >> 4, pos.getZ() >> 4) == null) {
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
        if (!ServerProfile.keepCanopy(box.minY, box.maxY, profile)) return;
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
            if (found.contains(pos)) continue;
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
