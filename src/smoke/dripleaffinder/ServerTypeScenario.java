package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

final class ServerTypeScenario implements Scenario {
    private final Smoke smoke;
    private final String expected;

    ServerTypeScenario(Smoke smoke, String expected) {
        this.smoke = smoke;
        this.expected = expected;
    }

    @Override
    public void tick(Minecraft mc, Finder finder) {
        if (smoke.ticks - smoke.joinedAt < 100) return;
        MenuModel model = DripleafClient.menu();
        String brand = Seeds.brand(), auto = finder.seeds.serverType()[0];
        System.out.println("SMOKE server brand \"" + brand + "\" -> " + auto + " (" + model.serverType() + ")");
        BlockPos at = mc.player.blockPosition();
        try {
            plant(Finder.DATA.resolve(Finder.connectionKey(mc)).resolve("predictions.tsv"), at.getX() + 64, at.getZ());
        } catch (IOException e) {
            smoke.shoot(mc, "FAIL could not write predictions: " + e);
            return;
        }
        finder.reloadData();
        BlockPos ordered = new BlockPos(at.getX() + 64, -49, at.getZ());
        List<String> views = new ArrayList<>();
        views.add(view(mc, model, finder, ordered));
        for (String type : List.of("Folia", "Paper/Spigot", MenuModel.AUTO)) {
            model.setServerType(type);
            views.add(view(mc, model, finder, ordered));
            if (type.equals("Folia")) finder.listNearest();
        }
        String folia = "5 drawn 5 folia", paper = "5 drawn 5 paper", auto5 = expected.equals("Folia") ? folia : paper;
        List<String> got = views.stream().map(v -> v.substring(0, v.indexOf(','))).toList();
        boolean ok = auto.equals(expected) && got.equals(List.of(auto5, folia, paper, auto5)) && model.serverType().equals(MenuModel.AUTO);
        smoke.shoot(mc, (ok ? "PASS" : "FAIL") + " brand \"" + brand + "\" -> " + auto + " (want " + expected + "); kept/drawn of 5 and labels"
            + " for Auto, Folia, Paper/Spigot, Auto: " + String.join(" ; ", views));
    }

    private static void plant(Path file, int x, int z) throws IOException {
        int cx = x >> 4, cz = z >> 4;
        String[] chunks = {cx + "," + cz + "\t" + cx + "," + cz, cx + "," + cz + "\t" + (cx + 1) + "," + cz, "-\t-", null,
            cx + "," + cz + "\t" + cx + "," + cz};
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < chunks.length; i++) {
            String row = x + "\t" + (-50 + i) + "\t" + z + "\ttop\tair\tasc\tyes\t64";
            lines.add(chunks[i] == null ? row : row + "\t" + chunks[i]);
        }
        Files.createDirectories(file.getParent());
        Files.write(file, lines);
    }

    private static String view(Minecraft mc, MenuModel model, Finder finder, BlockPos ordered) {
        int[] drawn = {0};
        finder.boxes(mc.player.getX(), mc.player.getY(), mc.player.getZ(), (box, color) -> {
            if (color == Finder.PREDICTED_COLOR) drawn[0]++;
        });
        String candidates = model.candidates(), text = finder.text(ordered);
        String label = text.contains(Guidance.FOLIA_ODDS) && candidates.contains(Guidance.FOLIA_ODDS) ? "folia"
            : text.contains("walk in from") && !candidates.contains("Folia") ? "paper" : "mixed";
        return finder.predicted.size() + " drawn " + drawn[0] + " " + label + ", status \"" + model.status() + "\", candidates \"" + candidates
            + "\", \"" + text + "\"";
    }
}
