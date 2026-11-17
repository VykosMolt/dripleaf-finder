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
        List<String> views = new ArrayList<>();
        views.add(view(mc, model, finder));
        for (String type : List.of("Folia", "Paper/Spigot", MenuModel.AUTO)) {
            model.setServerType(type);
            views.add(view(mc, model, finder));
            if (type.equals("Folia")) finder.listNearest();
        }
        boolean folia = expected.equals("Folia");
        String two = "2/5 drawn 2", five = "5/5 drawn 5";
        List<String> want = List.of(folia ? two : five, two, five, folia ? two : five);
        List<String> got = views.stream().map(v -> v.substring(0, v.indexOf(','))).toList();
        boolean ok = auto.equals(expected) && got.equals(want) && model.serverType().equals(MenuModel.AUTO);
        smoke.shoot(mc, (ok ? "PASS" : "FAIL") + " brand \"" + brand + "\" -> " + auto + " (want " + expected + "); shown/drawn of 5"
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

    private static String view(Minecraft mc, MenuModel model, Finder finder) {
        int[] drawn = {0};
        finder.boxes(mc.player.getX(), mc.player.getY(), mc.player.getZ(), (box, color) -> {
            if (color == Finder.PREDICTED_COLOR) drawn[0]++;
        });
        return finder.shown().size() + "/" + finder.predicted.size() + " drawn " + drawn[0] + ", status \"" + model.status() + "\"";
    }
}
