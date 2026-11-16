package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Data {
    public static List<String[]> rows(List<String> lines, int fields, int ints) {
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            String[] f = line.split("\t");
            if (f.length < fields) continue;
            for (int i = 0; i < f.length; i++) f[i] = f[i].trim();
            try {
                for (int i = 0; i < ints; i++) Integer.parseInt(f[i]);
                rows.add(f);
            } catch (NumberFormatException e) {
            }
        }
        return rows;
    }

    public static List<String> lines(Path file) throws IOException {
        return Files.exists(file) ? Files.readAllLines(file) : List.of();
    }

    static String read(Path file) {
        try {
            if (file == null || !Files.exists(file)) return null;
            String text = Files.readString(file).trim();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    private Data() {
    }
}
